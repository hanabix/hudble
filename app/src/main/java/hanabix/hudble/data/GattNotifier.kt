package hanabix.hudble.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "GattNotifier"

// Standard GATT UUIDs
private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

/**
 * BLE GATT notifier that connects to a device and provides
 * the ability to subscribe to characteristic data changes.
 *
 * Automatically disconnects when all subscription Flows are closed.
 */
class GattNotifier private constructor(
    private val gatt: BluetoothGatt,
    private val characteristicFlow: MutableSharedFlow<Pair<UUID, ByteArray>>,
    private val pendingDescriptorWrites: ConcurrentHashMap<UUID, CompletableDeferred<Int>>,
) {

    // Track active subscriptions to auto-disconnect when all are closed
    private val activeSubscriptions = AtomicInteger(0)

    // Track enabled notifications to avoid duplicate CCCD writes
    private val enabledCharacteristics = ConcurrentHashMap.newKeySet<UUID>()

    /**
     * GATT callback that handles connection state changes, service discovery,
     * descriptor writes, and characteristic notifications.
     */
    private class GattCallback(
        private val device: BluetoothDevice,
        private val cont: kotlinx.coroutines.CancellableContinuation<GattNotifier>,
        private val characteristicFlow: MutableSharedFlow<Pair<UUID, ByteArray>>,
        private val pendingDescriptorWrites: ConcurrentHashMap<UUID, CompletableDeferred<Int>>,
        private val onDisconnected: (status: Int) -> Unit,
    ) : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "Connection state changed: device=${device.address}, status=$status, newState=$newState")

            when {
                // Connected successfully → discover services
                newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS -> {
                    Log.d(TAG, "Connected, discovering services...")
                    gatt.discoverServices()
                }

                // Disconnected
                newState == BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.w(TAG, "Disconnected from ${device.address}, status=$status")
                    if (cont.isCompleted) {
                        onDisconnected(status)
                    } else {
                        cont.resumeWithException(Exception("Connection failed: status=$status"))
                    }
                }

                // Other unexpected states (e.g., STATE_CONNECTING should not appear here)
                else -> {
                    Log.e(TAG, "Unexpected connection state: newState=$newState, status=$status")
                    if (cont.isCompleted) {
                        onDisconnected(status)
                    } else {
                        cont.resumeWithException(Exception("Connection failed: status=$status"))
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered successfully")
                // Defensive check: cont should not be completed since gatt.close() prevents this callback
                if (!cont.isCompleted) {
                    val notifier = GattNotifier(gatt, characteristicFlow, pendingDescriptorWrites)
                    cont.resume(notifier)
                }
            } else {
                Log.e(TAG, "Service discovery failed: status=$status")
                cont.resumeWithException(Exception("Service discovery failed: status=$status"))
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            Log.d(TAG, "Descriptor write: uuid=${descriptor.uuid}, status=$status")
            pendingDescriptorWrites[descriptor.characteristic.uuid]?.complete(status)
        }

        @Deprecated("Deprecated in Android 13")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            val value = characteristic.value ?: return
            Log.d(TAG, "Characteristic changed (legacy): uuid=${characteristic.uuid}, " +
                "value=${value.joinToString(" ") { "%02X".format(it) }}")
            characteristicFlow.tryEmit(characteristic.uuid to value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            Log.d(TAG, "Characteristic changed: uuid=${characteristic.uuid}, " +
                "value=${value.joinToString(" ") { "%02X".format(it) }}")
            characteristicFlow.tryEmit(characteristic.uuid to value)
        }
    }

    companion object {
        /**
         * Factory method: connects to device and creates a ready-to-use GattNotifier.
         *
         * @param context Android context
         * @param device BLE device to connect to
         * @param onDisconnected Callback invoked when connection is lost.
         * @param status The BLE status code of the disconnection.
         */
        @SuppressLint("MissingPermission")
        suspend fun connect(
            context: Context,
            device: BluetoothDevice,
            onDisconnected: (status: Int) -> Unit,
        ): GattNotifier = suspendCancellableCoroutine { cont ->
            val characteristicFlow = MutableSharedFlow<Pair<UUID, ByteArray>>(
                extraBufferCapacity = 10,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
            val pendingDescriptorWrites = ConcurrentHashMap<UUID, CompletableDeferred<Int>>()

            val callback = GattCallback(
                device = device,
                cont = cont,
                characteristicFlow = characteristicFlow,
                pendingDescriptorWrites = pendingDescriptorWrites,
                onDisconnected = onDisconnected,
            )

            val gatt = device.connectGatt(context, false, callback)
            cont.invokeOnCancellation {
                Log.d(TAG, "Connection cancelled")
                gatt.close()
            }
        }
    }

    /**
     * Subscribe to a characteristic's notifications.
     *
     * @param serviceUuid GATT Service UUID
     * @param characteristicUuid GATT Characteristic UUID
     * @return Flow<ByteArray> that emits characteristic data changes.
     *         Automatically unsubscribes when the Flow is cancelled.
     * @throws IllegalArgumentException if characteristic is not found
     * @throws IllegalStateException if CCCD descriptor is not found
     * @throws IOException if descriptor write fails
     */
    @SuppressLint("MissingPermission")
    suspend fun subscribe(
        serviceUuid: UUID,
        characteristicUuid: UUID,
    ): Flow<ByteArray> {
        val characteristic = gatt.getService(serviceUuid)?.getCharacteristic(characteristicUuid)
            ?: throw IllegalArgumentException(
                "Characteristic not found: service=$serviceUuid, characteristic=$characteristicUuid"
            )

        try {
            enableNotification(characteristic)
        } catch (e: Exception) {
            gatt.close()
            throw e
        }

        return characteristicFlow
            .filter { (uuid, _) -> uuid == characteristicUuid }
            .map { (_, data) -> data }
            .onStart { activeSubscriptions.incrementAndGet() }
            .onCompletion {
                val remaining = activeSubscriptions.decrementAndGet()
                Log.d(TAG, "Subscription closed: remaining=$remaining")
                if (remaining == 0) {
                    Log.d(TAG, "All subscriptions closed, disconnecting GATT")
                    gatt.close()
                }
            }
    }

    /**
     * Enable notifications for a characteristic and wait for the descriptor write to complete.
     * Uses a per-characteristic CompletableDeferred to avoid concurrent subscription bugs.
     *
     * @throws IllegalStateException if CCCD descriptor is not found
     * @throws IOException if descriptor write fails
     */
    @SuppressLint("MissingPermission")
    private suspend fun enableNotification(characteristic: BluetoothGattCharacteristic) {
        // Skip if already enabled
        if (!enabledCharacteristics.add(characteristic.uuid)) {
            Log.d(TAG, "Notification already enabled for ${characteristic.uuid}")
            return
        }

        val cccd = characteristic.getDescriptor(CCCD)
        if (cccd == null) {
            enabledCharacteristics.remove(characteristic.uuid)
            throw IllegalStateException("CCCD descriptor not found for ${characteristic.uuid}")
        }

        val deferred = CompletableDeferred<Int>()
        pendingDescriptorWrites[characteristic.uuid] = deferred

        gatt.setCharacteristicNotification(characteristic, true)
        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        val success = gatt.writeDescriptor(cccd)

        if (!success) {
            pendingDescriptorWrites.remove(characteristic.uuid)
            enabledCharacteristics.remove(characteristic.uuid)
            throw IOException("writeDescriptor returned false for ${characteristic.uuid}")
        }

        val status = deferred.await()
        pendingDescriptorWrites.remove(characteristic.uuid)

        if (status != BluetoothGatt.GATT_SUCCESS) {
            enabledCharacteristics.remove(characteristic.uuid)
            throw IOException("Descriptor write failed for ${characteristic.uuid}: status=$status")
        } else {
            Log.d(TAG, "Notification enabled for ${characteristic.uuid}")
        }
    }
}

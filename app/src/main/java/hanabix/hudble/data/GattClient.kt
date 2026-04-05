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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "GattClient"

// Standard GATT UUIDs
private val HEART_RATE_SERVICE = UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB")
private val HEART_RATE_MEASUREMENT = UUID.fromString("00002A37-0000-1000-8000-00805F9B34FB")
private val RSC_SERVICE = UUID.fromString("00001814-0000-1000-8000-00805F9B34FB")
private val RSC_MEASUREMENT = UUID.fromString("00002A53-0000-1000-8000-00805F9B34FB")
private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

/**
 * BLE GATT client that connects to a device and subscribes to HRS/RSCS notifications.
 */
class GattClient(private val context: Context) {

    private var gatt: BluetoothGatt? = null
    private var wasConnected = false
    private val characteristicFlow = MutableSharedFlow<Pair<UUID, ByteArray>>(
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val connectionStateFlow = MutableSharedFlow<Int>(extraBufferCapacity = 1)

    /** Flow of connection state changes (BluetoothProfile state constants). */
    val connectionState = connectionStateFlow.asSharedFlow()

    /**
     * Connect to [device] and discover GATT services.
     * Must be called before subscribing to characteristics.
     */
    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice) {
        disconnect()
        gatt = connectGatt(device)
    }

    /** Disconnect from the current device. */
    fun disconnect() {
        gatt?.close()
        gatt = null
    }

    /**
     * Subscribe to heart rate measurements.
     * Returns a flow that emits heart rate in BPM.
     */
    @SuppressLint("MissingPermission")
    fun subscribeHeartRate(): Flow<Int> {
        val characteristic = gatt?.findCharacteristic(HEART_RATE_SERVICE, HEART_RATE_MEASUREMENT)
            ?: run {
                Log.w(TAG, "Heart rate characteristic not found")
                return kotlinx.coroutines.flow.emptyFlow()
            }

        enableNotification(gatt!!, characteristic)

        return characteristicFlow
            .filter { (uuid, _) -> uuid == HEART_RATE_MEASUREMENT }
            .map { (_, data) -> parseHeartRate(data) }
            .filter { it != null }
            .map { it!! }
    }

    /**
     * Subscribe to RSC measurements.
     * Returns a flow of pairs (speed in m/s, cadence in RPM).
     */
    @SuppressLint("MissingPermission")
    fun subscribeRSCS(): Flow<Pair<Float, Int>> {
        val characteristic = gatt?.findCharacteristic(RSC_SERVICE, RSC_MEASUREMENT)
            ?: run {
                Log.w(TAG, "RSCS characteristic not found")
                return kotlinx.coroutines.flow.emptyFlow()
            }

        enableNotification(gatt!!, characteristic)

        return characteristicFlow
            .filter { (uuid, _) -> uuid == RSC_MEASUREMENT }
            .map { (_, data) -> parseRSCS(data) }
            .filter { it != null }
            .map { it!! }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectGatt(device: BluetoothDevice): BluetoothGatt =
        suspendCancellableCoroutine { cont ->
            val connectionCallback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    Log.d(TAG, "Connection state changed: device=${device.address}, status=$status, newState=$newState")

                    if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                        wasConnected = true
                        Log.d(TAG, "Connected, discovering services...")
                        gatt.discoverServices()
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        Log.w(TAG, "Disconnected from ${device.address}, status=$status")
                        if (wasConnected) {
                            connectionStateFlow.tryEmit(newState)
                        }
                        wasConnected = false
                        if (!cont.isCompleted) {
                            cont.resumeWithException(Exception("Disconnected: status=$status"))
                        }
                    } else if (status != BluetoothGatt.GATT_SUCCESS) {
                        Log.e(TAG, "Connection error: status=$status")
                        if (!cont.isCompleted) {
                            cont.resumeWithException(Exception("Connection failed: status=$status"))
                        }
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.d(TAG, "Services discovered successfully")
                        if (!cont.isCompleted) {
                            cont.resume(gatt)
                        }
                    } else {
                        Log.e(TAG, "Service discovery failed: status=$status")
                        if (!cont.isCompleted) {
                            cont.resumeWithException(Exception("Service discovery failed: status=$status"))
                        }
                    }
                }

                override fun onDescriptorWrite(
                    gatt: BluetoothGatt,
                    descriptor: BluetoothGattDescriptor,
                    status: Int,
                ) {
                    Log.d(TAG, "Descriptor write: uuid=${descriptor.uuid}, status=$status")
                }

                @Deprecated("Deprecated in Android 13")
                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                ) {
                    val value = characteristic.value ?: return
                    Log.d(TAG, "Characteristic changed (legacy): uuid=${characteristic.uuid}, " +
                        "value=${value.joinToString(" ") { "%02X".format(it) }}, " +
                        "props=${characteristic.properties}")
                    characteristicFlow.tryEmit(characteristic.uuid to value)
                }

                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    value: ByteArray,
                ) {
                    Log.d(TAG, "Characteristic changed: uuid=${characteristic.uuid}, " +
                        "value=${value.joinToString(" ") { "%02X".format(it) }}, " +
                        "props=${characteristic.properties}")
                    characteristicFlow.tryEmit(characteristic.uuid to value)
                }
            }
            val gatt = device.connectGatt(context, false, connectionCallback)
            cont.invokeOnCancellation {
                Log.d(TAG, "Connection cancelled")
                gatt.close()
            }
        }

    private fun BluetoothGatt.findCharacteristic(
        serviceUuid: UUID,
        characteristicUuid: UUID,
    ): BluetoothGattCharacteristic? {
        return getService(serviceUuid)?.getCharacteristic(characteristicUuid)
    }

    private fun enableNotification(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
    ) {
        gatt.setCharacteristicNotification(characteristic, true)
        characteristic.getDescriptor(CCCD)?.let { descriptor ->
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            // Use WRITE_REQUEST for newer API levels
            val success = gatt.writeDescriptor(descriptor)
            Log.d(TAG, "Enable notification for ${characteristic.uuid}: writeDescriptor=$success, props=${characteristic.properties}")
        } ?: Log.w(TAG, "CCCD descriptor not found for ${characteristic.uuid}")
    }

    private fun parseHeartRate(data: ByteArray): Int? {
        return HeartRateService.parseHeartRate(data)
    }

    private fun parseRSCS(data: ByteArray): Pair<Float, Int>? {
        val measurement = RunningSpeedCadenceService.parseMeasurement(data)
        return measurement?.let { it.speedMs to it.cadenceRpm }
    }
}

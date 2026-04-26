package hanabix.hubu.android

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import hanabix.hubu.model.Connect
import hanabix.hubu.model.Metric
import hanabix.hubu.model.Meter
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.ConcurrentLinkedQueue

internal class BleConnect(
    private val context: Context,
    private val logger: Logger = NoopLogger,
) : Connect<DeviceSource> {

    @SuppressLint("MissingPermission")
    override fun invoke(
        source: DeviceSource,
        metrics: Set<Metric>,
    ): Flow<Connect.Status<DeviceSource>> = callbackFlow {
        val callback = GattCallbackBridge(
            source = source,
            requested = metrics,
            channel = this,
            logger = logger,
        )

        val gatt = try {
            source.device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        } catch (t: Throwable) {
            trySend(Connect.Status.Disconnected(source, t))
            close()
            return@callbackFlow
        }

        awaitClose {
            gatt.close()
        }
    }
}

@Suppress("OVERRIDE_DEPRECATION")
internal class GattCallbackBridge(
    private val source: DeviceSource,
    private val requested: Set<Metric>,
    private val channel: SendChannel<Connect.Status<DeviceSource>>,
    private val logger: Logger,
) : BluetoothGattCallback() {
    private val pending = ConcurrentLinkedQueue<BluetoothGattDescriptor>()

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        when {
            status != BluetoothGatt.GATT_SUCCESS ->
                disconnect("Gatt connection failed: status=$status")

            newState == BluetoothProfile.STATE_CONNECTED -> {
                gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER)
                gatt.discoverServices()
            }

            newState == BluetoothProfile.STATE_DISCONNECTED ->
                disconnect()
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            disconnect("Gatt service discovery failed: status=$status")
            return
        }

        val supported = requested.filter { metric ->
            gatt.getService(metric.service)
                ?.getCharacteristic(metric.characteristic)
                ?.getDescriptor(CLIENT_CHARACTERISTIC_CONFIGURATION) != null
        }.toSet()

        val unsupported = requested - supported
        if (unsupported.isNotEmpty()) {
            channel.trySend(Connect.Status.Unsupported(unsupported))
        }

        if (supported.isEmpty()) {
            gatt.disconnect()
            return
        }

        for (metric in supported) {
            val characteristic = gatt.getService(metric.service).getCharacteristic(metric.characteristic)
            val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIGURATION)
            if (!gatt.setCharacteristicNotification(characteristic, true)) {
                disconnect("Gatt notification setup failed: metric=$metric")
                return
            }
            if (!descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                disconnect("Gatt descriptor value setup failed: metric=$metric")
                return
            }
            pending.offer(descriptor)
        }

        writeNext(gatt)
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
    ) {
        val service = characteristic.service?.uuid ?: return
        channel.trySend(
            Connect.Status.Received(
                Meter(
                    source = source,
                    metric = Metric(
                        service = service,
                        characteristic = characteristic.uuid,
                    ),
                    data = characteristic.value ?: ByteArray(0),
                ),
            ),
        )
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int,
    ) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            disconnect("Gatt descriptor write failed: status=$status")
            return
        }

        writeNext(gatt)
    }

    private fun writeNext(gatt: BluetoothGatt) {
        val descriptor = pending.poll() ?: return
        if (!gatt.writeDescriptor(descriptor)) {
            disconnect("Gatt descriptor write request failed")
        }
    }

    private fun disconnect(msg: String? = null, cause: Throwable? = null) {
        if (msg != null) {
            logger.w(TAG, msg)
        }
        pending.clear()
        channel.trySend(Connect.Status.Disconnected(source, cause ?: msg?.let(::BleError)))
        channel.close()
    }
}

private val CLIENT_CHARACTERISTIC_CONFIGURATION =
    java.util.UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

private const val TAG = "BleConnect"

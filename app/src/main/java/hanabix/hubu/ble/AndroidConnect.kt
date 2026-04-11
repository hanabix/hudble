package hanabix.hubu.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val TAG = "AndroidConnect"
private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

internal class AndroidConnect(
    private val context: Context,
    private val log: Logger = AndroidLogger,
) : BleConnect<ScannedDevice> {
    @SuppressLint("MissingPermission")
    override fun invoke(metrics: List<BleMetric>): (ScannedDevice) -> Flow<BleConnectEvent<ScannedDevice>> = { device ->
        callbackFlow {
            val send: (BleConnectEvent<ScannedDevice>) -> Unit = { event ->
                when (event) {
                    is BleConnectEvent.Fatal -> {
                        trySend(event)
                        close()
                    }

                    else -> trySend(event)
                }
            }
            val callback = AndroidConnectCallback(
                device = device,
                metrics = metrics,
                emit = send,
                log = log,
            )
            val gatt = device.device.connectGatt(context.applicationContext, false, callback)

            awaitClose {
                gatt.close()
            }
        }
    }
}

internal class AndroidConnectCallback(
    private val device: ScannedDevice,
    private val metrics: List<BleMetric>,
    private val emit: (BleConnectEvent<ScannedDevice>) -> Unit,
    private val log: Logger,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) : BluetoothGattCallback() {
    private val supportedQueue = ConcurrentLinkedQueue<BleMetric>()
    private val fatalEmit = AtomicBoolean(false)

    override fun onConnectionStateChange(
        gatt: BluetoothGatt,
        status: Int,
        newState: Int,
    ) {
        when {
            status != BluetoothGatt.GATT_SUCCESS ->
                fatal("Connection failed: status=$status")

            newState == BluetoothProfile.STATE_CONNECTED -> {
                if (!gatt.discoverServices()) {
                    fatal("discoverServices returned false")
                }
            }

            newState == BluetoothProfile.STATE_DISCONNECTED ->
                fatal("Disconnected: status=$status")
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            val result = gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER)
            log.i(TAG, "requestConnectionPriority(lowPower) result=$result")
            val unsupported = metrics.filter { metric ->
                gatt.getService(metric.service)
                    ?.getCharacteristic(metric.characteristic) == null
            }
            val supported = metrics.filterNot { metric -> metric in unsupported.toSet() }
            if (unsupported.isNotEmpty()) {
                emit(
                    BleConnectEvent.Unsupported(
                        value = device,
                        part = supported.isNotEmpty(),
                        metrics = unsupported,
                    ),
                )
            }
            if (supported.isEmpty()) {
                fatal("No supported metrics found")
                return
            }
            supportedQueue.addAll(supported)
            enableNextNotification(gatt)
        } else {
            fatal("Service discovery failed: status=$status")
        }
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int,
    ) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            enableNextNotification(gatt)
        } else {
            fatal("Descriptor write failed for ${descriptor.characteristic.uuid}: status=$status")
        }
    }

    @Deprecated("Deprecated in Android 13")
    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
    ) {
        val value = characteristic.value ?: return
        handleNotify(characteristic, value)
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ) {
        handleNotify(characteristic, value)
    }

    private fun handleNotify(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ) {
        val metric = BleMetric.entries.firstOrNull { it.characteristic == characteristic.uuid }
            ?: run {
                return
            }

        emit(
            BleConnectEvent.Notify(
                value = device,
                meter = BleMeter(metric = metric, data = value),
            ),
        )
    }

    private fun enableNextNotification(gatt: BluetoothGatt) {
        val metric = supportedQueue.poll() ?: return
        val char = gatt.getService(metric.service)!!
            .getCharacteristic(metric.characteristic)!!

        val cccd = char.getDescriptor(CCCD)!!

        gatt.setCharacteristicNotification(char, true)
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            val writeResult = gatt.writeDescriptor(
                cccd,
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
            )
            if (writeResult != BluetoothStatusCodes.SUCCESS) {
                fatal("writeDescriptor failed for ${char.uuid}: result=$writeResult")
            }
        } else {
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            val writeResult = gatt.writeDescriptor(cccd)
            if (!writeResult) {
                fatal("writeDescriptor failed for ${char.uuid}")
            }
        }
    }

    private fun fatal(message: String) {
        if (fatalEmit.compareAndSet(false, true)) {
            emit(
                BleConnectEvent.Fatal(
                    value = device,
                    cause = message,
                ),
            )
            log.e(TAG, message)
        }
    }
}

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

internal fun interface BleConnect<T> {
    typealias CreateCallback = (
        ScannedDevice,
        List<BleMetric>,
        BleChannel<BleConnect.Event<ScannedDevice>>,
    ) -> BluetoothGattCallback

    operator fun invoke(metrics: List<BleMetric>): (T) -> Flow<Event<T>>

    companion object {
        operator fun invoke(
            context: Context,
            createCallback: CreateCallback = default(),
        ): BleConnect<ScannedDevice> = AndroidConnect(context, createCallback)

        fun default(): CreateCallback = { device, metrics, channel ->
            AndroidConnectCallback(device, metrics, channel, AndroidLogger)
        }
    }

    sealed interface Event<out T> {
        data class Connected(
            val unsupported: List<BleMetric>,
        ) : Event<Nothing>

        data class Abandon<T>(
            val device: T,
            val unsupported: List<BleMetric>,
        ) : Event<T>

        data class Notify<T>(
            val device: T,
            val meter: BleMeter,
        ) : Event<T>

        data class Disconnected<T>(
            val device: T,
            val cause: String,
        ) : Event<T>
    }
}

internal class AndroidConnect(
    private val context: Context,
    private val createCallback: BleConnect.CreateCallback,
) : BleConnect<ScannedDevice> {
    @SuppressLint("MissingPermission")
    override fun invoke(metrics: List<BleMetric>): (ScannedDevice) -> Flow<BleConnect.Event<ScannedDevice>> = { device ->
        callbackFlow {
            val callback = createCallback(
                device,
                metrics,
                object : BleChannel<BleConnect.Event<ScannedDevice>> {
                    override fun emit(a: BleConnect.Event<ScannedDevice>) {
                        trySend(a)
                    }

                    override fun close() {
                        close()
                    }
                },
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
    private val channel: BleChannel<BleConnect.Event<ScannedDevice>>,
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
                fatalIf(!gatt.discoverServices(), "discoverServices returned false")
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
            if (unsupported.size == metrics.size) {
                emitAbandon()
                return
            }
            channel.emit(BleConnect.Event.Connected(unsupported))
            supportedQueue.addAll(supported)
            enableNextNotification(gatt)
        } else {
            emitAbandon()
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
        emitNotify(characteristic, characteristic.value)
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ) {
        emitNotify(characteristic, value)
    }

    private fun emitNotify(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ) {
        val metric = BleMetric.entries.first { it.characteristic == characteristic.uuid }

        channel.emit(
            BleConnect.Event.Notify(
                device,
                BleMeter(metric, value),
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
            fatalIf(
                writeResult != BluetoothStatusCodes.SUCCESS,
                "writeDescriptor failed for ${char.uuid}: result=$writeResult",
            )
        } else {
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            val writeResult = gatt.writeDescriptor(cccd)
            fatalIf(!writeResult, "writeDescriptor failed for ${char.uuid}")
        }
    }

    private fun emitAbandon() {
        channel.emit(
            BleConnect.Event.Abandon(
                device,
                metrics,
            ),
        )
        channel.close()
    }

    private fun fatal(message: String) {
        log.e(TAG, message)
        if (fatalEmit.compareAndSet(false, true)) {
            channel.emit(
                BleConnect.Event.Disconnected(
                    device,
                    message,
                ),
            )
            channel.close()
        }
    }

    private fun fatalIf(expr: Boolean, message: String) {
        if (expr) {
            fatal(message)
        }
    }
}

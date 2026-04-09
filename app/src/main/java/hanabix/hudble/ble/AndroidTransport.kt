package hanabix.hudble.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "AndroidTransport"
private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

internal object AndroidTransport {

    @SuppressLint("MissingPermission")
    internal fun scan(context: Context): BleScan<BluetoothDevice> = BleScan { metrics ->
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        val bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

        callbackFlow {
            val filters = metrics.map { metric ->
                ScanFilter.Builder().setServiceUuid(ParcelUuid(metric.service)).build()
            }
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
                .build()

            val seen = mutableSetOf<String>()

            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val device = result.device
                    val id = device.address
                    if (!seen.add(id)) return
                    Log.i(TAG, "Found supported device: ${device.name} ($id)")
                    trySend(device)
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.w(TAG, "BLE scan failed: code=$errorCode")
                    close()
                }
            }

            bluetoothLeScanner.startScan(filters, settings, callback)
            awaitClose { bluetoothLeScanner.stopScan(callback) }
        }
    }

    @SuppressLint("MissingPermission")
    internal fun connect(context: Context): BleConnect<BluetoothDevice> = BleConnect { metrics ->
        { device ->
            if (metrics.isEmpty()) {
                flow {
                    emit(
                        BleConnectEvent.Fatal(
                            device = device,
                            cause = "No metrics requested",
                        ),
                    )
                }
            } else {
                callbackFlow {
                    val send: (BleConnectEvent<BluetoothDevice>) -> Unit = { event ->
                        when (event) {
                            is BleConnectEvent.Fatal -> {
                                trySend(event)
                                close()
                            }

                            else -> trySend(event)
                        }
                    }
                    val callback = GattCallback(
                        device = device,
                        metrics = metrics,
                        emit = send,
                    )
                    val gatt = device.connectGatt(context.applicationContext, false, callback)

                    awaitClose {
                        gatt.close()
                    }
                }
            }
        }
    }

    internal class GattCallback(
        private val device: BluetoothDevice,
        private val metrics: List<BleMetric>,
        private val emit: (BleConnectEvent<BluetoothDevice>) -> Unit,
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
                val unsupported = metrics.filter { metric ->
                    gatt.getService(metric.service)
                        ?.getCharacteristic(metric.characteristic) == null
                }
                val supported = metrics.filterNot { metric -> metric in unsupported.toSet() }
                if (unsupported.isNotEmpty()) {
                    emit(
                        BleConnectEvent.Unsupported(
                            device = device,
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
                    device = device,
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
                        device = device,
                        cause = message,
                    ),
                )
            }
        }
    }
}

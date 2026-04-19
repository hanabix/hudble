package hanabix.hubu.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

internal fun interface BleScan<T> {
    typealias CreateCallback = (BleChannel<ScannedDevice>) -> ScanCallback

    operator fun invoke(metrics: List<BleMetric>): Flow<T>

    companion object {
        operator fun invoke(
            context: Context,
            createCallback: CreateCallback = default(),
        ): BleScan<ScannedDevice> = AndroidScan(context, createCallback)

        fun default(): CreateCallback = { channel ->
            AndroidScanCallback(channel, AndroidLogger)
        }
    }
}

private const val TAG = "AndroidScan"

internal class AndroidScan(
    private val context: Context,
    private val createCallback: BleScan.CreateCallback,
) : BleScan<ScannedDevice> {
    @SuppressLint("MissingPermission")
    override fun invoke(metrics: List<BleMetric>): Flow<ScannedDevice> = callbackFlow {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        val bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

        val filters = metrics.map { metric ->
            ScanFilter.Builder().setServiceUuid(ParcelUuid(metric.service)).build()
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
            .build()

        val callback = createCallback(object : BleChannel<ScannedDevice> {
            override fun emit(a: ScannedDevice) {
                trySend(a)
            }

            override fun close() {
                close()
            }
        })

        bluetoothLeScanner.startScan(filters, settings, callback)
        awaitClose { bluetoothLeScanner.stopScan(callback) }
    }
}

private class AndroidScanCallback(
    private val channel: BleChannel<ScannedDevice>,
    private val log: Logger,
) : ScanCallback() {
    private val seen = mutableSetOf<String>()

    override fun onScanResult(callbackType: Int, result: ScanResult) {
        val device = result.device
        val id = device.address
        val name = result.scanRecord?.deviceName ?: id
        if (!seen.add(id)) return
        log.i(TAG, "Found supported device: name=$name ($id)")
        channel.emit(ScannedDevice(device = device, name = name))
    }

    override fun onScanFailed(errorCode: Int) {
        log.w(TAG, "BLE scan failed: code=$errorCode")
        channel.close()
    }
}

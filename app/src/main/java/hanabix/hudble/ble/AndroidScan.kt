package hanabix.hudble.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback as AndroidBleScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val TAG = "AndroidScan"

internal class AndroidScan(
    private val context: Context,
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

        val callback = AndroidScanCallback(
            emit = { result -> trySend(result) },
            close = { close() },
        )

        bluetoothLeScanner.startScan(filters, settings, callback)
        awaitClose { bluetoothLeScanner.stopScan(callback) }
    }
}

internal class AndroidScanCallback(
    private val emit: (ScannedDevice) -> Unit,
    private val close: () -> Unit,
) : AndroidBleScanCallback() {
    private val seen = mutableSetOf<String>()

    override fun onScanResult(callbackType: Int, result: ScanResult) {
        val device = result.device
        val id = device.address
        val name = result.scanRecord?.deviceName ?: id
        if (!seen.add(id)) return
        Log.i(TAG, "Found supported device: name=$name ($id)")
        emit(ScannedDevice(device = device, name = name))
    }

    override fun onScanFailed(errorCode: Int) {
        Log.w(TAG, "BLE scan failed: code=$errorCode")
        close()
    }
}

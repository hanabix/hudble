package hanabix.hudble.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
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
) : BleScan<BluetoothDevice> {
    @SuppressLint("MissingPermission")
    override fun invoke(metrics: List<BleMetric>): Flow<BluetoothDevice> = callbackFlow {
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
            emit = { device -> trySend(device) },
            close = { close() },
        )

        bluetoothLeScanner.startScan(filters, settings, callback)
        awaitClose { bluetoothLeScanner.stopScan(callback) }
    }
}

internal class AndroidScanCallback(
    private val emit: (BluetoothDevice) -> Unit,
    private val close: () -> Unit,
) : AndroidBleScanCallback() {
    private val seen = mutableSetOf<String>()

    override fun onScanResult(callbackType: Int, result: ScanResult) {
        val device = result.device
        val id = device.address
        if (!seen.add(id)) return
        Log.i(TAG, "Found supported device: ${device.name} ($id)")
        emit(device)
    }

    override fun onScanFailed(errorCode: Int) {
        Log.w(TAG, "BLE scan failed: code=$errorCode")
        close()
    }
}

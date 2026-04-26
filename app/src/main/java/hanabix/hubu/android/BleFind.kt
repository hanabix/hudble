package hanabix.hubu.android

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import hanabix.hubu.model.Find
import hanabix.hubu.model.Metric
import java.util.concurrent.ConcurrentHashMap
import android.os.ParcelUuid
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

internal open class BleFind(
    private val context: Context,
    private val timeout: Duration,
    private val logger: Logger = NoopLogger,
) : Find<DeviceSource> {

    @SuppressLint("MissingPermission")
    override fun invoke(metrics: Set<Metric>): Flow<Find.Status<DeviceSource>> = callbackFlow {
        val scanner =
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter.bluetoothLeScanner
        val callback = ScanCallbackBridge(this, logger)
        val filters = scanFilters(metrics)

        scanner.startScan(filters, scanSettings(), callback)

        val timer = launch {
            if (timeout > ZERO) {
                delay(timeout.inWholeMilliseconds)
            }
            logger.w(TAG, "BLE scan timeout")
            trySend(Find.Status.Done(BleError("BLE scan timeout")))
            close()
        }

        awaitClose {
            timer.cancel()
            scanner.stopScan(callback)
        }
    }

    protected open fun scanFilters(metrics: Set<Metric>): List<ScanFilter> =
        metrics.map { metric ->
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(metric.service))
                .build()
        }

    protected open fun scanSettings(): ScanSettings =
        ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
            .build()
}

internal class ScanCallbackBridge(
    private val channel: SendChannel<Find.Status<DeviceSource>>,
    private val logger: Logger,
) : ScanCallback() {
    private val seen = ConcurrentHashMap.newKeySet<String>()

    override fun onScanResult(callbackType: Int, result: ScanResult) {
        val device = result.device
        val address = device.address
        if (!seen.add(address)) return
        val name = result.scanRecord?.deviceName ?: address
        channel.trySend(Find.Status.Found(DeviceSource(name, device)))
    }

    override fun onBatchScanResults(results: MutableList<ScanResult>) {
        results.forEach { onScanResult(0, it) }
    }

    override fun onScanFailed(errorCode: Int) {
        val msg = "BLE scan failed: code=$errorCode"
        logger.w(TAG, msg)
        channel.trySend(Find.Status.Done(BleError(msg)))
        channel.close()
    }
}

private const val TAG = "BleFind"

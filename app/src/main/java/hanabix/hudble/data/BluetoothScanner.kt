package hanabix.hudble.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlin.time.Duration
import java.util.UUID

private const val TAG = "BluetoothScanner"

/** Standard 16-bit UUIDs for common GATT services. */
object GattServices {
    val HEART_RATE = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    val RUNNING_SPEED_CADENCE = UUID.fromString("00001814-0000-1000-8000-00805f9b34fb")
}

/**
 * Scans for nearby BLE devices that advertise at least one of the
 * specified GATT service UUIDs.
 */
class BluetoothScanner(
    context: Context,
    private val serviceUuids: List<UUID>,
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

    /**
     * Scan for BLE devices that support the specified services.
     *
     * Emits each matching device as it is discovered.
     * The flow completes after [timeout] of scanning.
     *
     * @param timeout max scan duration (default 10s)
     * @return a [Flow] of [BluetoothDevice] for each discovered device
     */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    @SuppressLint("MissingPermission")
    fun scan(timeout: Duration): Flow<BluetoothDevice> = channelFlow {
        val filters = serviceUuids.map { uuid ->
            ScanFilter.Builder().setServiceUuid(ParcelUuid(uuid)).build()
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        Log.i(TAG, "Starting scan for UUIDs: ${serviceUuids.map { it.toString().substring(4, 8) }}")

        val seenAddresses = mutableSetOf<String>()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val address = result.device.address
                if (address in seenAddresses) return

                Log.i(TAG, "Found: ${result.device.name} ($address)")
                seenAddresses.add(address)
                trySend(result.device)
            }

            override fun onScanFailed(errorCode: Int) {
                Log.w(TAG, "BLE scan failed: code=$errorCode")
                close()
            }
        }

        bluetoothLeScanner.startScan(filters, settings, callback)

        awaitClose {
            bluetoothLeScanner.stopScan(callback)
        }
    }.timeout(timeout)
        .catch { if (it !is TimeoutCancellationException) throw it }
}

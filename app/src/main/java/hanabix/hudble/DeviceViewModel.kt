package hanabix.hudble

import android.util.Log
import android.view.KeyEvent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hanabix.hudble.data.BluetoothScanner
import hanabix.hudble.data.DeviceStatus
import hanabix.hudble.data.DeviceStatus.Found
import hanabix.hudble.data.DeviceStatus.NotFound
import hanabix.hudble.data.DeviceStatus.Scanning
import hanabix.hudble.data.GattNotifier
import hanabix.hudble.data.HeartRateService
import hanabix.hudble.data.Pace.format
import hanabix.hudble.data.RunningSpeedCadenceService
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Manages BLE device scanning state machine and tap-to-rescan logic.
 *
 * State transitions:
 * ```
 * (start) → Scanning → Found(name)
 *                  → NotFound → (tap) → Scanning → ...
 * ```
 */
class DeviceViewModel(
    private val context: android.content.Context,
    private val bluetoothScanner: BluetoothScanner,
) : ViewModel() {

    private val _deviceStatus = MutableStateFlow<DeviceStatus>(Scanning)
    val deviceStatus = _deviceStatus.asStateFlow()

    private val _heartRate = MutableStateFlow<String?>(null)
    val heartRate = _heartRate.asStateFlow()

    private val _pace = MutableStateFlow<String?>(null)
    val pace = _pace.asStateFlow()

    private val _cadence = MutableStateFlow<String?>(null)
    val cadence = _cadence.asStateFlow()

    private var hrJob: Job? = null
    private var rscsJob: Job? = null

    private val keyEventChannel = Channel<KeyEvent>(Channel.UNLIMITED)

    init {
        keyEventChannel.receiveAsFlow()
            .filter { it.action == KeyEvent.ACTION_UP }
            .filter { it.keyCode in TapKeyCodes }
            .filter { _deviceStatus.value is NotFound }
            .onEach { scan() }
            .launchIn(viewModelScope)
    }

    /** Forward hardware key events from the Activity. */
    fun onKeyEvent(event: KeyEvent) {
        keyEventChannel.trySend(event)
    }

    /** Start the initial BLE device scan. */
    fun startScan() {
        scan()
    }

    private fun scan() {
        _deviceStatus.value = Scanning
        viewModelScope.launch {
            val device = bluetoothScanner.scan(10.seconds).firstOrNull()
            if (device != null) {
                _deviceStatus.value = Found(device)
                connectAndSubscribe(device)
            } else {
                _deviceStatus.value = NotFound
            }
        }
    }

    private fun connectAndSubscribe(device: android.bluetooth.BluetoothDevice) {
        viewModelScope.launch {
            try {
                val notifier = GattNotifier.connect(context = context, device = device) { status ->
                    Log.w(TAG, "Device disconnected, status=$status")
                    clearSensorValues()
                    _deviceStatus.value = NotFound
                }

                // Cancel previous subscriptions
                hrJob?.cancel()
                rscsJob?.cancel()

                // Subscribe to heart rate (waits for descriptor write to complete)
                hrJob = notifier.subscribe(HEART_RATE_SERVICE, HEART_RATE_MEASUREMENT)
                    .mapNotNull { HeartRateService.parseHeartRate(it) }
                    .onEach { bpm ->
                        Log.d(TAG, "Heart rate received: $bpm")
                        _heartRate.value = bpm.toString()
                    }
                    .catch { e -> Log.e(TAG, "Heart rate subscription error: ${e.message}") }
                    .launchIn(viewModelScope)

                // Subscribe to RSCS (waits for descriptor write to complete)
                rscsJob = notifier.subscribe(RSC_SERVICE, RSC_MEASUREMENT)
                    .mapNotNull { RunningSpeedCadenceService.parseMeasurement(it) }
                    .onEach { measurement ->
                        // Sensor reports single-leg cadence; multiply by 2 for bilateral cadence
                        // consistent with Garmin watch user expectations.
                        _cadence.value = if (measurement.cadenceRpm > 0) (measurement.cadenceRpm * 2).toString() else null
                        _pace.value = format(measurement.speedMs)
                    }
                    .catch { e -> Log.w(TAG, "RSCS subscription ended: ${e.message}") }
                    .launchIn(viewModelScope)
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed for ${device.address}: ${e.message}", e)
                clearSensorValues()
                _deviceStatus.value = NotFound
            }
        }
    }

    private fun clearSensorValues() {
        _heartRate.value = null
        _pace.value = null
        _cadence.value = null
    }

    override fun onCleared() {
        super.onCleared()
        hrJob?.cancel()
        rscsJob?.cancel()
    }

    companion object {
        private const val TAG = "DeviceViewModel"

        // Standard GATT UUIDs
        private val HEART_RATE_SERVICE = java.util.UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB")
        private val HEART_RATE_MEASUREMENT = java.util.UUID.fromString("00002A37-0000-1000-8000-00805F9B34FB")
        private val RSC_SERVICE = java.util.UUID.fromString("00001814-0000-1000-8000-00805F9B34FB")
        private val RSC_MEASUREMENT = java.util.UUID.fromString("00002A53-0000-1000-8000-00805F9B34FB")

        private val TapKeyCodes = setOf(
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_DPAD_CENTER,
        )
    }
}

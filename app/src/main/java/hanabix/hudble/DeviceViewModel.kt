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
import hanabix.hudble.data.GattClient
import hanabix.hudble.data.Pace.format
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
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
    private val bluetoothScanner: BluetoothScanner,
    private val gattClient: GattClient,
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

        // Auto-rescan when connection is lost (after being connected)
        gattClient.connectionState
            .onEach {
                Log.w(TAG, "Device disconnected, resuming scan")
                clearSensorValues()
                scan()
            }
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
                gattClient.connect(device)

                // Cancel previous subscriptions
                hrJob?.cancel()
                rscsJob?.cancel()

                // Subscribe to heart rate
                hrJob = gattClient.subscribeHeartRate()
                    .onEach { bpm ->
                        Log.d(TAG, "Heart rate received: $bpm")
                        _heartRate.value = bpm.toString()
                    }
                    .catch { e -> Log.e(TAG, "Heart rate subscription error: ${e.message}") }
                    .launchIn(viewModelScope)

                // Subscribe to RSCS (skip if not available)
                rscsJob = gattClient.subscribeRSCS()
                    .onEach { (speedMs, cadenceRpm) ->
                        _cadence.value = if (cadenceRpm > 0) cadenceRpm.toString() else null
                        _pace.value = format(speedMs)
                    }
                    .catch { e -> Log.w(TAG, "RSCS subscription ended: ${e.message}") }
                    .launchIn(viewModelScope)
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed for ${device.address}: ${e.message}", e)
                clearSensorValues()
                scan()
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
        gattClient.disconnect()
    }

    companion object {
        private const val TAG = "DeviceViewModel"
        private val TapKeyCodes = setOf(
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_DPAD_CENTER,
        )
    }
}

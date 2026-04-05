package hanabix.hudble

import android.view.KeyEvent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hanabix.hudble.data.BluetoothScanner
import hanabix.hudble.data.DeviceStatus
import hanabix.hudble.data.DeviceStatus.NotFound
import hanabix.hudble.data.DeviceStatus.Scanned
import hanabix.hudble.data.DeviceStatus.Scanning
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * (start) → Scanning → Scanned(name)
 *                  → NotFound → (tap) → Scanning → ...
 * ```
 */
class DeviceViewModel(
    private val bluetoothScanner: BluetoothScanner,
) : ViewModel() {

    private val _deviceStatus = MutableStateFlow<DeviceStatus>(Scanning)
    val deviceStatus = _deviceStatus.asStateFlow()

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
            _deviceStatus.value = device?.let { Scanned(it.name ?: "Unknown") } ?: NotFound
        }
    }

    companion object {
        private val TapKeyCodes = setOf(
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_DPAD_CENTER,
        )
    }
}

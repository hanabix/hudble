package hanabix.hudble.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hanabix.hudble.data.DeviceBatteryObserver
import hanabix.hudble.data.TimeSynchronizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HudViewModel(
    private val deviceBatteryObserver: DeviceBatteryObserver,
    private val timeSynchronizer: TimeSynchronizer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HudUiState.preview())
    val uiState: StateFlow<HudUiState> = _uiState.asStateFlow()

    init {
        observeBatteryLevel()
        observeCurrentTime()
    }

    private fun observeCurrentTime() {
        viewModelScope.launch {
            timeSynchronizer.current().collect { time ->
                _uiState.update { it.copy(currentTime = time) }
            }
        }
    }

    private fun observeBatteryLevel() {
        viewModelScope.launch {
            deviceBatteryObserver.observe().collect { level ->
                _uiState.update { it.copy(batteryLevel = level) }
            }
        }
    }
}

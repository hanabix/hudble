package hanabix.hudble.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hanabix.hudble.data.DeviceBatteryObserver
import hanabix.hudble.data.TimeSynchronizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

class HUDViewModel(
    private val deviceBatteryObserver: DeviceBatteryObserver,
    private val timeSynchronizer: TimeSynchronizer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HUDUiState.preview())
    val uiState: StateFlow<HUDUiState> = _uiState.asStateFlow()

    init {
        timeSynchronizer.current().onEach { time ->
            _uiState.update { it.copy(currentTime = time) }
        }.launchIn(viewModelScope)

        deviceBatteryObserver.observe().onEach { level ->
            _uiState.update { it.copy(batteryLevel = level) }
        }.launchIn(viewModelScope)
    }
}

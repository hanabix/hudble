package hanabix.hubu.ble

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.math.roundToInt

internal class BleViewModel(
    private val scan: BleScan<ScannedDevice>,
    private val connect: BleConnect<ScannedDevice>,
    private val info: DeviceInfo<ScannedDevice>,
) : ViewModel() {
    private val _bleStatus = MutableStateFlow(STATUS_CONNECTING)
    private val _heartRate = MutableStateFlow<String?>(null)
    private val _pace = MutableStateFlow<String?>(null)
    private val _cadence = MutableStateFlow<String?>(null)

    val bleStatus: StateFlow<String> = _bleStatus
    val heartRate: StateFlow<String?> = _heartRate
    val pace: StateFlow<String?> = _pace
    val cadence: StateFlow<String?> = _cadence

    fun run() {
        _bleStatus.value = STATUS_CONNECTING

        DefaultBleGather(viewModelScope, scan, connect, info)(BleMetric.entries)
            .onEach(::render)
            .launchIn(viewModelScope)
    }

    internal fun render(event: BleEvent) {
        when (event) {
            is BleEvent.Available -> {
                _bleStatus.value = event.device

                when (event.meter.metric) {
                    BleMetric.HeartRate -> HeartRateService.read(event.meter.data)?.let {
                        _heartRate.value = it.toString()
                    }

                    BleMetric.RunSpeedCadence -> RunningSpeedCadenceService.read(event.meter.data)?.let {
                        _pace.value = pace(it.speedMs)
                        _cadence.value = cadence(it.cadenceRpm)
                    }
                }
            }

            BleEvent.Unavailable -> {
                _heartRate.value = null
                _pace.value = null
                _cadence.value = null
                _bleStatus.value = STATUS_TAP_TO_RECONNECT
            }
        }
    }

    companion object {
        /** Converts speed in m/s to pace in "M'SS\"" format. */
        internal fun pace(speedMs: Float): String? {
            if (speedMs <= 0f) return null

            val paceMinKm = 1000.0 / (speedMs * 60.0)
            val minutes = paceMinKm.toInt()
            val seconds = ((paceMinKm - minutes) * 60).roundToInt()

            return if (seconds >= 60) {
                "${minutes + 1}'00\""
            } else {
                "${minutes}'${seconds.toString().padStart(2, '0')}\""
            }
        }

        /** Converts single-leg cadence to bilateral cadence. */
        internal fun cadence(rpm: Int): String? = if (rpm > 0) (rpm * 2).toString() else null
    }
}

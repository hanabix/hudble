package hanabix.hubu.view

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hanabix.hubu.android.DeviceSource
import hanabix.hubu.bluetooth.HeartRateService
import hanabix.hubu.bluetooth.RunningSpeedCadenceService
import hanabix.hubu.model.Gather
import hanabix.hubu.model.Metric
import hanabix.hubu.model.Meter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlin.math.roundToInt

internal class BleViewModel(
    private val gather: Gather<DeviceSource>,
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

        gather(REQUESTED_METRICS)
            .onEach(::render)
            .onCompletion { renderUnavailable() }
            .launchIn(viewModelScope)
    }

    internal fun render(meter: Meter<DeviceSource>) {
        _bleStatus.value = meter.source.name

        when (meter.metric) {
            HEART_RATE -> HeartRateService.read(meter.data)?.let {
                _heartRate.value = it.toString()
            }

            RUN_SPEED_CADENCE -> RunningSpeedCadenceService.read(meter.data)?.let {
                _pace.value = pace(it.speedMs)
                _cadence.value = cadence(it.cadenceRpm)
            }
        }
    }

    internal fun renderUnavailable() {
        _heartRate.value = null
        _pace.value = null
        _cadence.value = null
        _bleStatus.value = STATUS_TAP_TO_RECONNECT
    }

    companion object {
        private val HEART_RATE = Metric(
            service = java.util.UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB"),
            characteristic = java.util.UUID.fromString("00002A37-0000-1000-8000-00805F9B34FB"),
        )
        private val RUN_SPEED_CADENCE = Metric(
            service = java.util.UUID.fromString("00001814-0000-1000-8000-00805F9B34FB"),
            characteristic = java.util.UUID.fromString("00002A53-0000-1000-8000-00805F9B34FB"),
        )

        private val REQUESTED_METRICS = setOf(HEART_RATE, RUN_SPEED_CADENCE)

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

package hanabix.hudble.ble

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.FlowPreview
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val STATUS_SCANNING = "Connecting"
private const val STATUS_TAP_TO_RECONNECT = "Tap to Reconnect"

typealias BleScan<D> = (List<BleMetric>) -> Flow<D>

typealias BleConnect<D> = (List<BleMetric>) -> (D) -> Flow<BleConnectEvent<D>>

typealias BleGather = (List<BleMetric>) -> Flow<BleEvent>

typealias ToConnect<D> = (D, List<BleMetric>) -> Job

enum class BleMetric(
    val service: java.util.UUID,
    val characteristic: java.util.UUID,
) {
    HeartRate(
        service = java.util.UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB"),
        characteristic = java.util.UUID.fromString("00002A37-0000-1000-8000-00805F9B34FB"),
    ),
    RunSpeedCadence(
        service = java.util.UUID.fromString("00001814-0000-1000-8000-00805F9B34FB"),
        characteristic = java.util.UUID.fromString("00002A53-0000-1000-8000-00805F9B34FB"),
    ),
}

data class BleMeter(
    val metric: BleMetric,
    val data: ByteArray,
)

sealed interface BleConnectEvent<out D> {
    data class Unsupported<D>(
        val device: D,
        val part: Boolean,
        val metrics: List<BleMetric>,
    ) : BleConnectEvent<D>

    data class Notify<D>(
        val device: D,
        val meter: BleMeter,
    ) : BleConnectEvent<D>

    data class Fatal<D>(
        val device: D,
        val cause: String,
    ) : BleConnectEvent<D>
}

sealed interface BleEvent {
    data class Available(
        val device: String,
        val meter: BleMeter,
    ) : BleEvent

    data object Unavailable : BleEvent
}

class BleViewModel(
    private val scan: BleScan<BluetoothDevice>,
    private val connect: BleConnect<BluetoothDevice>,
) : ViewModel() {
    data class State<D>(
        val metrics: List<BleMetric>,
        val pending: List<D> = emptyList(),
        val solid: Boolean = false,
        val jobs: Map<String, Job> = emptyMap(),
    )

    sealed interface Event<out D> {
        data class Found<D>(val device: D) : Event<D>
        data object NoMoreDevice : Event<Nothing>
        data class Reply<D>(
            val event: BleConnectEvent<D>,
        ) : Event<D>
    }

    fun interface Dispatch<D> {
        operator fun invoke(state: State<D>, event: Event<D>): State<D>
    }

    private val _bleStatus = MutableStateFlow(STATUS_SCANNING)
    private val _heartRate = MutableStateFlow<String?>(null)
    private val _pace = MutableStateFlow<String?>(null)
    private val _cadence = MutableStateFlow<String?>(null)

    val bleStatus: StateFlow<String> = _bleStatus
    val heartRate: StateFlow<String?> = _heartRate
    val pace: StateFlow<String?> = _pace
    val cadence: StateFlow<String?> = _cadence

    fun run() {
        _bleStatus.value = STATUS_SCANNING

        gather(viewModelScope, scan, connect)(BleMetric.entries)
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
        @OptIn(FlowPreview::class)
        fun <D> gather(
            scope: CoroutineScope,
            scan: BleScan<D>,
            connect: BleConnect<D>,
            timeout: Duration = 5.seconds,
        ): BleGather = { metrics ->
            channelFlow {
                val bus = Channel<Event<D>>(Channel.UNLIMITED)
                var state = State<D>(metrics = metrics)
                val fire: ToConnect<D> = { device, requested ->
                    connect(requested)(device)
                        .onEach { event ->
                            bus.trySend(Event.Reply(event))
                        }
                        .launchIn(scope)
                }
                
                val handle = dispatch(fire) { event ->
                    when (event) {
                        BleEvent.Unavailable -> {
                            trySend(event)
                            close()
                        }

                        else -> trySend(event)
                    }
                }

                val react = bus.receiveAsFlow()
                    .onEach { event ->
                        state = handle(state, event)
                    }
                    .launchIn(scope)

                val source = scan(metrics)
                val scanning = (if (metrics.isEmpty()) source else source.take(metrics.size))
                    .timeout(timeout)
                    .onEach { device ->
                        bus.trySend(Event.Found(device))
                    }
                    .onCompletion {
                        bus.trySend(Event.NoMoreDevice)
                    }
                    .launchIn(scope)

                awaitClose {
                    scanning.cancel()
                    state.jobs.values.forEach { it.cancel() }
                    react.cancel()
                    bus.close()
                }
            }
        }

        private fun <D> dispatch(
            fire: ToConnect<D>,
            send: (BleEvent) -> Unit,
        ): Dispatch<D> = object : Dispatch<D> {
            override fun invoke(state: State<D>, event: Event<D>): State<D> {
                return when (event) {
                    is Event.Found -> onFound(state, event.device, fire)
                    is Event.Reply -> onReply(state, event.event, fire, send)
                    Event.NoMoreDevice -> onNoMoreDevice(state, send)
                }
            }
        }

        private fun <D> onFound(
            state: State<D>,
            device: D,
            fire: ToConnect<D>,
        ): State<D> {
            val (metrics, pending, _, jobs) = state
            val next = pending + device

            return when (metrics.isEmpty()) {
                true -> state.copy(pending = next)
                false -> {
                    val first = next.first()
                    val id = first.id()

                    state.copy(
                        metrics = emptyList(),
                        pending = next.drop(1),
                        jobs = jobs + (id to fire(first, metrics)),
                    )
                }
            }
        }

        private fun <D> onReply(
            state: State<D>,
            reply: BleConnectEvent<D>,
            fire: ToConnect<D>,
            send: (BleEvent) -> Unit,
        ): State<D> = when (reply) {
            is BleConnectEvent.Unsupported -> {
                val (device, part, metrics) = reply
                onUnsupported(state, device, part, metrics, fire, send)
            }

            is BleConnectEvent.Notify -> {
                val (device, meter) = reply
                onNotify(state, device, meter, send)
            }

            is BleConnectEvent.Fatal -> {
                val (device, _) = reply
                onFatal(state, device, send)
            }
        }

        private fun <D> onUnsupported(
            state: State<D>,
            device: D,
            part: Boolean,
            metrics: List<BleMetric>,
            fire: ToConnect<D>,
            send: (BleEvent) -> Unit,
        ): State<D> {
            val (_, pending, solid, jobs) = state
            val id = device.id()
            val actives = when (part) {
                true -> jobs.filterValues(Job::isActive)
                false -> (jobs - id).filterValues(Job::isActive)
            }

            return when {
                pending.isNotEmpty() -> {
                    val head = pending.first()
                    val job = fire(head, metrics)

                    state.copy(
                        metrics = emptyList(),
                        pending = pending.drop(1),
                        jobs = actives + (head.id() to job),
                    )
                }

                solid && actives.isEmpty() -> {
                    send(BleEvent.Unavailable)
                    state.copy(
                        metrics = metrics,
                        jobs = actives,
                    )
                }

                else -> state.copy(
                    metrics = metrics,
                    jobs = actives,
                )
            }
        }

        private fun <D> onNotify(
            state: State<D>,
            device: D,
            meter: BleMeter,
            send: (BleEvent) -> Unit,
        ): State<D> {
            send(BleEvent.Available(device = device.name(), meter = meter))
            return state
        }

        private fun <D> onFatal(
            state: State<D>,
            device: D,
            send: (BleEvent) -> Unit,
        ): State<D> {
            val next = state.copy(
                jobs = (state.jobs - device.id()).filterValues(Job::isActive),
            )
            return when {
                next.solid && next.jobs.isEmpty() && next.pending.isEmpty() -> {
                    send(BleEvent.Unavailable)
                    next
                }

                else -> next
            }
        }

        private fun <D> onNoMoreDevice(
            state: State<D>,
            send: (BleEvent) -> Unit,
        ): State<D> {
            val next = state.copy(
                solid = true,
                jobs = state.jobs.filterValues(Job::isActive),
            )
            return when {
                next.jobs.isEmpty() && next.pending.isEmpty() -> {
                    send(BleEvent.Unavailable)
                    next
                }

                else -> next
            }
        }

        private fun Any?.id(): String = when (this) {
            is BluetoothDevice -> address
            else -> toString()
        }

        private fun Any?.name(): String = when (this) {
            is BluetoothDevice -> name ?: address
            else -> toString()
        }

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

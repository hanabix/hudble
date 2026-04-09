package hanabix.hudble.ble

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.FlowPreview
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(FlowPreview::class)
internal class DefaultBleGather<D>(
    private val scope: CoroutineScope,
    private val scan: BleScan<D>,
    private val connect: BleConnect<D>,
    private val timeout: Duration = 5.seconds,
) : BleGather {
    override fun invoke(metrics: List<BleMetric>): Flow<BleEvent> = channelFlow {
        val bus = Channel<Event<D>>(Channel.UNLIMITED)
        var state = State<D>(metrics = metrics)

        val fire: ToConnect<D> = ToConnect { device, requested ->
            connect(requested)(device)
                .onEach { event -> bus.trySend(Event.Reply(event)) }
                .launchIn(scope)
        }

        val handle = DefaultDispatch(fire) { event ->
            when (event) {
                BleEvent.Unavailable -> {
                    trySend(event)
                    close()
                }

                else -> trySend(event)
            }
        }

        val react = bus.receiveAsFlow()
            .onEach { event -> state = handle(state, event) }
            .launchIn(scope)

        val source = scan(metrics)
        val scanning = (if (metrics.isEmpty()) source else source.take(metrics.size))
            .timeout(timeout)
            .onEach { device -> bus.trySend(Event.Found(device)) }
            .onCompletion { bus.trySend(Event.NoMoreDevice) }
            .launchIn(scope)

        awaitClose {
            scanning.cancel()
            state.jobs.values.forEach { it.cancel() }
            react.cancel()
            bus.close()
        }
    }
}

private data class State<D>(
    val metrics: List<BleMetric>,
    val pending: List<D> = emptyList(),
    val solid: Boolean = false,
    val jobs: Map<String, Job> = emptyMap(),
)

private sealed interface Event<out D> {
    data class Found<D>(val device: D) : Event<D>
    data object NoMoreDevice : Event<Nothing>
    data class Reply<D>(
        val event: BleConnectEvent<D>,
    ) : Event<D>
}

private fun interface Dispatch<D> {
    operator fun invoke(state: State<D>, event: Event<D>): State<D>
}

private class DefaultDispatch<D>(
    private val fire: ToConnect<D>,
    private val send: (BleEvent) -> Unit,
) : Dispatch<D> {
    override fun invoke(state: State<D>, event: Event<D>): State<D> {
        return when (event) {
            is Event.Found -> onFound(state, event.device)
            is Event.Reply -> onReply(state, event.event)
            Event.NoMoreDevice -> onNoMoreDevice(state)
        }
    }

    private fun onFound(
        state: State<D>,
        device: D,
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

    private fun onReply(
        state: State<D>,
        reply: BleConnectEvent<D>,
    ): State<D> = when (reply) {
        is BleConnectEvent.Unsupported -> {
            val (device, part, metrics) = reply
            onUnsupported(state, device, part, metrics)
        }

        is BleConnectEvent.Notify -> {
            val (device, meter) = reply
            onNotify(state, device, meter)
        }

        is BleConnectEvent.Fatal -> {
            val (device, _) = reply
            onFatal(state, device)
        }
    }

    private fun onUnsupported(
        state: State<D>,
        device: D,
        part: Boolean,
        metrics: List<BleMetric>,
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

                state.copy(
                    metrics = emptyList(),
                    pending = pending.drop(1),
                    jobs = actives + (head.id() to fire(head, metrics)),
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

    private fun onNotify(
        state: State<D>,
        device: D,
        meter: BleMeter,
    ): State<D> {
        send(BleEvent.Available(device = device.name(), meter = meter))
        return state
    }

    private fun onFatal(
        state: State<D>,
        device: D,
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

    private fun onNoMoreDevice(
        state: State<D>,
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
}

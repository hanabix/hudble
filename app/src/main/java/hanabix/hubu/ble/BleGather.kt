package hanabix.hubu.ble

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
internal class DefaultBleGather<T>(
    private val scope: CoroutineScope,
    private val scan: BleScan<T>,
    private val connect: BleConnect<T>,
    private val info: DeviceInfo<T>,
    private val timeout: Duration = 5.seconds,
) : BleGather {
    override fun invoke(metrics: List<BleMetric>): Flow<BleEvent> = channelFlow {
        val bus = Channel<BleReact.Event<T>>(Channel.UNLIMITED)
        var state = BleReact.State<T>(unsupported = metrics)
        val reducer = BleReact(info)
        val channel = object : BleChannel<BleEvent> {
            override fun emit(a: BleEvent) {
                trySend(a)
            }

            override fun close() {
                close()
            }
        }

        val fire: BleReact.Launch<T> = { device, requested ->
            connect(requested)(device)
                .onEach { event -> bus.trySend(BleReact.Event.Connecting(event)) }
                .launchIn(scope)
        }

        val react = bus.receiveAsFlow()
            .onEach { event -> state = reducer(state, event, channel, fire) }
            .launchIn(scope)

        val scanning = scan(metrics)
            .take(metrics.size)
            .timeout(timeout)
            .onEach { value -> bus.trySend(BleReact.Event.Found(value)) }
            .onCompletion { bus.trySend(BleReact.Event.ScanningEnded) }
            .launchIn(scope)

        awaitClose {
            scanning.cancel()
            state.connecting.values.forEach(Job::cancel)
            react.cancel()
            bus.close()
        }
    }
}

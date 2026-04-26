package hanabix.hubu.model

import java.util.ArrayDeque
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow

private typealias Cancel = () -> Unit
private typealias LaunchConnect<A> = (A, Set<Metric>) -> Cancel

fun interface Gather<A> {
    operator fun invoke(metrics: Set<Metric>): Flow<Meter<A>>

    companion object {
        operator fun <A> invoke(
            find: Find<A>,
            connect: Connect<A>,
        ): Gather<A> = DefaultGather(find, connect)
    }
}

private class DefaultGather<A>(
    private val find: Find<A>,
    private val connect: Connect<A>,
) : Gather<A> {
    override fun invoke(metrics: Set<Metric>): Flow<Meter<A>> = channelFlow {
        val bus = Channel<Event<A>>(Channel.UNLIMITED)
        val channel = this as SendChannel<Meter<A>>
        val launchConnect: LaunchConnect<A> = { source, requested ->
            val job = connect(source, requested)
                .onEach { status -> bus.trySend(status.asEvent(source)) }
                .launchIn(this)

            val cancel: Cancel = { job.cancel() }
            cancel
        }
        val react = React(channel, launchConnect)

        var state: State<A> = State(unsupported = metrics)

        val reducer = bus.receiveAsFlow()
            .onEach { event -> state = react(state, event) }
            .launchIn(this)

        val finder = find(metrics)
            .onEach { status -> bus.trySend(status.asEvent()) }
            .launchIn(this)

        awaitClose {
            finder.cancel()
            reducer.cancel()
            state.connectings.values.forEach { it() }
            bus.close()
        }
    }
}

private fun <A> Find.Status<A>.asEvent(): Event<A> = when (this) {
    is Find.Status.Found -> Event.Found(source)
    is Find.Status.Done -> Event.Done(cause)
}

private fun <A> Connect.Status<A>.asEvent(source: A): Event<A> = when (this) {
    is Connect.Status.Unsupported -> Event.Unsupported(metrics)
    is Connect.Status.Received -> Event.Received(meter)
    is Connect.Status.Disconnected -> Event.Disconnected(source, cause)
}

private fun interface React<A> {
    operator fun invoke(state: State<A>, event: Event<A>): State<A>

    companion object {
        operator fun <A> invoke(
            channel: SendChannel<Meter<A>>,
            connect: LaunchConnect<A>,
        ): React<A> = DefaultReact(channel, connect)
    }
}

private data class State<A>(
    val unsupported: Set<Metric>,
    val pending: ArrayDeque<A> = ArrayDeque(),
    val connectings: Map<A, Cancel> = emptyMap(),
    val noMoreSource: Boolean = false,
)

private sealed interface Event<out A> {
    data class Found<A>(
        val source: A,
    ) : Event<A>

    data class Done(
        val cause: Throwable?,
    ) : Event<Nothing>

    data class Unsupported(
        val metrics: Set<Metric>,
    ) : Event<Nothing>

    data class Received<A>(
        val meter: Meter<A>,
    ) : Event<A>

    data class Disconnected<A>(
        val source: A,
        val cause: Throwable?,
    ) : Event<A>
}

private class DefaultReact<A>(
    private val channel: SendChannel<Meter<A>>,
    private val connect: LaunchConnect<A>,
) : React<A> {
    override fun invoke(state: State<A>, event: Event<A>): State<A> = when (event) {
        is Event.Found -> {
            state.pending.offer(event.source)
            state.launchPending()
        }

        is Event.Done -> state
            .noMoreSource()
            .sendIfUnavailable()

        is Event.Unsupported -> state
            .unsupported(event.metrics)
            .launchPending()

        is Event.Received -> {
            channel.trySend(event.meter)
            state
        }

        is Event.Disconnected -> state
            .connecting { it - event.source }
            .sendIfUnavailable()
    }

    private fun State<A>.unsupported(metrics: Set<Metric>): State<A> =
        copy(unsupported = metrics)

    private fun State<A>.noMoreSource(): State<A> =
        copy(noMoreSource = true)

    private fun State<A>.connecting(fn: (Map<A, Cancel>) -> Map<A, Cancel>): State<A> =
        copy(connectings = fn(connectings))

    private fun State<A>.launchPending(): State<A> {
        if (unsupported.isEmpty() || pending.isEmpty()) return this

        val source = pending.removeFirst()
        return copy(
            unsupported = emptySet(),
            connectings = connectings + (source to connect(source, unsupported)),
        )
    }

    private fun State<A>.sendIfUnavailable(): State<A> {
        if (noMoreSource && pending.isEmpty() && connectings.isEmpty()) {
            channel.close()
        }
        return this
    }
}

package hanabix.hubu.model

import kotlinx.coroutines.flow.Flow

fun interface Connect<A> {
    operator fun invoke(source: A, metrics: Set<Metric>): Flow<Status<A>>

    sealed interface Status<out A> {
        data class Unsupported(
            val metrics: Set<Metric>,
        ) : Status<Nothing>

        data class Received<A>(
            val meter: Meter<A>,
        ) : Status<A>

        data class Disconnected<A>(
            val source: A,
            val cause: Throwable?,
        ) : Status<A>
    }
}

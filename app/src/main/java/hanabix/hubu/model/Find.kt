package hanabix.hubu.model

import kotlinx.coroutines.flow.Flow

fun interface Find<A> {
    operator fun invoke(metrics: Set<Metric>): Flow<Status<A>>

    sealed interface Status<out A> {
        data class Found<A>(
            val source: A,
        ) : Status<A>

        data class Done(
            val cause: Throwable?,
        ) : Status<Nothing>
    }
}

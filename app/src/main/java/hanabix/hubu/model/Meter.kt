package hanabix.hubu.model

import java.util.UUID

data class Meter<A>(
    val source: A,
    val metric: Metric,
    val data: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is Meter<*> &&
            source == other.source &&
            metric == other.metric &&
            data.contentEquals(other.data)

    override fun hashCode(): Int = 31 * (31 * source.hashCode() + metric.hashCode()) + data.contentHashCode()
}

data class Metric(
    val service: UUID,
    val characteristic: UUID,
)

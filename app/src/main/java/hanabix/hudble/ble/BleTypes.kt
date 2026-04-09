package hanabix.hudble.ble

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow

internal fun interface BleScan<D> {
    operator fun invoke(metrics: List<BleMetric>): Flow<D>
}

internal fun interface BleConnect<D> {
    operator fun invoke(metrics: List<BleMetric>): (D) -> Flow<BleConnectEvent<D>>
}

internal fun interface BleGather {
    operator fun invoke(metrics: List<BleMetric>): Flow<BleEvent>
}

internal fun interface ToConnect<D> {
    operator fun invoke(device: D, metrics: List<BleMetric>): Job
}

internal enum class BleMetric(
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

internal data class BleMeter(
    val metric: BleMetric,
    val data: ByteArray,
)

internal sealed interface BleConnectEvent<out D> {
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

internal sealed interface BleEvent {
    data class Available(
        val device: String,
        val meter: BleMeter,
    ) : BleEvent

    data object Unavailable : BleEvent
}

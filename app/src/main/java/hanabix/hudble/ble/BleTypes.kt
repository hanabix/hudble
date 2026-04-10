package hanabix.hudble.ble

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow

internal fun interface BleScan<T> {
    operator fun invoke(metrics: List<BleMetric>): Flow<T>
}

internal fun interface BleConnect<T> {
    operator fun invoke(metrics: List<BleMetric>): (T) -> Flow<BleConnectEvent<T>>
}

internal fun interface BleGather {
    operator fun invoke(metrics: List<BleMetric>): Flow<BleEvent>
}

internal fun interface ToConnect<T> {
    operator fun invoke(value: T, metrics: List<BleMetric>): Job
}

internal interface BleInfo<T> {
    fun id(value: T): String
    fun name(value: T): String
}

internal data class ScannedDevice(
    val device: BluetoothDevice,
    val name: String,
)

internal val ScannedDeviceBleInfo = object : BleInfo<ScannedDevice> {
    override fun id(value: ScannedDevice): String = value.device.address

    override fun name(value: ScannedDevice): String = value.name
}

internal const val STATUS_CONNECTING = "Connecting"
internal const val STATUS_TAP_TO_RECONNECT = "Tap to Reconnect"

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

internal sealed interface BleConnectEvent<out T> {
    data class Unsupported<T>(
        val value: T,
        val part: Boolean,
        val metrics: List<BleMetric>,
    ) : BleConnectEvent<T>

    data class Notify<T>(
        val value: T,
        val meter: BleMeter,
    ) : BleConnectEvent<T>

    data class Fatal<T>(
        val value: T,
        val cause: String,
    ) : BleConnectEvent<T>
}

internal sealed interface BleEvent {
    data class Available(
        val device: String,
        val meter: BleMeter,
    ) : BleEvent

    data object Unavailable : BleEvent
}

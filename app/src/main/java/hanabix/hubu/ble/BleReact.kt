package hanabix.hubu.ble

import kotlinx.coroutines.Job

internal fun interface BleReact<T> {
    typealias Launch<T> = (T, List<BleMetric>) -> Job

    operator fun invoke(
        state: State<T>,
        event: Event<T>,
        channel: BleChannel<BleEvent>,
        launch: Launch<T>,
    ): State<T>

    companion object {
        operator fun <T> invoke(info: DeviceInfo<T>): BleReact<T> = BleReact { state, event, channel, launch ->
            when (event) {
                is BleReact.Event.Found -> state.pending { it + event.device }.launchPending(info, launch)

                is BleReact.Event.Connecting -> when (val conn = event.wrapped) {
                    is BleConnect.Event.Connected ->
                        state.unsupported(conn.unsupported).launchPending(info, launch)

                    is BleConnect.Event.Abandon -> {
                        state.unsupported(conn.unsupported)
                            .connecting { it - with(info) { conn.device.id() } }
                            .launchPending(info, launch)
                            .sendIfUnavailable(channel)
                    }

                    is BleConnect.Event.Notify -> {
                        channel.emit(BleEvent.Available(device = with(info) { conn.device.name() }, meter = conn.meter))
                        state
                    }

                    is BleConnect.Event.Disconnected -> {
                        state.connecting { it - with(info) { conn.device.id() } }.sendIfUnavailable(channel)
                    }
                }

                BleReact.Event.ScanningEnded -> state.scanningEnded().sendIfUnavailable(channel)
            }
        }
    }

    data class State<T>(
        val unsupported: List<BleMetric>,
        val scanningEnded: Boolean = false,
        val pending: List<T> = emptyList(),
        val connecting: Map<DeviceId, Job> = emptyMap(),
    ) {
        fun unsupported(metrics: List<BleMetric>): State<T> =
            copy(unsupported = metrics)

        fun scanningEnded(): State<T> =
            copy(scanningEnded = true)

        fun pending(fn: (List<T>) -> List<T>): State<T> =
            copy(pending = fn(pending))

        fun connecting(fn: (Map<DeviceId, Job>) -> Map<DeviceId, Job>): State<T> =
            copy(connecting = fn(connecting))

        fun launchPending(
            info: DeviceInfo<T>,
            launch: Launch<T>,
        ): State<T> {
            if (unsupported.isEmpty() || pending.isEmpty()) {
                return this
            }

            val head = pending.first()
            return copy(
                unsupported = emptyList(),
                pending = pending.drop(1),
                connecting = connecting + (with(info) { head.id() } to launch(head, unsupported)),
            )
        }

        fun sendIfUnavailable(
            channel: BleChannel<BleEvent>,
        ): State<T> {
            if (scanningEnded && connecting.isEmpty() && pending.isEmpty()) {
                channel.emit(BleEvent.Unavailable)
                channel.close()
            }

            return this
        }
    }

    sealed interface Event<out T> {
        data class Found<T>(val device: T) : Event<T>
        data object ScanningEnded : Event<Nothing>
        data class Connecting<T>(
            val wrapped: BleConnect.Event<T>,
        ) : Event<T>
    }
}

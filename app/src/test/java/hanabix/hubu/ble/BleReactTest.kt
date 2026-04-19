package hanabix.hubu.ble

import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BleReactTest {

    @Test
    fun `found launches first device and queues later ones`() {
        val react = BleReact(FakeInfo())
        val launched = mutableListOf<Pair<String, List<BleMetric>>>()
        val events = mutableListOf<BleEvent>()
        val state = BleReact.State<String>(unsupported = requestedMetrics())
        val channel = channel(events)

        val next = react(state, BleReact.Event.Found("a"), channel, launch(launched))
        val queued = react(next, BleReact.Event.Found("b"), channel, launch(launched))

        assertEquals(listOf("a" to requestedMetrics()), launched)
        assertEquals(emptyList<BleEvent>(), events)
        assertTrue(next.unsupported.isEmpty())
        assertEquals(emptyList<String>(), next.pending)
        assertEquals(listOf("b"), queued.pending)
    }

    @Test
    fun `scanning ended without active jobs emits unavailable`() {
        val react = BleReact(FakeInfo())
        val events = mutableListOf<BleEvent>()
        val channel = channel(events)

        val next = react(
            BleReact.State(unsupported = requestedMetrics()),
            BleReact.Event.ScanningEnded,
            channel,
            launch(),
        )

        assertEquals(listOf(BleEvent.Unavailable), events)
        assertTrue(next.scanningEnded)
    }

    @Test
    fun `notify emits available`() {
        val react = BleReact(FakeInfo(mapOf("a" to "Enduro 2")))
        val events = mutableListOf<BleEvent>()
        val channel = channel(events)
        val meter = BleMeter(BleMetric.HeartRate, byteArrayOf(0x06, 0x72))

        val next = react(
            BleReact.State(unsupported = requestedMetrics()),
            BleReact.Event.Connecting(BleConnect.Event.Notify("a", meter)),
            channel,
            launch(),
        )

        assertEquals(
            listOf(BleEvent.Available(device = "Enduro 2", meter = meter)),
            events,
        )
        assertEquals(requestedMetrics(), next.unsupported)
    }

    @Test
    fun `unsupported retries pending device`() {
        val react = BleReact(FakeInfo())
        val launched = mutableListOf<Pair<String, List<BleMetric>>>()
        val events = mutableListOf<BleEvent>()
        val channel = channel(events)
        val first = BleReact.State<String>(unsupported = requestedMetrics())
        val afterFirst = react(first, BleReact.Event.Found("a"), channel, launch(launched))
        val afterPending = react(afterFirst, BleReact.Event.Found("b"), channel, launch(launched))
        val next = react(
            afterPending,
            BleReact.Event.Connecting(
                BleConnect.Event.Connected(listOf(BleMetric.HeartRate)),
            ),
            channel,
            launch(launched),
        )

        assertEquals(
            listOf(
                "a" to requestedMetrics(),
                "b" to listOf(BleMetric.HeartRate),
            ),
            launched,
        )
        assertEquals(emptyList<BleEvent>(), events)
        assertEquals(emptyList<String>(), next.pending)
        assertTrue(next.connecting.containsKey("b"))
    }

    @Test
    fun `fatal after scan end emits unavailable when idle`() {
        val react = BleReact(FakeInfo())
        val launched = mutableListOf<Pair<String, List<BleMetric>>>()
        val events = mutableListOf<BleEvent>()
        val channel = channel(events)
        val afterFound = react(
            BleReact.State(unsupported = requestedMetrics()),
            BleReact.Event.Found("a"),
            channel,
            launch(launched),
        )
        val afterScan = react(afterFound, BleReact.Event.ScanningEnded, channel, launch(launched))
        val next = react(
            afterScan,
            BleReact.Event.Connecting(BleConnect.Event.Disconnected("a", "gone")),
            channel,
            launch(launched),
        )

        assertEquals(listOf(BleEvent.Unavailable), events)
        assertTrue(next.connecting.isEmpty())
    }

    private fun requestedMetrics(): List<BleMetric> = listOf(
        BleMetric.HeartRate,
        BleMetric.RunSpeedCadence,
    )

    private fun launch(
        launches: MutableList<Pair<String, List<BleMetric>>> = mutableListOf(),
    ): BleReact.Launch<String> = { value, metrics ->
        launches += value to metrics
        Job()
    }

    private fun channel(events: MutableList<BleEvent>): BleChannel<BleEvent> = object : BleChannel<BleEvent> {
        override fun emit(a: BleEvent) {
            events += a
        }

        override fun close() = Unit
    }

    private class FakeInfo(
        private val names: Map<String, String> = emptyMap(),
    ) : DeviceInfo<String> {
        override fun String.id(): String = this

        override fun String.name(): String = names[this] ?: this
    }
}

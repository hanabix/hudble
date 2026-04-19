package hanabix.hubu.ble

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class BleGatherTest {

    @Test
    fun `emits available on notify and unavailable after fatal`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val scan = FakeScan()
        val connect = FakeConnect()
        val gather = DefaultBleGather(scope, scan.asScan(), connect.asConnect(), FakeInfo())
        val events = mutableListOf<BleEvent>()

        gather(requestedMetrics()).onEach { events += it }.launchIn(scope)

        scan.emit("a")
        scan.close()

        waitUntil("connections") {
            connect.invoked.contains("a")
        }

        connect.session("a").emitNotify(
            BleMetric.HeartRate,
            byteArrayOf(0x06, 0x72),
        )

        waitUntil("available event") {
            events.any { it is BleEvent.Available }
        }

        connect.session("a").emitDisconnected("gone")

        waitUntil("unavailable") {
            events.lastOrNull() is BleEvent.Unavailable
        }

        assertEquals(
            listOf("a"),
            connect.invoked,
        )
    }

    @Test
    fun `fatal does not cancel the connection immediately`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val scan = FakeScan()
        val connect = FakeConnect()
        val gather = DefaultBleGather(
            scope,
            scan.asScan(),
            connect.asConnect(),
            FakeInfo(mapOf("alpha" to "Enduro 2")),
        )
        val events = mutableListOf<BleEvent>()

        gather(requestedMetrics()).onEach { events += it }.launchIn(scope)

        scan.emit("a")

        waitUntil("connection") {
            connect.invoked.contains("a")
        }

        connect.session("a").emitDisconnected("gone")
        connect.session("a").emitNotify(
            BleMetric.HeartRate,
            byteArrayOf(0x06, 0x72),
        )

        waitUntil("available after fatal") {
            events.any { it is BleEvent.Available }
        }

        assertTrue(events.none { it is BleEvent.Unavailable })
    }

    @Test
    fun `ignores unsupported and keeps forwarding notify`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val scan = FakeScan()
        val connect = FakeConnect()
        val gather = DefaultBleGather(scope, scan.asScan(), connect.asConnect(), FakeInfo())
        val events = mutableListOf<BleEvent>()

        gather(requestedMetrics()).onEach { events += it }.launchIn(scope)

        scan.emit("a")
        scan.close()

        waitUntil("connection") {
            connect.invoked.contains("a")
        }

        connect.session("a").emitConnected(
            metrics = listOf(BleMetric.HeartRate),
        )
        connect.session("a").emitNotify(BleMetric.HeartRate, byteArrayOf(0x06, 0x72))

        waitUntil("available") {
            events.any { it is BleEvent.Available }
        }

        assertEquals(
            listOf(BleMetric.HeartRate, BleMetric.RunSpeedCadence),
            connect.metricsInvoked.single(),
        )
    }

    @Test
    fun `abandon emits unavailable when scan ended and idle`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val scan = FakeScan()
        val connect = FakeConnect()
        val gather = DefaultBleGather(scope, scan.asScan(), connect.asConnect(), FakeInfo())
        val events = mutableListOf<BleEvent>()

        gather(requestedMetrics()).onEach { events += it }.launchIn(scope)

        scan.emit("a")
        scan.close()

        waitUntil("connection") {
            connect.invoked.contains("a")
        }

        connect.session("a").emitAbandon(
            metrics = listOf(BleMetric.HeartRate),
        )

        waitUntil("unavailable") {
            events.lastOrNull() is BleEvent.Unavailable
        }

        assertEquals(
            listOf("a"),
            connect.invoked,
        )
    }

    @Test
    fun `abandon retries pending device before unavailable`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val scan = FakeScan()
        val connect = FakeConnect()
        val gather = DefaultBleGather(scope, scan.asScan(), connect.asConnect(), FakeInfo())
        val events = mutableListOf<BleEvent>()

        gather(requestedMetrics()).onEach { events += it }.launchIn(scope)

        scan.emit("a")
        scan.emit("b")
        scan.close()

        waitUntil("first connection") {
            connect.invoked.contains("a")
        }

        connect.session("a").emitAbandon(
            metrics = listOf(BleMetric.HeartRate),
        )

        waitUntil("retry connection") {
            connect.invoked.contains("b")
        }

        assertTrue(events.none { it is BleEvent.Unavailable })
        assertEquals(listOf("a", "b"), connect.invoked)
    }

    @Test
    fun `notify keeps pending device available for retry`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val scan = FakeScan()
        val connect = FakeConnect()
        val gather = DefaultBleGather(scope, scan.asScan(), connect.asConnect(), FakeInfo())

        gather(requestedMetrics()).launchIn(scope)

        scan.emit("a")
        scan.emit("a")
        scan.close()

        waitUntil("first connection") {
            connect.invoked.size == 1
        }

        connect.session("a").emitNotify(
            BleMetric.HeartRate,
            byteArrayOf(0x06, 0x72),
        )
        connect.session("a").emitConnected(
            metrics = listOf(BleMetric.HeartRate),
        )

        waitUntil("retry connection") {
            connect.invoked.size == 2
        }

        assertEquals(
            listOf("a", "a"),
            connect.invoked,
        )
    }

    @Test
    fun `notify derives available device from reply device`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val scan = FakeScan()
        val connect = FakeConnect()
        val gather = DefaultBleGather(
            scope,
            scan.asScan(),
            connect.asConnect(),
            FakeInfo(mapOf("alpha" to "Enduro 2")),
        )
        val events = mutableListOf<BleEvent>()

        gather(requestedMetrics()).onEach { events += it }.launchIn(scope)

        scan.emit("alpha")
        scan.close()

        waitUntil("connection") {
            connect.invoked.contains("alpha")
        }

        connect.session("alpha").emitNotify(
            BleMetric.HeartRate,
            byteArrayOf(0x06, 0x72),
        )

        waitUntil("available") {
            events.any {
                it is BleEvent.Available && it.device == "Enduro 2"
            }
        }

        assertEquals("Enduro 2", (events.last { it is BleEvent.Available } as BleEvent.Available).device)
    }

    @Test
    fun `emits unavailable when scan ends without active devices`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val scan = FakeScan()
        val connect = FakeConnect()
        val gather = DefaultBleGather(scope, scan.asScan(), connect.asConnect(), FakeInfo())
        val events = mutableListOf<BleEvent>()

        gather(requestedMetrics()).onEach { events += it }.launchIn(scope)

        scan.close()

        waitUntil("unavailable") {
            events.lastOrNull() is BleEvent.Unavailable
        }
    }

    @Test
    fun `scan end keeps completed connections without unavailable`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val scan = FakeScan()
        val connect = BleConnect<String> { _: List<BleMetric> ->
            { _: String -> emptyFlow() }
        }
        val gather = DefaultBleGather(scope, scan.asScan(), connect, FakeInfo())
        val events = mutableListOf<BleEvent>()

        gather(requestedMetrics()).onEach { events += it }.launchIn(scope)

        scan.emit("a")
        scan.close()

        delay(50)

        assertTrue(events.none { it is BleEvent.Unavailable })
    }

    @Test
    fun `pending blocks unavailable after fatal`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val scan = FakeScan()
        val connect = FakeConnect()
        val gather = DefaultBleGather(scope, scan.asScan(), connect.asConnect(), FakeInfo())
        val events = mutableListOf<BleEvent>()

        gather(requestedMetrics()).onEach { events += it }.launchIn(scope)

        scan.emit("a")
        scan.emit("b")

        waitUntil("first connection") {
            connect.invoked.contains("a")
        }

        scan.close()

        connect.session("a").emitDisconnected("gone")

        delay(50)

        assertTrue(events.none { it is BleEvent.Unavailable })
        assertEquals(listOf("a"), connect.invoked)
    }

    @Test
    fun `keeps later found devices pending after first connection`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val connect = FakeConnect()
        val scan = BleScan<String> { _: List<BleMetric> -> flowOf("a", "b", "c") }
        val gather = DefaultBleGather(scope, scan, connect.asConnect(), FakeInfo())

        gather(requestedMetrics()).launchIn(scope)

        waitUntil("first connection") {
            connect.invoked.size == 1
        }

        assertEquals(listOf("a"), connect.invoked)
    }

    @Test
    fun `emits unavailable when scan times out`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val connect = FakeConnect()
        val scan = BleScan<String> { _: List<BleMetric> ->
            flow { awaitCancellation() }
        }
        val gather = DefaultBleGather(
            scope = scope,
            scan = scan,
            connect = connect.asConnect(),
            info = FakeInfo(),
            timeout = 50.milliseconds,
        )
        val events = mutableListOf<BleEvent>()

        gather(requestedMetrics()).onEach { events += it }.launchIn(scope)

        waitUntil("unavailable", timeoutMs = 500) {
            events.lastOrNull() is BleEvent.Unavailable
        }
    }

    @Test
    fun `run clears state when unavailable arrives`() = runBlocking {
        val viewModel = BleViewModel(
            scan = { flow { } },
            connect = { _ -> { flow { } } },
            info = object : DeviceInfo<ScannedDevice> {
                override fun ScannedDevice.id(): String = device.address

                override fun ScannedDevice.name(): String = name
            },
        )

        viewModel.render(
            BleEvent.Available(
                device = "a",
                meter = BleMeter(BleMetric.HeartRate, byteArrayOf(0x00, 0x72)),
            ),
        )
        viewModel.render(
            BleEvent.Available(
                device = "a",
                meter = BleMeter(
                    BleMetric.RunSpeedCadence,
                    byteArrayOf(0x00, 0x00, 0x01, 0x10),
                ),
            ),
        )

        assertEquals("a", viewModel.bleStatus.value)
        assertEquals("114", viewModel.heartRate.value)
        assertTrue(viewModel.pace.value != null)
        assertTrue(viewModel.cadence.value != null)

        viewModel.render(BleEvent.Unavailable)

        assertEquals("Tap to Reconnect", viewModel.bleStatus.value)
        assertEquals(null, viewModel.heartRate.value)
        assertEquals(null, viewModel.pace.value)
        assertEquals(null, viewModel.cadence.value)
    }

    private fun requestedMetrics(): List<BleMetric> = listOf(
        BleMetric.HeartRate,
        BleMetric.RunSpeedCadence,
    )

    private suspend fun waitUntil(
        label: String,
        timeoutMs: Long = 1_000,
        condition: () -> Boolean,
    ) {
        try {
            withTimeout(timeoutMs) {
                while (!condition()) {
                    delay(1)
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw AssertionError("Timed out waiting for $label", e)
        }
    }

    private class FakeScan {
        private val channel = Channel<String>(Channel.UNLIMITED)

        fun asScan(): BleScan<String> = BleScan { _: List<BleMetric> -> channel.receiveAsFlow() }

        suspend fun emit(device: String) {
            channel.send(device)
        }

        fun close() {
            channel.close()
        }
    }

    private class FakeInfo(
        private val names: Map<String, String> = emptyMap(),
    ) : DeviceInfo<String> {
        override fun String.id(): String = this

        override fun String.name(): String = names[this] ?: this
    }

    private class FakeConnect {
        val invoked = mutableListOf<String>()
        val metricsInvoked = mutableListOf<List<BleMetric>>()
        private val sessions = linkedMapOf<String, FakeSession>()

        fun asConnect(): BleConnect<String> = BleConnect { metrics: List<BleMetric> ->
            { device: String ->
                invoked += device
                metricsInvoked += metrics
                sessions.getOrPut(device) { FakeSession(device) }.flow()
            }
        }

        fun session(device: String): FakeSession = sessions.getValue(device)
    }

    private class FakeSession(
        private val device: String,
    ) {
        private val channel = Channel<BleConnect.Event<String>>(Channel.UNLIMITED)

        fun flow(): Flow<BleConnect.Event<String>> = channel.receiveAsFlow()

        suspend fun emitConnected(metrics: List<BleMetric>) {
            channel.send(
                BleConnect.Event.Connected(
                    unsupported = metrics,
                ),
            )
        }

        suspend fun emitAbandon(metrics: List<BleMetric>) {
            channel.send(
                BleConnect.Event.Abandon(
                    device = device,
                    unsupported = metrics,
                ),
            )
            channel.close()
        }

        suspend fun emitNotify(
            metric: BleMetric,
            data: ByteArray,
        ) {
            channel.send(
                BleConnect.Event.Notify(
                    device = device,
                    meter = BleMeter(metric = metric, data = data),
                ),
            )
        }

        suspend fun emitDisconnected(cause: String) {
            channel.send(
                BleConnect.Event.Disconnected(
                    device = device,
                    cause = cause,
                ),
            )
        }
    }
}

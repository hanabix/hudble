package hanabix.hubu.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun requestedMetrics(): Set<Metric> = setOf(heartRate(), cadence())

private fun meter(
    source: String,
    metric: Metric,
    data: ByteArray,
): Meter<String> = Meter(source = source, metric = metric, data = data)

private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { index ->
    values[index].toByte()
}

private fun heartRate(): Metric = Metric(
    service = java.util.UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB"),
    characteristic = java.util.UUID.fromString("00002A37-0000-1000-8000-00805F9B34FB"),
)

private fun cadence(): Metric = Metric(
    service = java.util.UUID.fromString("00001814-0000-1000-8000-00805F9B34FB"),
    characteristic = java.util.UUID.fromString("00002A53-0000-1000-8000-00805F9B34FB"),
)

class GatherTest {

    @Test
    fun `emits meter and completes after done`() = runBlocking {
        Harness().use { h ->
            // Given
            h.emitFound("a")
            h.awaitConnection(1)

            // When
            h.emitConnect("a", Connect.Status.Received(meter("a", heartRate(), bytes(0x06, 0x72))))
            h.emitConnect("a", Connect.Status.Disconnected("a", null))
            h.closeConnection("a")
            h.emitDone()

            // Then
            h.awaitCompletion()
            h.assertConnections(
                "a" to requestedMetrics(),
            )
            h.assertMeters(
                meter("a", heartRate(), bytes(0x06, 0x72)),
            )
        }
    }

    @Test
    fun `launches pending source after unsupported`() = runBlocking {
        Harness().use { h ->
            // Given
            h.emitFound("a")
            h.emitFound("b")
            h.awaitConnection(1)

            // When
            h.emitConnect("a", Connect.Status.Unsupported(setOf(heartRate())))
            h.emitConnect("a", Connect.Status.Disconnected("a", null))
            h.closeConnection("a")
            h.awaitConnection(2)
            h.emitConnect("b", Connect.Status.Received(meter("b", heartRate(), bytes(0x06, 0x73))))
            h.emitConnect("b", Connect.Status.Disconnected("b", null))
            h.closeConnection("b")
            h.emitDone()

            // Then
            h.awaitCompletion()
            h.assertConnections(
                "a" to requestedMetrics(),
                "b" to setOf(heartRate()),
            )
            h.assertMeters(
                meter("b", heartRate(), bytes(0x06, 0x73)),
            )
        }
    }

    @Test
    fun `done without sources closes immediately`() = runBlocking {
        Harness().use { h ->
            // Given
            h.emitDone()

            // Then
            h.awaitCompletion()
            h.assertNoMeters()
            h.assertNoConnections()
        }
    }

    private class Harness : AutoCloseable {
        private val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        private val finder = FakeFind()
        private val connector = FakeConnect()
        private val events = mutableListOf<Meter<String>>()
        private val job = Gather(finder.asFind(), connector.asConnect())
            .invoke(requestedMetrics())
            .onEach { events += it }
            .launchIn(scope)

        override fun close() {
            scope.cancel()
        }

        suspend fun awaitConnection(count: Int) {
            waitUntil("connection #$count") {
                connector.invoked.size == count
            }
        }

        suspend fun awaitCompletion() {
            waitUntil("gather completed") {
                job.isCompleted
            }
        }

        suspend fun emitFound(source: String) {
            finder.emit(Find.Status.Found(source))
        }

        suspend fun emitDone(cause: Throwable? = null) {
            finder.emit(Find.Status.Done(cause))
        }

        suspend fun emitConnect(
            source: String,
            status: Connect.Status<String>,
        ) {
            connector.emit(source, status)
        }

        fun closeConnection(source: String) {
            connector.close(source)
        }

        fun assertConnections(vararg expected: Pair<String, Set<Metric>>) {
            assertEquals(expected.toList(), connector.invoked)
        }

        fun assertMeters(vararg expected: Meter<String>) {
            assertEquals(expected.toList(), events)
        }

        fun assertNoMeters() {
            assertTrue(events.isEmpty())
        }

        fun assertNoConnections() {
            assertTrue(connector.invoked.isEmpty())
        }
    }

    private class FakeFind {
        val invoked = mutableListOf<Set<Metric>>()
        private val bus = Channel<Find.Status<String>>(Channel.UNLIMITED)

        fun asFind(): Find<String> = Find { metrics ->
            invoked += metrics
            bus.receiveAsFlow()
        }

        suspend fun emit(status: Find.Status<String>) {
            bus.send(status)
        }
    }

    private class FakeConnect {
        val invoked = mutableListOf<Pair<String, Set<Metric>>>()
        private val sessions = mutableMapOf<String, Channel<Connect.Status<String>>>()

        fun asConnect(): Connect<String> = Connect { source, metrics ->
            invoked += source to metrics
            session(source).receiveAsFlow()
        }

        suspend fun emit(
            source: String,
            status: Connect.Status<String>,
        ) {
            session(source).send(status)
        }

        fun close(source: String) {
            sessions[source]?.close()
        }

        private fun session(source: String): Channel<Connect.Status<String>> =
            sessions.getOrPut(source) { Channel(Channel.UNLIMITED) }
    }
}

private suspend fun waitUntil(
    label: String,
    timeoutMs: Long = 2000,
    block: () -> Boolean,
) {
    try {
        withTimeout(timeoutMs) {
            while (!block()) {
                delay(10)
            }
        }
    } catch (_: TimeoutCancellationException) {
        error("Timed out waiting for $label")
    }
}

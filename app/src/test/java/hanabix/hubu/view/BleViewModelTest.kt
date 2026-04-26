package hanabix.hubu.view

import android.bluetooth.BluetoothDevice
import hanabix.hubu.android.DeviceSource
import hanabix.hubu.model.Metric
import hanabix.hubu.model.Meter
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BleViewModelTest {

    @Test
    fun `render updates metrics and status`() {
        val viewModel = BleViewModel(
            gather = mockk(relaxed = true),
        )
        val device = device("watch")

        viewModel.render(
            Meter(
                source = DeviceSource(name = "a", device = device),
                metric = metric("0000180D-0000-1000-8000-00805F9B34FB", "00002A37-0000-1000-8000-00805F9B34FB"),
                data = byteArrayOf(0x00, 0x72),
            ),
        )
        viewModel.render(
            Meter(
                source = DeviceSource(name = "a", device = device),
                metric = metric("00001814-0000-1000-8000-00805F9B34FB", "00002A53-0000-1000-8000-00805F9B34FB"),
                data = byteArrayOf(0x00, 0x00, 0x01, 0x10),
            ),
        )

        assertEquals("a", viewModel.bleStatus.value)
        assertEquals("114", viewModel.heartRate.value)
        assertTrue(viewModel.pace.value != null)
        assertTrue(viewModel.cadence.value != null)
    }

    @Test
    fun `renderUnavailable clears metrics and status`() {
        val viewModel = BleViewModel(
            gather = mockk(relaxed = true),
        )
        val device = device("watch")

        viewModel.render(
            Meter(
                source = DeviceSource(name = "a", device = device),
                metric = metric("0000180D-0000-1000-8000-00805F9B34FB", "00002A37-0000-1000-8000-00805F9B34FB"),
                data = byteArrayOf(0x00, 0x72),
            ),
        )
        viewModel.renderUnavailable()

        assertEquals("Tap to Reconnect", viewModel.bleStatus.value)
        assertNull(viewModel.heartRate.value)
        assertNull(viewModel.pace.value)
        assertNull(viewModel.cadence.value)
    }

    private fun metric(service: String, characteristic: String) = Metric(
        service = java.util.UUID.fromString(service),
        characteristic = java.util.UUID.fromString(characteristic),
    )

    private fun device(name: String): BluetoothDevice = mockk<BluetoothDevice>().also {
        every { it.address } returns name
    }
}

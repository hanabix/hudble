package hanabix.hubu.ble

import android.bluetooth.BluetoothDevice
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [32])
class BleInfoTest {

    @Test
    fun `scanned device info uses device address as id`() {
        val device = mockk<BluetoothDevice>()
        every { device.address } returns "38:F9:F5:18:BC:57"
        val value = ScannedDevice(device = device, name = "Enduro 2")

        assertEquals("38:F9:F5:18:BC:57", ScannedDeviceBleInfo.id(value))
    }

    @Test
    fun `scanned device info uses frozen name`() {
        val device = mockk<BluetoothDevice>()
        every { device.address } returns "38:F9:F5:18:BC:57"
        val value = ScannedDevice(device = device, name = "Enduro 2")

        assertEquals("Enduro 2", ScannedDeviceBleInfo.name(value))
    }
}

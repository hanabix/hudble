package hanabix.hudble.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HUDUiStateTest {

    @Test
    fun previewMatchesHUDPrototypeDefaults() {
        val state = HUDUiState.preview()

        assertEquals("6'21\"", state.pace)
        assertEquals("156", state.heartRate)
        assertEquals("178", state.cadence)
        assertEquals("15:47", state.currentTime)
        assertEquals("Enduro 2", state.deviceName)
        assertEquals("87%", state.batteryLevel)
    }
}

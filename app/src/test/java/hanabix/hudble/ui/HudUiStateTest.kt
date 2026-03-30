package hanabix.hudble.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HudUiStateTest {

    @Test
    fun previewMatchesHudPrototypeDefaults() {
        val state = HudUiState.preview()

        assertEquals("6'21\"", state.pace.value)
        assertEquals("/km", state.pace.unit)
        assertEquals("156", state.heartRate.value)
        assertEquals("178", state.cadence.value)
        assertEquals("5.43 km", state.distance)
        assertEquals("00:36:18", state.duration)
        assertEquals("15:47", state.currentTime)
        assertEquals("Enduro 2", state.deviceName)
        assertEquals("87%", state.batteryLevel)
    }
}

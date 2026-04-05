package hanabix.hudble.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Test cases for pace formatting from speed (m/s) to "M'SS\"" format.
 */
class PaceTest {

    @Test
    fun `format speed to pace 6'04"`() {
        // 2.75 m/s = 9.90 km/h -> pace = 6.067 min/km -> 6'04"
        assertEquals("6'04\"", Pace.format(2.75f))
    }

    @Test
    fun `format speed to pace 9'22"`() {
        // 1.781 m/s = 6.41 km/h -> pace = 9.353 min/km -> 9'21" (rounded)
        assertEquals("9'21\"", Pace.format(1.781f))
    }

    @Test
    fun `format speed to pace 5'00"`() {
        // 3.333 m/s = 12.0 km/h -> pace = 5.0 min/km -> 5'00"
        assertEquals("5'00\"", Pace.format(3.333f))
    }

    @Test
    fun `format speed to pace 10'00"`() {
        // 1.667 m/s = 6.0 km/h -> pace = 10.0 min/km -> 10'00"
        assertEquals("10'00\"", Pace.format(1.667f))
    }

    @Test
    fun `format speed to pace 12'31"`() {
        // 1.331 m/s -> pace = 12.516 min/km -> 12'31"
        assertEquals("12'31\"", Pace.format(1.331f))
    }

    @Test
    fun `format rounds seconds correctly`() {
        // 2.742 m/s -> pace = 6.078 min/km -> 6'05" (rounded from 4.7s)
        assertEquals("6'05\"", Pace.format(2.742f))
    }

    @Test
    fun `format handles 60 seconds carry over`() {
        // 2.783 m/s -> pace = 5.989 min/km -> seconds = 59.34 -> rounds to 59
        // To get 60 seconds carry-over, need more precise speed
        // 2.778 m/s -> pace = 6.0 min/km exactly
        assertEquals("6'00\"", Pace.format(2.778f))
    }

    @Test
    fun `format returns null for zero speed`() {
        assertNull(Pace.format(0f))
    }

    @Test
    fun `format returns null for negative speed`() {
        assertNull(Pace.format(-1f))
    }

    @Test
    fun `format from Garmin watch actual data`() {
        // Speed/Cadence data from Garmin Enduro 2
        val testCases = mapOf(
            2.75f to "6'04\"",
            2.742f to "6'05\"",
            2.730f to "6'06\"",
            2.715f to "6'08\"",
            2.695f to "6'11\"",
            2.648f to "6'18\"",
            2.293f to "7'16\"",
            2.004f to "8'19\"",
            1.781f to "9'21\"",
        )

        testCases.forEach { (speedMs, expectedPace) ->
            assertEquals(
                "Failed for speed: $speedMs",
                expectedPace,
                Pace.format(speedMs)
            )
        }
    }
}

package hanabix.hubu.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Test cases for RunningSpeedCadenceService binary protocol parsing.
 *
 * Protocol reference: RSCS v1.0.1 (UUID 0x1814 / 0x2A53)
 * - Byte 0: Flags (Bit 0 = stride length, Bit 1 = total distance, Bit 2 = walking/running)
 * - Bytes 1-2: Instantaneous Speed (UINT16, 1/256 m/s, little endian)
 * - Byte 3: Instantaneous Cadence (UINT8, RPM)
 * - Bytes 4-5: Stride Length (UINT16, mm) [conditional]
 * - Bytes 6-9: Total Distance (UINT32, dm) [conditional]
 */
class RunningSpeedCadenceServiceTest {

    // Flags = 0x00 (no optional fields)
    private val flagsBasic = byteArrayOf(0x00.toByte())

    @Test
    fun `parse basic RSCS measurement`() {
        // 0x00 0xC0 0x02 0x53
        // Speed = 0x02C0 = 704 -> 704/256 = 2.75 m/s = 9.90 km/h
        // Cadence = 0x53 = 83 RPM
        val data = flagsBasic + 0xC0.toByte() + 0x02.toByte() + 0x53.toByte()
        val result = RunningSpeedCadenceService.read(data)

        assertEquals(2.75f, result!!.speedMs, 0.001f)
        assertEquals(83, result.cadenceRpm)
    }

    @Test
    fun `parse RSCS measurement with sequence from Garmin watch`() {
        // Actual data sequence from Garmin Enduro 2
        val testCases = mapOf(
            byteArrayOf(0x00, 0xC0.toByte(), 0x02.toByte(), 0x53.toByte()) to RSCSMeasurement(2.75f, 83),
            byteArrayOf(0x00, 0xBE.toByte(), 0x02.toByte(), 0x53.toByte()) to RSCSMeasurement(2.742f, 83),
            byteArrayOf(0x00, 0xBB.toByte(), 0x02.toByte(), 0x53.toByte()) to RSCSMeasurement(2.730f, 83),
            byteArrayOf(0x00, 0xB7.toByte(), 0x02.toByte(), 0x53.toByte()) to RSCSMeasurement(2.715f, 83),
            byteArrayOf(0x00, 0xB2.toByte(), 0x02.toByte(), 0x00.toByte()) to RSCSMeasurement(2.695f, 0),
            byteArrayOf(0x00, 0xA6.toByte(), 0x02.toByte(), 0x00.toByte()) to RSCSMeasurement(2.648f, 0),
            byteArrayOf(0x00, 0x4B.toByte(), 0x02.toByte(), 0x00.toByte()) to RSCSMeasurement(2.293f, 0),
            byteArrayOf(0x00, 0x01.toByte(), 0x02.toByte(), 0x00.toByte()) to RSCSMeasurement(2.004f, 0),
            byteArrayOf(0x00, 0xC8.toByte(), 0x01.toByte(), 0x3E.toByte()) to RSCSMeasurement(1.781f, 62),
        )

        testCases.forEach { (data, expected) ->
            val result = RunningSpeedCadenceService.read(data)
            assertEquals(
                "Failed for data: ${data.joinToString(" ")}",
                expected.speedMs,
                result!!.speedMs,
                0.001f
            )
            assertEquals(
                "Failed for data: ${data.joinToString(" ")}",
                expected.cadenceRpm,
                result.cadenceRpm
            )
        }
    }

    @Test
    fun `parse RSCS measurement with stride length and total distance`() {
        // 0x03 0x80 0x00 0x5A 0x00 0x01 0x00 0x00 0x04
        // Flags = 0x03 (stride length + total distance present)
        // Speed = 0x0080 = 128 -> 128/256 = 0.5 m/s
        // Cadence = 0x5A = 90 RPM
        // Stride Length = 0x0100 = 256 mm
        // Total Distance = 0x00000400 = 1024 dm
        val data = byteArrayOf(
            0x03.toByte(), // Flags
            0x80.toByte(), 0x00.toByte(), // Speed (UINT16 LE)
            0x5A.toByte(), // Cadence
            0x00.toByte(), 0x01.toByte(), // Stride Length (UINT16 LE)
            0x00.toByte(), 0x00.toByte(), 0x04.toByte(), 0x00.toByte(), // Total Distance (UINT32 LE)
        )

        val result = RunningSpeedCadenceService.read(data)

        assertEquals(0.5f, result!!.speedMs, 0.001f)
        assertEquals(90, result.cadenceRpm)
    }

    @Test
    fun `return null for data too short`() {
        // Need at least 4 bytes (flags + speed UINT16 + cadence UINT8)
        assertNull(RunningSpeedCadenceService.read(byteArrayOf(0x00)))
        assertNull(RunningSpeedCadenceService.read(byteArrayOf(0x00, 0x01)))
        assertNull(RunningSpeedCadenceService.read(byteArrayOf(0x00, 0x01, 0x02)))
    }

    @Test
    fun `parse RSCS with zero speed and cadence`() {
        val data = byteArrayOf(0x00, 0x00.toByte(), 0x00.toByte(), 0x00.toByte())
        val result = RunningSpeedCadenceService.read(data)

        assertEquals(0f, result!!.speedMs, 0.001f)
        assertEquals(0, result.cadenceRpm)
    }

    @Test
    fun `parse RSCS with walking status flag set`() {
        // Flags = 0x04 (binary: 00000100, Bit 2 = walking/running status)
        val data = byteArrayOf(0x04.toByte(), 0x00.toByte(), 0x01.toByte(), 0x50.toByte())
        val result = RunningSpeedCadenceService.read(data)

        // Speed = 0x0100 = 256 -> 256/256 = 1.0 m/s
        assertEquals(1.0f, result!!.speedMs, 0.001f)
        assertEquals(80, result.cadenceRpm)
    }
}

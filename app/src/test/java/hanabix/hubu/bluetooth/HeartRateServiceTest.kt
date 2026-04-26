package hanabix.hubu.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Test cases for HeartRateService binary protocol parsing.
 *
 * Protocol reference: HRS v1.0 (UUID 0x180D / 0x2A37)
 * - Byte 0: Flags (Bit 0 = value format, Bit 1-2 = sensor contact, Bit 3 = energy expended, Bit 4 = RR-interval)
 * - Byte 1-2: Heart Rate Value (UINT8 or UINT16, little endian)
 */
class HeartRateServiceTest {

    // Flags = 0x06 (binary: 00000110)
    // Bit 1=1: Sensor contact detected
    // Bit 2=1: Sensor contact supported
    // Bit 0=0: UINT8 format
    private val flagsUint8WithContact = byteArrayOf(0x06.toByte())

    // Flags = 0x07 (binary: 00000111)
    // Bit 0=1: UINT16 format
    private val flagsUint16WithContact = byteArrayOf(0x07.toByte())

    @Test
    fun `parse UINT8 heart rate`() {
        // 0x06 0x72 -> HR = 0x72 = 114 bpm
        val data = flagsUint8WithContact + 0x72.toByte()
        assertEquals(114, HeartRateService.read(data))
    }

    @Test
    fun `parse UINT8 heart rate with sequence from Garmin watch`() {
        // Actual data sequence from Garmin Enduro 2
        val testCases = mapOf(
            byteArrayOf(0x06, 0x72.toByte()) to 114,
            byteArrayOf(0x06, 0x73.toByte()) to 115,
            byteArrayOf(0x06, 0x74.toByte()) to 116,
            byteArrayOf(0x06, 0x75.toByte()) to 117,
            byteArrayOf(0x06, 0x76.toByte()) to 118,
            byteArrayOf(0x06, 0x77.toByte()) to 119,
            byteArrayOf(0x06, 0x78.toByte()) to 120,
            byteArrayOf(0x06, 0x79.toByte()) to 121,
            byteArrayOf(0x06, 0x7A.toByte()) to 122,
        )

        testCases.forEach { (data, expected) ->
            assertEquals("Failed for data: ${data.joinToString(" ")}", expected, HeartRateService.read(data))
        }
    }

    @Test
    fun `parse UINT16 heart rate`() {
        // 0x07 0x80 0x00 -> HR = 0x0080 = 128 bpm (little endian)
        val data = flagsUint16WithContact + 0x80.toByte() + 0x00.toByte()
        assertEquals(128, HeartRateService.read(data))
    }

    @Test
    fun `parse UINT16 heart rate with value greater than 255`() {
        // 0x07 0x00 0x01 -> HR = 0x0100 = 256 bpm
        val data = flagsUint16WithContact + 0x00.toByte() + 0x01.toByte()
        assertEquals(256, HeartRateService.read(data))
    }

    @Test
    fun `return null for empty data`() {
        assertNull(HeartRateService.read(byteArrayOf()))
    }

    @Test
    fun `return null for UINT8 with missing value byte`() {
        // Only flags, no HR value
        assertNull(HeartRateService.read(flagsUint8WithContact))
    }

    @Test
    fun `return null for UINT16 with missing value bytes`() {
        // Flags + only 1 value byte (need 2 for UINT16)
        val data = flagsUint16WithContact + 0x80.toByte()
        assertNull(HeartRateService.read(data))
    }

    @Test
    fun `parse UINT8 with additional fields (energy expended, RR-interval)`() {
        // Flags = 0x1E (binary: 00011110)
        // Bit 1=1: Sensor contact
        // Bit 2=1: Sensor contact supported
        // Bit 3=1: Energy expended present
        // Bit 4=1: RR-interval present
        // Data: 0x1E 0x70 0x00 0x01 0x00 0x02
        // HR = 0x70 = 112, Energy = 0x0100 = 256 kJ, RR = 0x0200 = 512/1024 s
        val data = byteArrayOf(
            0x1E.toByte(), // Flags
            0x70.toByte(), // HR (UINT8)
            0x00.toByte(), 0x01.toByte(), // Energy Expended (UINT16 LE)
            0x00.toByte(), 0x02.toByte(), // RR-Interval (UINT16 LE)
        )
        assertEquals(112, HeartRateService.read(data))
    }
}

package hanabix.hubu.bluetooth

/**
 * Parses Heart Rate Measurement characteristic data (UUID 0x2A37).
 *
 * Binary format (Little Endian):
 * - Byte 0: Flags
 *   - Bit 0: Heart Rate Value Format (0 = UINT8, 1 = UINT16)
 *   - Bit 1-2: Sensor Contact Status
 *   - Bit 3: Energy Expended Status
 *   - Bit 4: RR-Interval
 * - Byte 1-2: Heart Rate Value (format determined by Flags Bit 0)
 * - Additional bytes (optional): Energy Expended, RR-Interval
 */
object HeartRateService {

    /**
     * Reads heart rate value from the raw characteristic data.
     *
     * @param data raw byte array from the Heart Rate Measurement characteristic
     * @return heart rate in BPM, or null if data is invalid
     */
    fun read(data: ByteArray): Int? {
        if (data.isEmpty()) return null

        val flags = data[0].toInt()
        val valueFormatIsUint16 = (flags and 0x01) != 0

        return if (valueFormatIsUint16) {
            if (data.size < 3) null
            else ((data[2].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        } else {
            if (data.size < 2) null
            else data[1].toInt() and 0xFF
        }
    }
}

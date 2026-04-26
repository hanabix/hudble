package hanabix.hubu.bluetooth

/**
 * Represents parsed RSCS (Running Speed and Cadence Service) measurement data.
 *
 * @param speedMs speed in meters per second
 * @param cadenceRpm cadence in revolutions per minute (steps per minute)
 */
data class RSCSMeasurement(
    val speedMs: Float,
    val cadenceRpm: Int,
)

/**
 * Parses RSC Measurement characteristic data (UUID 0x2A53).
 *
 * Binary format (Little Endian):
 * - Byte 0: Flags
 *   - Bit 0: Stride Length Present
 *   - Bit 1: Total Distance Present
 *   - Bit 2: Walking or Running Status
 * - Bytes 1-2: Instantaneous Speed (UINT16, 1/256 m/s)
 * - Byte 3: Instantaneous Cadence (UINT8, RPM)
 * - Bytes 4-5: Instantaneous Stride Length (UINT16, mm) [conditional]
 * - Bytes 6-9: Total Distance (UINT32, dm) [conditional]
 */
object RunningSpeedCadenceService {

    /**
     * Reads RSCS measurement data from the raw characteristic data.
     *
     * @param data raw byte array from the RSC Measurement characteristic
     * @return parsed measurement, or null if data is too short
     */
    fun read(data: ByteArray): RSCSMeasurement? {
        if (data.size < 4) return null

        val speedRaw = ((data[2].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        val speedMs = speedRaw / 256f
        val cadenceRpm = data[3].toInt() and 0xFF

        return RSCSMeasurement(speedMs = speedMs, cadenceRpm = cadenceRpm)
    }
}

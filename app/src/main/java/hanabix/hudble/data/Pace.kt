package hanabix.hudble.data

import kotlin.math.roundToInt

/**
 * Converts speed in meters per second to a pace string in "M'SS\"" format.
 */
object Pace {

    /**
     * @param speedMs speed in meters per second
     * @return pace string (e.g., "6'04\""), or null if speed is zero or negative
     */
    fun format(speedMs: Float): String? {
        if (speedMs <= 0f) return null

        val paceMinKm = 1000.0 / (speedMs * 60.0)
        val minutes = paceMinKm.toInt()
        val seconds = ((paceMinKm - minutes) * 60).roundToInt()

        return if (seconds >= 60) {
            "${minutes + 1}'00\""
        } else {
            "${minutes}'${seconds.toString().padStart(2, '0')}\""
        }
    }
}

package hanabix.hudble.data

/**
 * Represents the BLE device scanning state machine.
 */
sealed interface DeviceStatus {
    /** Actively scanning for devices. */
    data object Scanning : DeviceStatus

    /** A compatible device was found and scanned successfully. */
    data class Scanned(val deviceName: String) : DeviceStatus

    /** Scanning completed without finding any compatible device. Tap to rescan. */
    data object NotFound : DeviceStatus
}

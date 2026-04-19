package hanabix.hubu.ble

internal typealias DeviceId = String

internal interface DeviceInfo<T> {
    fun T.id(): DeviceId

    fun T.name(): String

    companion object {
        internal val ScannedDeviceInfo = object : DeviceInfo<ScannedDevice> {
            override fun ScannedDevice.id(): DeviceId = device.address

            override fun ScannedDevice.name(): String = name
        }
    }
}

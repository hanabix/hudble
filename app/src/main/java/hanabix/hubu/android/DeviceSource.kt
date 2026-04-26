package hanabix.hubu.android

import android.bluetooth.BluetoothDevice

data class DeviceSource(
    val name: String,
    val device: BluetoothDevice,
)

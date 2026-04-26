package hanabix.hubu.android

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BleConnectTest {

    @Test
    fun `gatt callback emits unsupported received and disconnected`() = runBlocking {
        val channel = Channel<hanabix.hubu.model.Connect.Status<DeviceSource>>(Channel.UNLIMITED)
        val source = source()
        val heartRate = metric("0000180D-0000-1000-8000-00805F9B34FB", "00002A37-0000-1000-8000-00805F9B34FB")
        val cadence = metric("00001814-0000-1000-8000-00805F9B34FB", "00002A53-0000-1000-8000-00805F9B34FB")
        val gatt = mockk<BluetoothGatt>()
        val service = mockk<BluetoothGattService>()
        val characteristic = mockk<BluetoothGattCharacteristic>()
        val descriptor = mockk<BluetoothGattDescriptor>()
        val cccd = java.util.UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
        val callback = GattCallbackBridge(
            source = source,
            requested = setOf(heartRate, cadence),
            channel = channel,
            logger = NoopLogger,
        )

        every { gatt.discoverServices() } returns true
        every { gatt.getService(heartRate.service) } returns service
        every { service.getCharacteristic(heartRate.characteristic) } returns characteristic
        every { characteristic.getDescriptor(cccd) } returns descriptor
        every { gatt.getService(cadence.service) } returns null
        every { gatt.setCharacteristicNotification(characteristic, true) } returns true
        every { descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) } returns true
        every { gatt.writeDescriptor(descriptor) } returns true
        every { characteristic.service } returns service
        every { service.uuid } returns heartRate.service
        every { characteristic.uuid } returns heartRate.characteristic
        every { characteristic.value } returns byteArrayOf(0x01, 0x02)

        connected(callback, gatt)
        callback.onServicesDiscovered(gatt, BluetoothGatt.GATT_SUCCESS)
        verify(exactly = 1) { gatt.writeDescriptor(descriptor) }
        callback.onDescriptorWrite(gatt, descriptor, BluetoothGatt.GATT_SUCCESS)
        callback.onCharacteristicChanged(gatt, characteristic)
        callback.onConnectionStateChange(
            gatt = gatt,
            status = BluetoothGatt.GATT_SUCCESS,
            newState = BluetoothProfile.STATE_DISCONNECTED,
        )

        assertEquals(
            listOf(
                hanabix.hubu.model.Connect.Status.Unsupported(setOf(cadence)),
                hanabix.hubu.model.Connect.Status.Received(
                    hanabix.hubu.model.Meter(
                        source = source,
                        metric = heartRate,
                        data = byteArrayOf(0x01, 0x02),
                    ),
                ),
                hanabix.hubu.model.Connect.Status.Disconnected(source, null),
            ),
            channel.receiveAsFlow().toList(),
        )
    }

    @Test
    fun `gatt callback writes notification descriptors sequentially`() = runBlocking {
        val channel = Channel<hanabix.hubu.model.Connect.Status<DeviceSource>>(Channel.UNLIMITED)
        val source = source()
        val first = metric("0000180D-0000-1000-8000-00805F9B34FB", "00002A37-0000-1000-8000-00805F9B34FB")
        val second = metric("00001814-0000-1000-8000-00805F9B34FB", "00002A53-0000-1000-8000-00805F9B34FB")
        val gatt = mockk<BluetoothGatt>()
        val firstService = mockk<BluetoothGattService>()
        val secondService = mockk<BluetoothGattService>()
        val firstCharacteristic = mockk<BluetoothGattCharacteristic>()
        val secondCharacteristic = mockk<BluetoothGattCharacteristic>()
        val firstDescriptor = mockk<BluetoothGattDescriptor>()
        val secondDescriptor = mockk<BluetoothGattDescriptor>()
        val cccd = java.util.UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
        val callback = GattCallbackBridge(
            source = source,
            requested = setOf(first, second),
            channel = channel,
            logger = NoopLogger,
        )

        every { gatt.discoverServices() } returns true
        every { gatt.getService(first.service) } returns firstService
        every { firstService.getCharacteristic(first.characteristic) } returns firstCharacteristic
        every { firstCharacteristic.getDescriptor(cccd) } returns firstDescriptor
        every { gatt.getService(second.service) } returns secondService
        every { secondService.getCharacteristic(second.characteristic) } returns secondCharacteristic
        every { secondCharacteristic.getDescriptor(cccd) } returns secondDescriptor
        every { gatt.setCharacteristicNotification(firstCharacteristic, true) } returns true
        every { gatt.setCharacteristicNotification(secondCharacteristic, true) } returns true
        every { firstDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) } returns true
        every { secondDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) } returns true
        every { gatt.writeDescriptor(firstDescriptor) } returns true
        every { gatt.writeDescriptor(secondDescriptor) } returns true

        connected(callback, gatt)
        callback.onServicesDiscovered(gatt, BluetoothGatt.GATT_SUCCESS)
        verify(exactly = 1) { gatt.writeDescriptor(firstDescriptor) }
        verify(exactly = 0) { gatt.writeDescriptor(secondDescriptor) }

        callback.onDescriptorWrite(gatt, firstDescriptor, BluetoothGatt.GATT_SUCCESS)
        verify(exactly = 1) { gatt.writeDescriptor(secondDescriptor) }

        callback.onDescriptorWrite(gatt, secondDescriptor, BluetoothGatt.GATT_SUCCESS)
        callback.onConnectionStateChange(
            gatt = gatt,
            status = BluetoothGatt.GATT_SUCCESS,
            newState = BluetoothProfile.STATE_DISCONNECTED,
        )

        assertEquals(
            listOf(hanabix.hubu.model.Connect.Status.Disconnected(source, null)),
            channel.receiveAsFlow().toList(),
        )
    }

    @Test
    fun `gatt callback emits disconnected on gatt error`() = runBlocking {
        val channel = Channel<hanabix.hubu.model.Connect.Status<DeviceSource>>(Channel.UNLIMITED)
        val logger = NoopLogger
        val source = source()
        val callback = GattCallbackBridge(
            source = source,
            requested = emptySet(),
            channel = channel,
            logger = logger,
        )
        val gatt = mockk<BluetoothGatt>()

        callback.onConnectionStateChange(
            gatt = gatt,
            status = 42,
            newState = BluetoothProfile.STATE_DISCONNECTED,
        )

        val disconnected = channel.receiveAsFlow().toList().single() as hanabix.hubu.model.Connect.Status.Disconnected
        assertEquals(source, disconnected.source)
        assertTrue(disconnected.cause?.message == "Gatt connection failed: status=42")
    }

    @Test
    fun `gatt callback emits disconnected on service discovery failure`() = runBlocking {
        val channel = Channel<hanabix.hubu.model.Connect.Status<DeviceSource>>(Channel.UNLIMITED)
        val source = source()
        val callback = GattCallbackBridge(
            source = source,
            requested = emptySet(),
            channel = channel,
            logger = NoopLogger,
        )
        val gatt = mockk<BluetoothGatt>()

        callback.onServicesDiscovered(gatt, 7)

        val disconnected = channel.receiveAsFlow().toList().single() as hanabix.hubu.model.Connect.Status.Disconnected
        assertEquals(source, disconnected.source)
        assertTrue(disconnected.cause?.message == "Gatt service discovery failed: status=7")
    }

    @Test
    fun `gatt callback emits disconnected on descriptor write failure`() = runBlocking {
        val channel = Channel<hanabix.hubu.model.Connect.Status<DeviceSource>>(Channel.UNLIMITED)
        val source = source()
        val callback = GattCallbackBridge(
            source = source,
            requested = emptySet(),
            channel = channel,
            logger = NoopLogger,
        )
        val gatt = mockk<BluetoothGatt>()
        val descriptor = mockk<BluetoothGattDescriptor>()

        callback.onDescriptorWrite(gatt, descriptor, 8)

        val disconnected = channel.receiveAsFlow().toList().single() as hanabix.hubu.model.Connect.Status.Disconnected
        assertEquals(source, disconnected.source)
        assertTrue(disconnected.cause?.message == "Gatt descriptor write failed: status=8")
    }

    @Test
    fun `gatt callback disconnects when notification registration fails`() = runBlocking {
        val channel = Channel<hanabix.hubu.model.Connect.Status<DeviceSource>>(Channel.UNLIMITED)
        val source = source()
        val metric = metric("0000180D-0000-1000-8000-00805F9B34FB", "00002A37-0000-1000-8000-00805F9B34FB")
        val gatt = mockk<BluetoothGatt>()
        val service = mockk<BluetoothGattService>()
        val characteristic = mockk<BluetoothGattCharacteristic>()
        val descriptor = mockk<BluetoothGattDescriptor>()
        val cccd = java.util.UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
        val callback = GattCallbackBridge(
            source = source,
            requested = setOf(metric),
            channel = channel,
            logger = NoopLogger,
        )

        every { gatt.discoverServices() } returns true
        every { gatt.getService(metric.service) } returns service
        every { service.getCharacteristic(metric.characteristic) } returns characteristic
        every { characteristic.getDescriptor(cccd) } returns descriptor
        every { gatt.setCharacteristicNotification(characteristic, true) } returns false

        connected(callback, gatt)
        callback.onServicesDiscovered(gatt, BluetoothGatt.GATT_SUCCESS)

        val disconnected = channel.receiveAsFlow().toList().single() as hanabix.hubu.model.Connect.Status.Disconnected
        assertEquals(source, disconnected.source)
        assertTrue(disconnected.cause?.message == "Gatt notification setup failed: metric=$metric")
        verify(exactly = 0) { gatt.writeDescriptor(any()) }
    }

    @Test
    fun `gatt callback disconnects when descriptor value setup fails`() = runBlocking {
        val channel = Channel<hanabix.hubu.model.Connect.Status<DeviceSource>>(Channel.UNLIMITED)
        val source = source()
        val metric = metric("0000180D-0000-1000-8000-00805F9B34FB", "00002A37-0000-1000-8000-00805F9B34FB")
        val gatt = mockk<BluetoothGatt>()
        val service = mockk<BluetoothGattService>()
        val characteristic = mockk<BluetoothGattCharacteristic>()
        val descriptor = mockk<BluetoothGattDescriptor>()
        val cccd = java.util.UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
        val callback = GattCallbackBridge(
            source = source,
            requested = setOf(metric),
            channel = channel,
            logger = NoopLogger,
        )

        every { gatt.discoverServices() } returns true
        every { gatt.getService(metric.service) } returns service
        every { service.getCharacteristic(metric.characteristic) } returns characteristic
        every { characteristic.getDescriptor(cccd) } returns descriptor
        every { gatt.setCharacteristicNotification(characteristic, true) } returns true
        every { descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) } returns false

        connected(callback, gatt)
        callback.onServicesDiscovered(gatt, BluetoothGatt.GATT_SUCCESS)

        val disconnected = channel.receiveAsFlow().toList().single() as hanabix.hubu.model.Connect.Status.Disconnected
        assertEquals(source, disconnected.source)
        assertTrue(disconnected.cause?.message == "Gatt descriptor value setup failed: metric=$metric")
        verify(exactly = 0) { gatt.writeDescriptor(any()) }
    }

    @Test
    fun `gatt callback disconnects when descriptor write request fails`() = runBlocking {
        val channel = Channel<hanabix.hubu.model.Connect.Status<DeviceSource>>(Channel.UNLIMITED)
        val source = source()
        val metric = metric("0000180D-0000-1000-8000-00805F9B34FB", "00002A37-0000-1000-8000-00805F9B34FB")
        val gatt = mockk<BluetoothGatt>()
        val service = mockk<BluetoothGattService>()
        val characteristic = mockk<BluetoothGattCharacteristic>()
        val descriptor = mockk<BluetoothGattDescriptor>()
        val cccd = java.util.UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
        val callback = GattCallbackBridge(
            source = source,
            requested = setOf(metric),
            channel = channel,
            logger = NoopLogger,
        )

        every { gatt.discoverServices() } returns true
        every { gatt.getService(metric.service) } returns service
        every { service.getCharacteristic(metric.characteristic) } returns characteristic
        every { characteristic.getDescriptor(cccd) } returns descriptor
        every { gatt.setCharacteristicNotification(characteristic, true) } returns true
        every { descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) } returns true
        every { gatt.writeDescriptor(descriptor) } returns false

        connected(callback, gatt)
        callback.onServicesDiscovered(gatt, BluetoothGatt.GATT_SUCCESS)

        val disconnected = channel.receiveAsFlow().toList().single() as hanabix.hubu.model.Connect.Status.Disconnected
        assertEquals(source, disconnected.source)
        assertTrue(disconnected.cause?.message == "Gatt descriptor write request failed")
    }

    private fun source(): DeviceSource {
        val device = mockk<BluetoothDevice>()
        every { device.address } returns "AA:BB:CC:DD:EE:FF"
        return DeviceSource(
            name = "watch",
            device = device,
        )
    }

    private fun connected(
        callback: GattCallbackBridge,
        gatt: BluetoothGatt,
    ) {
        every { gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER) } returns true
        callback.onConnectionStateChange(
            gatt = gatt,
            status = BluetoothGatt.GATT_SUCCESS,
            newState = BluetoothProfile.STATE_CONNECTED,
        )
        verify(exactly = 1) { gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER) }
    }

    private fun metric(service: String, characteristic: String) = hanabix.hubu.model.Metric(
        service = java.util.UUID.fromString(service),
        characteristic = java.util.UUID.fromString(characteristic),
    )
}

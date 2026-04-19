package hanabix.hubu.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

@Suppress("DEPRECATION")
class AndroidConnectCallbackTest {

    @Test
    fun `onConnectionStateChange emits fatal on status failure`() {
        val harness = Harness()
        val gatt = mockk<BluetoothGatt>()

        harness.callback.onConnectionStateChange(
            gatt = gatt,
            status = 1,
            newState = BluetoothProfile.STATE_CONNECTED,
        )

        assertEquals(1, harness.fatalCount())
    }

    @Test
    fun `onConnectionStateChange calls discoverServices on connected success`() {
        val harness = Harness()
        val gatt = mockk<BluetoothGatt>()
        every { gatt.discoverServices() } returns true

        harness.callback.onConnectionStateChange(
            gatt = gatt,
            status = BluetoothGatt.GATT_SUCCESS,
            newState = BluetoothProfile.STATE_CONNECTED,
        )

        verify(exactly = 1) { gatt.discoverServices() }
        assertTrue(harness.events.isEmpty())
    }

    @Test
    fun `onConnectionStateChange emits fatal when discoverServices returns false`() {
        val harness = Harness()
        val gatt = mockk<BluetoothGatt>()
        every { gatt.discoverServices() } returns false

        harness.callback.onConnectionStateChange(
            gatt = gatt,
            status = BluetoothGatt.GATT_SUCCESS,
            newState = BluetoothProfile.STATE_CONNECTED,
        )

        verify(exactly = 1) { gatt.discoverServices() }
        assertEquals(1, harness.fatalCount())
    }

    @Test
    fun `onConnectionStateChange emits fatal on disconnected`() {
        val harness = Harness()
        val gatt = mockk<BluetoothGatt>()

        harness.callback.onConnectionStateChange(
            gatt = gatt,
            status = BluetoothGatt.GATT_SUCCESS,
            newState = BluetoothProfile.STATE_DISCONNECTED,
        )

        assertEquals(1, harness.fatalCount())
    }

    @Test
    fun `onServicesDiscovered starts first supported notification without unsupported`() {
        val harness = Harness()
        val gatt = mockk<BluetoothGatt>()
        val hr = support(gatt, BleMetric.HeartRate)
        support(gatt, BleMetric.RunSpeedCadence)

        harness.callback.onServicesDiscovered(
            gatt = gatt,
            status = BluetoothGatt.GATT_SUCCESS,
        )

        verify(exactly = 1) {
            gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER)
        }
        val connected = harness.events.filterIsInstance<BleConnect.Event.Connected>()
        assertEquals(1, connected.size)
        assertTrue(connected.single().unsupported.isEmpty())
        verify(exactly = 1) {
            gatt.setCharacteristicNotification(hr.characteristic, true)
            gatt.writeDescriptor(hr.descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        }
    }

    @Test
    fun `onServicesDiscovered uses legacy descriptor write on api 32`() {
        val harness = Harness(sdkInt = 32)
        val gatt = mockk<BluetoothGatt>()
        val hr = support(gatt, BleMetric.HeartRate)
        every { gatt.getService(BleMetric.RunSpeedCadence.service) } returns null

        harness.callback.onServicesDiscovered(
            gatt = gatt,
            status = BluetoothGatt.GATT_SUCCESS,
        )

        verify(exactly = 1) {
            hr.descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(hr.descriptor)
        }
    }

    @Test
    fun `onServicesDiscovered uses api 33 descriptor write`() {
        val harness = Harness(sdkInt = 33)
        val gatt = mockk<BluetoothGatt>()
        val hr = support(gatt, BleMetric.HeartRate)
        every { gatt.getService(BleMetric.RunSpeedCadence.service) } returns null

        harness.callback.onServicesDiscovered(
            gatt = gatt,
            status = BluetoothGatt.GATT_SUCCESS,
        )

        verify(exactly = 1) {
            gatt.writeDescriptor(hr.descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        }
    }

    @Test
    fun `onServicesDiscovered emits unsupported and still starts supported notification`() {
        val harness = Harness()
        val gatt = mockk<BluetoothGatt>()
        val hr = support(gatt, BleMetric.HeartRate)
        every { gatt.getService(BleMetric.RunSpeedCadence.service) } returns null

        harness.callback.onServicesDiscovered(
            gatt = gatt,
            status = BluetoothGatt.GATT_SUCCESS,
        )

        val unsupported = harness.events.filterIsInstance<BleConnect.Event.Connected>()
        assertEquals(1, unsupported.size)
        assertEquals(listOf(BleMetric.RunSpeedCadence), unsupported.single().unsupported)
        verify(exactly = 1) {
            gatt.setCharacteristicNotification(hr.characteristic, true)
            gatt.writeDescriptor(hr.descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        }
    }

    @Test
    fun `onServicesDiscovered emits abandon when no metrics supported`() {
        val harness = Harness()
        val gatt = mockk<BluetoothGatt>()
        every { gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER) } returns true
        every { gatt.getService(BleMetric.HeartRate.service) } returns null
        every { gatt.getService(BleMetric.RunSpeedCadence.service) } returns null

        harness.callback.onServicesDiscovered(
            gatt = gatt,
            status = BluetoothGatt.GATT_SUCCESS,
        )

        val abandon = harness.events.filterIsInstance<BleConnect.Event.Abandon<ScannedDevice>>()
        assertEquals(1, abandon.size)
        assertEquals(listOf(BleMetric.HeartRate, BleMetric.RunSpeedCadence), abandon.single().unsupported)
        assertTrue(harness.closed)
        assertEquals(0, harness.fatalCount())
    }

    @Test
    fun `onServicesDiscovered emits abandon on failure`() {
        val harness = Harness()
        val gatt = mockk<BluetoothGatt>()

        harness.callback.onServicesDiscovered(
            gatt = gatt,
            status = 7,
        )

        val abandon = harness.events.filterIsInstance<BleConnect.Event.Abandon<ScannedDevice>>()
        assertEquals(1, abandon.size)
        assertEquals(listOf(BleMetric.HeartRate, BleMetric.RunSpeedCadence), abandon.single().unsupported)
        assertTrue(harness.closed)
        assertEquals(0, harness.fatalCount())
    }

    @Test
    fun `onDescriptorWrite advances to next metric on success`() {
        val harness = Harness()
        val gatt = mockk<BluetoothGatt>()
        val hr = support(gatt, BleMetric.HeartRate)
        val rsc = support(gatt, BleMetric.RunSpeedCadence)

        harness.callback.onServicesDiscovered(
            gatt = gatt,
            status = BluetoothGatt.GATT_SUCCESS,
        )

        harness.callback.onDescriptorWrite(
            gatt = gatt,
            descriptor = hr.descriptor,
            status = BluetoothGatt.GATT_SUCCESS,
        )

        verify(exactly = 1) {
            gatt.setCharacteristicNotification(hr.characteristic, true)
            gatt.writeDescriptor(hr.descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            gatt.setCharacteristicNotification(rsc.characteristic, true)
            gatt.writeDescriptor(rsc.descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        }
    }

    @Test
    fun `onDescriptorWrite emits fatal on failure`() {
        val harness = Harness(metrics = listOf(BleMetric.HeartRate))
        val gatt = mockk<BluetoothGatt>()
        val hr = support(gatt, BleMetric.HeartRate)

        harness.callback.onServicesDiscovered(
            gatt = gatt,
            status = BluetoothGatt.GATT_SUCCESS,
        )

        harness.callback.onDescriptorWrite(
            gatt = gatt,
            descriptor = hr.descriptor,
            status = 12,
        )

        assertEquals(1, harness.fatalCount())
    }

    @Test
    fun `onCharacteristicChanged emits notify for known metric`() {
        val harness = Harness()
        val gatt = mockk<BluetoothGatt>()
        val characteristic = mockk<BluetoothGattCharacteristic>()
        every { characteristic.uuid } returns BleMetric.HeartRate.characteristic

        harness.callback.onCharacteristicChanged(
            gatt = gatt,
            characteristic = characteristic,
            value = byteArrayOf(0x06, 0x72),
        )

        val notify = harness.events.filterIsInstance<BleConnect.Event.Notify<ScannedDevice>>()
        assertEquals(1, notify.size)
        assertEquals(BleMetric.HeartRate, notify.single().meter.metric)
        assertArrayEquals(byteArrayOf(0x06, 0x72), notify.single().meter.data)
    }

    @Test
    fun `legacy onCharacteristicChanged emits notify for known metric`() {
        val harness = Harness()
        val gatt = mockk<BluetoothGatt>()
        val characteristic = mockk<BluetoothGattCharacteristic>()
        every { characteristic.uuid } returns BleMetric.HeartRate.characteristic
        every { characteristic.value } returns byteArrayOf(0x06, 0x72)

        harness.callback.onCharacteristicChanged(
            gatt = gatt,
            characteristic = characteristic,
        )

        val notify = harness.events.filterIsInstance<BleConnect.Event.Notify<ScannedDevice>>()
        assertEquals(1, notify.size)
        assertEquals(BleMetric.HeartRate, notify.single().meter.metric)
        assertArrayEquals(byteArrayOf(0x06, 0x72), notify.single().meter.data)
    }

    @Test(expected = NoSuchElementException::class)
    fun `onCharacteristicChanged fails on unknown metric`() {
        val harness = Harness()
        val gatt = mockk<BluetoothGatt>()
        val characteristic = mockk<BluetoothGattCharacteristic>()
        every { characteristic.uuid } returns UUID.fromString("12345678-1234-1234-1234-1234567890AB")

        harness.callback.onCharacteristicChanged(
            gatt = gatt,
            characteristic = characteristic,
            value = byteArrayOf(0x01),
        )
    }

    @Test
    fun `multiple failures only emit one fatal`() {
        val harness = Harness()
        val gatt = mockk<BluetoothGatt>()
        every { gatt.discoverServices() } returns false
        val descriptor = mockk<BluetoothGattDescriptor>()
        val characteristic = mockk<BluetoothGattCharacteristic>()
        every { descriptor.characteristic } returns characteristic
        every { characteristic.uuid } returns BleMetric.HeartRate.characteristic

        harness.callback.onConnectionStateChange(
            gatt = gatt,
            status = BluetoothGatt.GATT_SUCCESS,
            newState = BluetoothProfile.STATE_CONNECTED,
        )

        harness.callback.onDescriptorWrite(
            gatt = gatt,
            descriptor = descriptor,
            status = 9,
        )

        assertEquals(1, harness.fatalCount())
    }

    private class Harness(
        metrics: List<BleMetric> = listOf(BleMetric.HeartRate, BleMetric.RunSpeedCadence),
        sdkInt: Int = 33,
    ) {
        val events = mutableListOf<BleConnect.Event<ScannedDevice>>()
        var closed = false
        val device = ScannedDevice(
            device = mockk(relaxed = true),
            name = "Enduro 2",
        )
        val callback = AndroidConnectCallback(
            device = device,
            metrics = metrics,
            channel = object : BleChannel<BleConnect.Event<ScannedDevice>> {
                override fun emit(a: BleConnect.Event<ScannedDevice>) {
                    events += a
                }

                override fun close() {
                    closed = true
                }
            },
            log = NoopLogger,
            sdkInt = sdkInt,
        )

        fun fatalCount(): Int = events.count { it is BleConnect.Event.Disconnected }
    }

    private data class SupportedFixture(
        val service: BluetoothGattService,
        val characteristic: BluetoothGattCharacteristic,
        val descriptor: BluetoothGattDescriptor,
    )

    private fun support(
        gatt: BluetoothGatt,
        metric: BleMetric,
    ): SupportedFixture {
        val service = mockk<BluetoothGattService>()
        val characteristic = mockk<BluetoothGattCharacteristic>()
        val descriptor = mockk<BluetoothGattDescriptor>()

        every { service.getCharacteristic(metric.characteristic) } returns characteristic
        every { characteristic.uuid } returns metric.characteristic
        every {
            characteristic.getDescriptor(
                UUID.fromString("00002902-0000-1000-8000-00805F9B34FB"),
            )
        } returns descriptor
        every { descriptor.characteristic } returns characteristic
        every { descriptor.setValue(any()) } returns true
        every { gatt.getService(metric.service) } returns service
        every { gatt.setCharacteristicNotification(characteristic, true) } returns true
        every { gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER) } returns true
        every { gatt.writeDescriptor(descriptor) } returns true
        every {
            gatt.writeDescriptor(
                descriptor,
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
            )
        } returns BluetoothStatusCodes.SUCCESS

        return SupportedFixture(service, characteristic, descriptor)
    }
}

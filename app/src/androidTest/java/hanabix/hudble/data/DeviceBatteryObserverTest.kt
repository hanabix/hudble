package hanabix.hudble.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceBatteryObserverTest {

    @Test
    fun observe_emitsCorrectBatteryLevel() = runTest {
        val mockContext = mockk<Context>()
        val mockIntent = mockk<Intent>()
        every { mockIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) } returns 87
        every { mockIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1) } returns 100
        every { mockContext.registerReceiver(any(), any<IntentFilter>()) } returns mockIntent

        val observer = DeviceBatteryObserver(mockContext)
        val result = observer.observe().first()

        assertEquals("87%", result)
    }
}

package hanabix.hudble.ble

import android.util.Log
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AndroidLogTest {

    @Test
    fun `log i does not crash under jvm`() {
        assertEquals(0, Log.i("AndroidLogTest", "hello"))
    }
}

package hanabix.hubu.ble

import android.util.Log
import hanabix.hubu.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [32])
class AndroidLogTest {

    @Test
    fun `log i does not crash under jvm`() {
        assertEquals(0, Log.i("AndroidLogTest", "hello"))
    }

    @Test
    fun `build config includes git commit`() {
        assertTrue(BuildConfig.GIT_COMMIT.isNotBlank())
        assertEquals("0.1.0+${BuildConfig.GIT_COMMIT}", BuildConfig.VERSION_NAME)
    }
}

package hanabix.hubu.android

import hanabix.hubu.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidLogTest {

    @Test
    fun `noop logger does not crash on jvm`() {
        NoopLogger.i("AndroidLogTest", "hello")
        NoopLogger.w("AndroidLogTest", "hello")
        NoopLogger.e("AndroidLogTest", "hello")
    }

    @Test
    fun `build config includes git commit`() {
        assertTrue(BuildConfig.GIT_COMMIT.isNotBlank())
        assertEquals("0.1.0+${BuildConfig.GIT_COMMIT}", BuildConfig.VERSION_NAME)
    }
}

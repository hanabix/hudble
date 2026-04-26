package hanabix.hubu.android

import android.util.Log

internal interface Logger {
    fun i(tag: String, msg: String) {}
    fun w(tag: String, msg: String) {}
    fun e(tag: String, msg: String) {}
}

internal object AndroidLogger : Logger {
    override fun i(tag: String, msg: String) {
        Log.i(tag, msg)
    }

    override fun w(tag: String, msg: String) {
        Log.w(tag, msg)
    }

    override fun e(tag: String, msg: String) {
        Log.e(tag, msg)
    }
}

internal object NoopLogger : Logger

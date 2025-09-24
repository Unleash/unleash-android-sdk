package io.getunleash.android.util

import android.util.Log

/**
 * Central logging wrapper for the SDK.
 *
 *  - Single stable base tag so hosts can filter via `adb shell setprop log.tag.io.getunleash LEVEL`.
 *  - Respect both a runtime min level and `Log.isLoggable(baseTag, level)`.
 */
object LoggerWrapper {
    var baseTag: String = "io.getunleash"

    var logLevel: LogLevel = LogLevel.WARN

    fun e(tag: String?, msg: String, tr: Throwable? = null) {
        if (!enabled(Log.ERROR)) return

        Log.e("$baseTag/$tag", msg, tr)
    }

    fun w(tag: String?, msg: String, tr: Throwable? = null) {
        if (!enabled(Log.WARN)) return

        Log.w("$baseTag/$tag", msg, tr)
    }

    fun i(tag: String?, msg: String, tr: Throwable? = null) {
        if (!enabled(Log.INFO)) return

        Log.i("$baseTag/$tag", msg, tr)
    }

    fun d(tag: String?, msg: String, tr: Throwable? = null) {
        if (!enabled(Log.DEBUG)) return

        Log.d("$baseTag/$tag", msg, tr)
    }

    fun v(tag: String?, msg: String, tr: Throwable? = null) {
        if (!enabled(Log.VERBOSE)) return

        Log.v("$baseTag/$tag", msg, tr)
    }

    private fun enabled(priority: Int): Boolean {
        if (logLevel == LogLevel.NONE) return false
        if (priority < logLevel.priority) return false
        // Honor system property-based filtering: `adb shell setprop log.tag.<base> LEVEL`
        return Log.isLoggable(baseTag, priority)
    }
}
enum class LogLevel(val priority: Int) {
    NONE(Int.MAX_VALUE),
    ERROR(Log.ERROR),
    WARN(Log.WARN),
    INFO(Log.INFO),
    DEBUG(Log.DEBUG),
    VERBOSE(Log.VERBOSE)
}
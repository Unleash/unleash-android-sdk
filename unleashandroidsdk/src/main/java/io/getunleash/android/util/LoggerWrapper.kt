package io.getunleash.android.util

import android.util.Log

internal object LoggerWrapper {
    
    var logLevel = LogLevel.NONE
    
    fun e(tag: String?, msg: String, tr: Throwable? = null) {
        if (logLevel < LogLevel.ERROR) return

        Log.e(tag, msg, tr)
    }

    fun w(tag: String?, msg: String, tr: Throwable? = null) {
        if (logLevel < LogLevel.WARN) return

        Log.w(tag, msg, tr)
    }
    
    fun i(tag: String?, msg: String, tr: Throwable? = null) {
        if (logLevel < LogLevel.INFO) return

        Log.i(tag, msg, tr)
    }
    
    fun d(tag: String?, msg: String, tr: Throwable? = null) {
        if (logLevel < LogLevel.DEBUG) return

        Log.d(tag, msg, tr)
    }

    fun v(tag: String?, msg: String, tr: Throwable? = null) {
        if (logLevel < LogLevel.VERBOSE) return

        Log.v(tag, msg, tr)
    }
    
}

enum class LogLevel {
    NONE,
    ERROR,
    WARN,
    INFO,
    DEBUG,
    VERBOSE
}
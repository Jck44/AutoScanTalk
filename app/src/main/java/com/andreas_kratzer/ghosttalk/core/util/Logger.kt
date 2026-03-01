package com.andreas_kratzer.ghosttalk.core.util

import android.util.Log

interface Logger {
    fun d(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}

object AppLogger : Logger {
    override fun d(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        Log.e(tag, message, throwable)
    }
}

/**
 * A No-Op logger for tests or when logging is disabled.
 */
object TestLogger : Logger {
    override fun d(tag: String, message: String) {
        // Do nothing or optionally println(message)
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        // Do nothing or optionally println(message)
    }
}

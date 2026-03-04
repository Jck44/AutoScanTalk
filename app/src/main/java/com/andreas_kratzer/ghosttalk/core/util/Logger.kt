package com.andreas_kratzer.ghosttalk.core.util

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

interface Logger {
    fun d(tag: String, message: String)
    fun w(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}

@Singleton
class AppLogger @Inject constructor() : Logger {
    override fun d(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun w(tag: String, message: String) {
        Log.w(tag, message)
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        Log.e(tag, message, throwable)
    }
}

/**
 * A No-Op logger for tests or when logging is disabled.
 */
class TestLogger : Logger {
    override fun d(tag: String, message: String) {
        // Do nothing or optionally println(message)
    }

    override fun w(tag: String, message: String) {
        // Do nothing or optionally println(message)
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        // Do nothing or optionally println(message)
    }
}

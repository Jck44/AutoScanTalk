package com.andreas_kratzer.ghosttalk.core.util

import android.content.Context
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

interface Logger {
    fun d(tag: String, message: String)
    fun w(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}

@Singleton
class AppLogger @Inject constructor(
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) : Logger {
    private val rollingFileLogger = RollingFileLogger(context)

    override fun d(tag: String, message: String) {
        Log.d(tag, message)
        rollingFileLogger.log("D", tag, message)
    }

    override fun w(tag: String, message: String) {
        Log.w(tag, message)
        rollingFileLogger.log("W", tag, message)
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        Log.e(tag, message, throwable)
        rollingFileLogger.log("E", tag, message, throwable)
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

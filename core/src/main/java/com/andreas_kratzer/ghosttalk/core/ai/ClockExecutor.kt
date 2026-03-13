package com.andreas_kratzer.ghosttalk.core.ai

interface ClockExecutor {
    fun setAlarm(hour: Int, minute: Int, message: String = ""): Boolean
    fun getNextAlarm(): String
}

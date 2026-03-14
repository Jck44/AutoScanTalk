package com.andreas_kratzer.ghosttalk.core.actions

interface ActionLogger {
    fun log(message: String)
    fun error(message: String, throwable: Throwable? = null)
}

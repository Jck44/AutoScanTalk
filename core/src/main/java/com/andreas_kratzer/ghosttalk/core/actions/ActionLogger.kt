package com.andreas_kratzer.ghosttalk.core.actions

import com.andreas_kratzer.ghosttalk.core.model.ButtonAction

interface ActionLogger {
    fun log(message: String, action: ButtonAction? = null, label: String? = null)
    fun error(message: String, throwable: Throwable? = null, action: ButtonAction? = null, label: String? = null)
    var lastSpokenText: String
}

package com.andreas_kratzer.ghosttalk.core.actions

import android.content.Intent

import com.andreas_kratzer.ghosttalk.core.model.ButtonAction

sealed class ActionExecutionEvent {
    data class NavigateToPage(val pageId: String, val action: ButtonAction? = null, val label: String? = null) : ActionExecutionEvent()
    data class Log(val message: String, val action: ButtonAction? = null, val label: String? = null) : ActionExecutionEvent()
    data class Error(val message: String, val throwable: Throwable? = null, val action: ButtonAction? = null, val label: String? = null) : ActionExecutionEvent()
    data class RecoverableAuthError(val intent: Intent) : ActionExecutionEvent()
}

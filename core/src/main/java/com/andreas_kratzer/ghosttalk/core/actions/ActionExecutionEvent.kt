package com.andreas_kratzer.ghosttalk.core.actions

import android.content.Intent

sealed class ActionExecutionEvent {
    data class NavigateToPage(val pageId: String) : ActionExecutionEvent()
    data class Log(val message: String) : ActionExecutionEvent()
    data class Error(val message: String, val throwable: Throwable? = null) : ActionExecutionEvent()
    data class RecoverableAuthError(val intent: Intent) : ActionExecutionEvent()
}

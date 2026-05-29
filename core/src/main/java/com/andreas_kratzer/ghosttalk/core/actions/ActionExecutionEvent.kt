package com.andreas_kratzer.ghosttalk.core.actions

import android.content.Intent

import com.andreas_kratzer.ghosttalk.core.model.ButtonAction

sealed class ActionExecutionEvent {
    data class NavigateToPage(val pageId: String, val action: ButtonAction? = null, val label: String? = null) : ActionExecutionEvent()
    data class Log(val message: String, val action: ButtonAction? = null, val label: String? = null) : ActionExecutionEvent()
    data class Error(val message: String, val throwable: Throwable? = null, val action: ButtonAction? = null, val label: String? = null) : ActionExecutionEvent()
    data class RecoverableAuthError(val intent: Intent) : ActionExecutionEvent()
    data class RequestPermissions(val permissions: Array<String>) : ActionExecutionEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as RequestPermissions
            return permissions.contentEquals(other.permissions)
        }

        override fun hashCode(): Int {
            return permissions.contentHashCode()
        }
    }
}

package com.andreas_kratzer.ghosttalk.core.actions

import com.andreas_kratzer.ghosttalk.core.model.ButtonAction

interface ActionEventEmitter {
    suspend fun emitEvent(event: ActionEvent)
}

sealed class ActionEvent {
    data class NavigateToPage(val pageId: String, val action: ButtonAction? = null, val label: String? = null) : ActionEvent()
    data class NavigateBack(val action: ButtonAction? = null, val label: String? = null) : ActionEvent()
    data class VocalSwitchTriggered(
        val action: ButtonAction? = null,
        val label: String? = null,
        val positiveConfidence: Float = 0f,
        val negativeConfidence: Float = 0f,
        val threshold: Float = 0.82f
    ) : ActionEvent()
    data class RecoverableAuthError(val intent: android.content.Intent) : ActionEvent()
    data class RequestPermissions(val permissions: Array<String>) : ActionEvent() {
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

package com.andreas_kratzer.ghosttalk.core.actions

import com.andreas_kratzer.ghosttalk.core.model.ButtonAction

interface ActionEventEmitter {
    suspend fun emitEvent(event: ActionEvent)
}

sealed class ActionEvent {
    data class NavigateToPage(val pageId: String, val action: ButtonAction? = null, val label: String? = null) : ActionEvent()
    data class RecoverableAuthError(val intent: android.content.Intent) : ActionEvent()
    // Other event types can be added here
}

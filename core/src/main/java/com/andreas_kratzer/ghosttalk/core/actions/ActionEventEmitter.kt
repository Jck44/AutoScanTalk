package com.andreas_kratzer.ghosttalk.core.actions

interface ActionEventEmitter {
    suspend fun emitEvent(event: ActionEvent)
}

sealed class ActionEvent {
    data class NavigateToPage(val pageId: String) : ActionEvent()
    data class RecoverableAuthError(val intent: android.content.Intent) : ActionEvent()
    // Other event types can be added here
}

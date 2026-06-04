package com.andreas_kratzer.ghosttalk.core.actions

import com.andreas_kratzer.ghosttalk.core.di.ApplicationScope
import com.andreas_kratzer.ghosttalk.core.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActionCoordinator @Inject constructor(
    @param:ApplicationScope private val scope: CoroutineScope,
    private val logger: Logger
) : ActionLogger, ActionEventEmitter {

    private val _events = MutableSharedFlow<ActionExecutionEvent>()
    val events: SharedFlow<ActionExecutionEvent> = _events.asSharedFlow()

    override fun log(message: String, action: com.andreas_kratzer.ghosttalk.core.model.ButtonAction?, label: String?) {
        logger.d("ActionExecutor", "Log: $message")
        scope.launch { _events.emit(ActionExecutionEvent.Log(message, action, label)) }
    }

    override fun error(message: String, throwable: Throwable?, action: com.andreas_kratzer.ghosttalk.core.model.ButtonAction?, label: String?) {
        logger.e("ActionExecutor", message, throwable)
        scope.launch { _events.emit(ActionExecutionEvent.Error(message, throwable, action, label)) }
    }

    override suspend fun emitEvent(event: ActionEvent) {
        val executionEvent = when (event) {
            is ActionEvent.NavigateToPage -> ActionExecutionEvent.NavigateToPage(event.pageId, event.action, event.label)
            is ActionEvent.NavigateBack -> ActionExecutionEvent.NavigateBack(event.action, event.label)
            is ActionEvent.VocalSwitchTriggered -> ActionExecutionEvent.VocalSwitchTriggered(event.action, event.label)
            is ActionEvent.RecoverableAuthError -> ActionExecutionEvent.RecoverableAuthError(event.intent)
            is ActionEvent.RequestPermissions -> ActionExecutionEvent.RequestPermissions(event.permissions)
        }
        _events.emit(executionEvent)
    }
}

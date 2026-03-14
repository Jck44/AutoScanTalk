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

    override fun log(message: String) {
        logger.d("ActionExecutor", "Log: $message")
        scope.launch { _events.emit(ActionExecutionEvent.Log(message)) }
    }

    override fun error(message: String, throwable: Throwable?) {
        logger.e("ActionExecutor", message, throwable)
        scope.launch { _events.emit(ActionExecutionEvent.Log("Error: $message")) }
    }

    override suspend fun emitEvent(event: ActionEvent) {
        val executionEvent = when (event) {
            is ActionEvent.NavigateToPage -> ActionExecutionEvent.NavigateToPage(event.pageId)
            is ActionEvent.RecoverableAuthError -> ActionExecutionEvent.RecoverableAuthError(event.intent)
        }
        _events.emit(executionEvent)
    }
}

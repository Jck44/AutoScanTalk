package com.andreas_kratzer.ghosttalk.core.actions

import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.domain.GeminiUseCase
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.FrequentActionButtonAction
import com.andreas_kratzer.ghosttalk.model.GeminiButtonAction
import com.andreas_kratzer.ghosttalk.model.GeminiSearchButtonAction
import com.andreas_kratzer.ghosttalk.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.model.SmartPredictionButtonAction
import com.andreas_kratzer.ghosttalk.model.ChangeVolumeButtonAction
import com.andreas_kratzer.ghosttalk.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.actions.*
import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ActionExecutor(
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val logger: Logger,
    var geminiUseCase: GeminiUseCase?,
    var ttsHelper: TextToSpeechHelper?,
    private val buttonUsageRepository: ButtonUsageRepository? = null,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {
    sealed class ExecutionEvent {
        data class NavigateToPage(val pageId: String) : ExecutionEvent()
        data class Log(val message: String) : ExecutionEvent()
        data class Error(val message: String) : ExecutionEvent()
        data class RecoverableAuthError(val intent: android.content.Intent) : ExecutionEvent()
    }

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    private val _events = MutableSharedFlow<ExecutionEvent>()
    val events: SharedFlow<ExecutionEvent> = _events.asSharedFlow()

    private var lastExecutionTime = -1L
    private var activeExecutionId = 0

    private val handlers: List<ActionHandler<out ButtonAction>> by lazy {
        listOf(
            SpeechActionHandler(settingsRepository, ttsHelper, ::log),
            NavigationActionHandler(scope, settingsRepository, ttsHelper, ::emitEvent, ::log),
            VolumeActionHandler(settingsRepository, ttsHelper, ::log),
            GeminiActionHandler(scope, settingsRepository, geminiUseCase, ttsHelper, ::emitEvent, ::log),
            NotificationActionHandler(settingsRepository, ttsHelper, ::log),
            FrequentActionHandler(::log),
            SmartPredictionActionHandler(::log)
        )
    }

    // Only for unit tests
    fun setExecutingStateForTest(isExecuting: Boolean) {
        _isExecuting.value = isExecuting
    }

    fun executeButtonAction(buttonConfig: ButtonConfig, bookId: String? = null) {
        val currentTime = timeProvider()
        val holdingTime = settingsRepository.holdingTimeMillis
        
        if (lastExecutionTime != -1L && currentTime - lastExecutionTime < holdingTime) {
            log("Aktion ignoriert (Haltezeit aktiv: ${holdingTime}ms)")
            return
        }
        
        if (_isExecuting.value) {
            log("Aktion ignoriert (Sprachausgabe aktiv)")
            return
        }
        
        lastExecutionTime = currentTime
        val currentExecutionId = ++activeExecutionId
        _isExecuting.value = true

        // Record button usage for statistics
        if (bookId != null && buttonUsageRepository != null) {
            scope.launch {
                try {
                    buttonUsageRepository.recordUsage(bookId, buttonConfig)
                } catch (_: Exception) { /* Non-critical, don't block action */ }
            }
        }

        val action = buttonConfig.buttonAction
        val handler = handlers.find { it.canHandle(action) }
        
        if (handler != null) {
            @Suppress("UNCHECKED_CAST")
            (handler as ActionHandler<ButtonAction>).handle(
                buttonConfig,
                action,
                currentExecutionId,
                ::finishExecution
            )
        } else {
            log("Kein Handler für Aktionstyp gefunden: ${action::class.simpleName}")
            logger.d("ActionExecutor", "No handler for ${action::class.simpleName}")
            finishExecution(currentExecutionId)
        }
    }

    private fun finishExecution(executionId: Int) {
        if (executionId == activeExecutionId) {
            _isExecuting.value = false
        }
    }

    private fun log(message: String) {
        scope.launch { _events.emit(ExecutionEvent.Log(message)) }
    }

    private suspend fun emitEvent(event: ExecutionEvent) {
        _events.emit(event)
    }
}


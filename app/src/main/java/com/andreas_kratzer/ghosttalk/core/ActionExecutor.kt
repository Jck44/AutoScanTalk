package com.andreas_kratzer.ghosttalk.core

import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.domain.GeminiUseCase
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.FrequentActionButtonAction
import com.andreas_kratzer.ghosttalk.model.GeminiButtonAction
import com.andreas_kratzer.ghosttalk.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.model.SmartPredictionButtonAction
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

        when (val action = buttonConfig.buttonAction) {
            is SpeakTextButtonAction -> {
                val textToSpeak = buttonConfig.spokenText?.takeIf { it.isNotBlank() } 
                    ?: action.textToSpeech.takeIf { it.isNotBlank() }
                    ?: buttonConfig.label
                if (ttsHelper?.isReady == true) {
                    ttsHelper?.speakRouted(textToSpeak, settingsRepository.ttsAudioDeviceAddress) {
                        finishExecution(currentExecutionId)
                    }
                    log("Gesprochen: \"$textToSpeak\"")
                } else {
                    log("Sprechen (TTS nicht bereit): \"$textToSpeak\"")
                    finishExecution(currentExecutionId)
                }
            }
            is FrequentActionButtonAction -> {
                // Sollte vor der Ausführung durch FrequentActionResolver aufgelöst werden
                log("Häufigste Aktion (unaufgelöst) ignoriert")
                finishExecution(currentExecutionId)
            }
            is NavigateToPageButtonAction -> {
                val feedback = buttonConfig.spokenText?.takeIf { it.isNotBlank() }
                
                // Closure to execute the actual navigation
                val performNavigation = {
                    scope.launch {
                        emitEvent(ExecutionEvent.NavigateToPage(action.pageId))
                        // Reset isExecuting immediately for navigation as the ViewModel/Screen 
                        // handles the new state.
                        finishExecution(currentExecutionId)
                    }
                }

                if (feedback != null && ttsHelper?.isReady == true) {
                    ttsHelper?.speakRouted(feedback, settingsRepository.cuesAudioDeviceAddress) {
                        performNavigation()
                    }
                    log("Navigations-Feedback: \"$feedback\"")
                } else {
                    if (feedback != null) log("Nav-Feedback (TTS nicht bereit): \"$feedback\"")
                    performNavigation()
                }
            }
            is GeminiButtonAction -> {
                log("Gemini aufgerufen mit: \"${action.prompt}\"")
                scope.launch {
                    try {
                        if (!settingsRepository.isGeminiEnabled) {
                            val errorMsg = ttsHelper?.context?.getString(R.string.error_gemini_disabled) 
                                ?: "Gemini in Einstellungen prüfen"
                            ttsHelper?.speakRouted(errorMsg, settingsRepository.ttsAudioDeviceAddress) {
                                finishExecution(currentExecutionId)
                            }
                            return@launch
                        }

                        val response = geminiUseCase?.generateResponse(action.prompt) 
                            ?: "Fehler: Gemini Integration nicht verfügbar."
                        
                        if (ttsHelper?.isReady == true) {
                            ttsHelper?.speakRouted(response, settingsRepository.ttsAudioDeviceAddress) {
                                finishExecution(currentExecutionId)
                            }
                        } else {
                            log("Gemini Ergebnis: \"$response\"")
                            finishExecution(currentExecutionId)
                        }
                    } catch (e: UserRecoverableAuthIOException) {
                        log("Gemini: Berechtigung erforderlich.")
                        e.intent?.let { emitEvent(ExecutionEvent.RecoverableAuthError(it)) }
                        finishExecution(currentExecutionId)
                    } catch (e: UserRecoverableAuthException) {
                        log("Gemini: Berechtigung erforderlich.")
                        e.intent?.let { emitEvent(ExecutionEvent.RecoverableAuthError(it)) }
                        finishExecution(currentExecutionId)
                    } catch (e: Exception) {
                        log("Gemini Fehler: ${e.message}")
                        finishExecution(currentExecutionId)
                    }
                }
            }
            is SmartPredictionButtonAction -> {
                log("Smart Prediction button clicked (Rank: ${action.rank}). Actual execution handled in ViewModel.")
                finishExecution(currentExecutionId)
            }
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


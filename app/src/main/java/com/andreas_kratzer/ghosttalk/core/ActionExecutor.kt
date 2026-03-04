package com.andreas_kratzer.ghosttalk.core

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
import com.andreas_kratzer.ghosttalk.model.TtsModeButtonAction
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

        when (val action = buttonConfig.buttonAction) {
            is SpeakTextButtonAction -> {
                val textToSpeak = buttonConfig.spokenText?.takeIf { it.isNotBlank() } 
                    ?: action.textToSpeech.takeIf { it.isNotBlank() }
                    ?: buttonConfig.label
                val targetDeviceAddress = if (buttonConfig.playActionAsAuditoryCue) {
                    settingsRepository.cuesAudioDeviceAddress
                } else {
                    settingsRepository.ttsAudioDeviceAddress
                }
                val tts = ttsHelper
                if (tts?.isReady == true) {
                    tts.speakRouted(textToSpeak, targetDeviceAddress) {
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
                val targetDeviceAddress = if (buttonConfig.playActionAsAuditoryCue) {
                    settingsRepository.cuesAudioDeviceAddress
                } else {
                    settingsRepository.ttsAudioDeviceAddress
                }
                scope.launch {
                    val tts = ttsHelper
                    try {
                        if (!settingsRepository.isGeminiEnabled) {
                            val errorMsg = tts?.context?.getString(R.string.error_gemini_disabled) 
                                ?: "Gemini in Einstellungen prüfen"
                            if (tts != null) {
                                tts.speakRouted(errorMsg, targetDeviceAddress) {
                                    finishExecution(currentExecutionId)
                                }
                            } else { finishExecution(currentExecutionId) }
                            return@launch
                        }

                        val response = geminiUseCase?.generateResponse(action.prompt) 
                            ?: "Fehler: Gemini Integration nicht verfügbar."
                        
                        if (tts?.isReady == true) {
                            tts.speakRouted(response, targetDeviceAddress) {
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
                        val message = e.message ?: ""
                        if (message.contains("429")) {
                            // Extract remaining seconds if present (e.g. from GeminiUseCase lockout exception)
                            val remainingMatch = Regex("wait (\\d+) seconds", RegexOption.IGNORE_CASE).find(message)
                            val seconds = remainingMatch?.groupValues?.get(1)?.toIntOrNull() ?: 60

                            val quotaMsg = tts?.context?.getString(
                                R.string.error_gemini_quota_reached, seconds
                            ) ?: "Gemini-Limit erreicht. Bitte $seconds Sekunden warten."
                            
                            log(quotaMsg)
                            if (tts?.isReady == true) {
                                tts.speakRouted(quotaMsg, settingsRepository.ttsAudioDeviceAddress) {
                                    finishExecution(currentExecutionId)
                                }
                            } else {
                                finishExecution(currentExecutionId)
                            }
                        } else {
                            log("Gemini Fehler: $message")
                            finishExecution(currentExecutionId)
                        }
                    }
                }
            }
            is GeminiSearchButtonAction -> {
                log("Gemini Suche aufgerufen mit: \"${action.prompt}\"")
                val targetDeviceAddress = if (buttonConfig.playActionAsAuditoryCue) {
                    settingsRepository.cuesAudioDeviceAddress
                } else {
                    settingsRepository.ttsAudioDeviceAddress
                }
                scope.launch {
                    val tts = ttsHelper
                    try {
                        if (!settingsRepository.isGeminiEnabled) {
                            val errorMsg = tts?.context?.getString(R.string.error_gemini_disabled) 
                                ?: "Gemini in Einstellungen prüfen"
                            if (tts != null) {
                                tts.speakRouted(errorMsg, targetDeviceAddress) {
                                    finishExecution(currentExecutionId)
                                }
                            } else { finishExecution(currentExecutionId) }
                            return@launch
                        }

                        val response = geminiUseCase?.generateResponse(action.prompt, useGoogleSearch = true) 
                            ?: "Fehler: Gemini Integration nicht verfügbar."
                        
                        if (tts?.isReady == true) {
                            tts.speakRouted(response, targetDeviceAddress) {
                                finishExecution(currentExecutionId)
                            }
                        } else {
                            log("Gemini Suche Ergebnis: \"$response\"")
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
                        val message = e.message ?: ""
                        if (message.contains("429")) {
                            val remainingMatch = Regex("wait (\\d+) seconds", RegexOption.IGNORE_CASE).find(message)
                            val seconds = remainingMatch?.groupValues?.get(1)?.toIntOrNull() ?: 60

                            val quotaMsg = tts?.context?.getString(
                                R.string.error_gemini_quota_reached, seconds
                            ) ?: "Gemini-Limit erreicht. Bitte $seconds Sekunden warten."
                            
                            log(quotaMsg)
                            if (tts?.isReady == true) {
                                tts.speakRouted(quotaMsg, settingsRepository.ttsAudioDeviceAddress) {
                                    finishExecution(currentExecutionId)
                                }
                            } else {
                                finishExecution(currentExecutionId)
                            }
                        } else {
                            log("Gemini Fehler: $message")
                            finishExecution(currentExecutionId)
                        }
                    }
                }
            }
            is SmartPredictionButtonAction -> {
                log("Smart Prediction button clicked (Rank: ${action.rank}). Actual execution handled in ViewModel.")
                finishExecution(currentExecutionId)
            }
            is com.andreas_kratzer.ghosttalk.model.NotificationButtonAction -> {
                val service = com.andreas_kratzer.ghosttalk.core.services.NotificationReaderService.instance
                val targetDeviceAddress = if (buttonConfig.playActionAsAuditoryCue) {
                    settingsRepository.cuesAudioDeviceAddress
                } else {
                    settingsRepository.ttsAudioDeviceAddress
                }

                val tts = ttsHelper
                if (service == null || !settingsRepository.isNotificationReadingEnabled) {
                    val msg = "Vorlesen von Benachrichtigungen nicht aktiv oder Berechtigung fehlt."
                    log(msg)
                    if (tts?.isReady == true) {
                        tts.speakRouted(msg, targetDeviceAddress) {
                            finishExecution(currentExecutionId)
                        }
                    } else finishExecution(currentExecutionId)
                    return
                }

                val activeNotifs = try {
                    service.activeNotifications
                } catch (e: Exception) {
                    log("Fehler beim Abrufen der Benachrichtigungen: ${e.message}")
                    null
                }
                
                if (activeNotifs == null || activeNotifs.isEmpty()) {
                    val msg = "Keine Benachrichtigungen vorhanden."
                    log(msg)
                    if (tts?.isReady == true) {
                        tts.speakRouted(msg, targetDeviceAddress) {
                            finishExecution(currentExecutionId)
                        }
                    } else finishExecution(currentExecutionId)
                    return
                }

                val allowedApps = settingsRepository.monitoredNotificationApps
                val filtered = activeNotifs.filter { sbn ->
                    val pkg = sbn.packageName
                    val isAllowed = allowedApps.contains(pkg)
                    val matchesTarget = action.targetApp == "ALL" || pkg == action.targetApp
                    isAllowed && matchesTarget
                }

                if (filtered.isEmpty()) {
                    val msg = "Keine passenden Benachrichtigungen gefunden."
                    log(msg)
                    if (tts?.isReady == true) {
                        tts.speakRouted(msg, targetDeviceAddress) {
                            finishExecution(currentExecutionId)
                        }
                    } else finishExecution(currentExecutionId)
                    return
                }

                val messagesToRead = filtered.mapNotNull { sbn ->
                    val extras = sbn.notification.extras
                    val title = extras.getString(android.app.Notification.EXTRA_TITLE) ?: "Unbekannt"
                    val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
                    if (text.isNullOrBlank()) null else "Von $title: $text"
                }

                if (messagesToRead.isEmpty()) {
                    val msg = "Benachrichtigungen enthalten keinen Text."
                    log(msg)
                    if (tts?.isReady == true) {
                        tts.speakRouted(msg, targetDeviceAddress) {
                            finishExecution(currentExecutionId)
                        }
                    } else finishExecution(currentExecutionId)
                    return
                }

                val combinedMessage = messagesToRead.joinToString(". ")
                log("Lese Benachrichtigungen: $combinedMessage")
                
                tts?.isReadingNotification = true
                if (tts?.isReady == true) {
                    tts.speakRouted(combinedMessage, targetDeviceAddress) {
                        tts.isReadingNotification = false
                        finishExecution(currentExecutionId)
                    }
                } else {
                    tts?.isReadingNotification = false
                    finishExecution(currentExecutionId)
                }
            }
            is ChangeVolumeButtonAction -> {
                val currentVolume = if (action.isForCues) {
                    settingsRepository.cuesVolumeMultiplier
                } else {
                    settingsRepository.ttsVolumeMultiplier
                }
                
                val newVolume = if (action.isAbsolute) {
                    action.amount
                } else {
                    (currentVolume + action.amount).coerceIn(0.0f, 3.0f)
                }
                
                if (action.isForCues) {
                    settingsRepository.cuesVolumeMultiplier = newVolume
                    log("Hinweis-Lautstärke auf ${(newVolume * 100).toInt()}% gesetzt")
                } else {
                    settingsRepository.ttsVolumeMultiplier = newVolume
                    log("TTS-Lautstärke auf ${(newVolume * 100).toInt()}% gesetzt")
                }
                
                val targetDeviceAddress = if (buttonConfig.playActionAsAuditoryCue) {
                    settingsRepository.cuesAudioDeviceAddress
                } else {
                    settingsRepository.ttsAudioDeviceAddress
                }
                
                ttsHelper?.speakRouted("Lautstärke ${(newVolume * 100).toInt()}%", targetDeviceAddress) {
                    finishExecution(currentExecutionId)
                } ?: finishExecution(currentExecutionId)
            }
            is TtsModeButtonAction -> {
                settingsRepository.ttsMode = action.mode
                val displayMode = when (action.mode) {
                    "WHISPER" -> "Flüstern"
                    "SHOUT" -> "Schreien"
                    else -> "Normal"
                }
                log("Sprachmodus auf $displayMode gesetzt")
                
                val targetDeviceAddress = if (buttonConfig.playActionAsAuditoryCue) {
                    settingsRepository.cuesAudioDeviceAddress
                } else {
                    settingsRepository.ttsAudioDeviceAddress
                }
                
                ttsHelper?.speakRouted("Modus $displayMode", targetDeviceAddress) {
                    finishExecution(currentExecutionId)
                } ?: finishExecution(currentExecutionId)
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


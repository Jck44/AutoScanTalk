package com.andreas_kratzer.ghosttalk.core

import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

import com.andreas_kratzer.ghosttalk.model.GeminiButtonAction
import com.andreas_kratzer.ghosttalk.domain.GeminiUseCase
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException

class ActionExecutor(
    private val scope: CoroutineScope,
    private val pageRepository: PageRepository,
    private val settingsRepository: SettingsRepository,
    private val geminiUseCase: GeminiUseCase?,
    var ttsHelper: TextToSpeechHelper?,
    private val onLoadPage: (Page) -> Unit,
    private val onPauseScanning: () -> Unit,
    private val onResumeScanning: () -> Unit,
    private val onLogAction: (String) -> Unit,
    private val onRecoverableAuthError: (android.content.Intent) -> Unit = {},
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {
    private var lastExecutionTime = -1L
    private var activeExecutionId = 0
    private var isSpeaking = false

    fun executeButtonAction(buttonConfig: ButtonConfig) {
        val currentTime = timeProvider()
        val holdingTime = settingsRepository.holdingTimeMillis
        
        if (lastExecutionTime != -1L && currentTime - lastExecutionTime < holdingTime) {
            onLogAction("Aktion ignoriert (Haltezeit aktiv: ${holdingTime}ms)")
            return
        }
        
        lastExecutionTime = currentTime
        val currentExecutionId = ++activeExecutionId
        isSpeaking = true

        onPauseScanning()
        
        when (val action = buttonConfig.buttonAction) {
            is SpeakTextButtonAction -> {
                val textToSpeak = buttonConfig.spokenText?.takeIf { it.isNotBlank() } 
                    ?: action.textToSpeech.takeIf { it.isNotBlank() }
                    ?: buttonConfig.label
                if (ttsHelper?.isReady == true) {
                    ttsHelper?.speakRouted(textToSpeak, settingsRepository.ttsAudioDeviceAddress) {
                        if (currentExecutionId == activeExecutionId) {
                            isSpeaking = false
                            onResumeScanning()
                        }
                    }
                    onLogAction("Gesprochen: \"$textToSpeak\"")
                } else {
                    onLogAction("Sprechen (TTS nicht bereit): \"$textToSpeak\"")
                    if (currentExecutionId == activeExecutionId) {
                        isSpeaking = false
                        onResumeScanning()
                    }
                }
            }
            is NavigateToPageButtonAction -> {
                val feedback = buttonConfig.spokenText?.takeIf { it.isNotBlank() }
                
                // Closure to execute the actual navigation
                val performNavigation = {
                    scope.launch {
                        val nextPage = pageRepository.getPageById(action.pageId)
                        if (nextPage != null) {
                            onLoadPage(nextPage)
                            // isSpeaking and onResumeScanning handling for navigation:
                            // Since Navigation takes time and might have its own TTS feedback,
                            // we reset isSpeaking only after the navigation is "done" from ActionExecutor perspective.
                            // Note: PageScreen handles scanner resume via DisposableEffect.
                            isSpeaking = false 
                            onLogAction("Navigiert zu Seite: ${nextPage.name} (ID: ${action.pageId})")
                        } else {
                            onLogAction("Fehler: Seite mit ID '${action.pageId}' nicht gefunden.")
                            if (ttsHelper?.isReady == true) {
                                ttsHelper?.speak("Seite nicht gefunden") { 
                                    if (currentExecutionId == activeExecutionId) {
                                        isSpeaking = false
                                        onResumeScanning()
                                    }
                                }
                            } else {
                                if (currentExecutionId == activeExecutionId) {
                                    isSpeaking = false
                                    onResumeScanning()
                                }
                            }
                        }
                    }
                }

                if (feedback != null && ttsHelper?.isReady == true) {
                    ttsHelper?.speakRouted(feedback, settingsRepository.cuesAudioDeviceAddress) {
                        performNavigation()
                    }
                    onLogAction("Navigations-Feedback: \"$feedback\"")
                } else {
                    if (feedback != null) onLogAction("Nav-Feedback (TTS nicht bereit): \"$feedback\"")
                    performNavigation()
                }
            }
            is GeminiButtonAction -> {
                onLogAction("Gemini aufgerufen mit: \"${action.prompt}\"")
                scope.launch {
                    try {
                        val response = geminiUseCase?.generateResponse(action.prompt) 
                            ?: "Fehler: Gemini Integration nicht verfügbar."
                        
                        if (ttsHelper?.isReady == true) {
                            ttsHelper?.speakRouted(response, settingsRepository.ttsAudioDeviceAddress) {
                                if (currentExecutionId == activeExecutionId) {
                                    isSpeaking = false
                                    onResumeScanning()
                                }
                            }
                        } else {
                            onLogAction("Gemini Ergebnis: \"$response\"")
                            if (currentExecutionId == activeExecutionId) {
                                isSpeaking = false
                                onResumeScanning()
                            }
                        }
                    } catch (e: UserRecoverableAuthIOException) {
                        onLogAction("Gemini: Berechtigung erforderlich.")
                        e.intent?.let { onRecoverableAuthError(it) }
                        isSpeaking = false
                        onResumeScanning()
                    } catch (e: UserRecoverableAuthException) {
                        onLogAction("Gemini: Berechtigung erforderlich.")
                        e.intent?.let { onRecoverableAuthError(it) }
                        isSpeaking = false
                        onResumeScanning()
                    } catch (e: Exception) {
                        onLogAction("Gemini Fehler: ${e.message}")
                        isSpeaking = false
                        onResumeScanning()
                    }
                }
            }
        }
    }
}

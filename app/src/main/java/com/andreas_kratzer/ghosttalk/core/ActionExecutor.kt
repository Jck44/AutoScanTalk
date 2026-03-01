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

class ActionExecutor(
    private val scope: CoroutineScope,
    private val pageRepository: PageRepository,
    private val settingsRepository: SettingsRepository,
    var ttsHelper: TextToSpeechHelper?,
    private val onLoadPage: (Page) -> Unit,
    private val onPauseScanning: () -> Unit,
    private val onResumeScanning: () -> Unit,
    private val onLogAction: (String) -> Unit
) {
    private var lastExecutionTime = 0L

    fun executeButtonAction(buttonConfig: ButtonConfig) {
        val currentTime = System.currentTimeMillis()
        val holdingTime = settingsRepository.holdingTimeMillis
        if (currentTime - lastExecutionTime < holdingTime) {
            onLogAction("Aktion ignoriert (Haltezeit aktiv: ${holdingTime}ms)")
            return
        }
        lastExecutionTime = currentTime

        onPauseScanning()
        
        when (val action = buttonConfig.buttonAction) {
            is SpeakTextButtonAction -> {
                val textToSpeak = buttonConfig.spokenText?.takeIf { it.isNotBlank() } 
                    ?: action.textToSpeech.takeIf { it.isNotBlank() }
                    ?: buttonConfig.label
                if (ttsHelper?.isReady == true) {
                    ttsHelper?.speakRouted(textToSpeak, settingsRepository.ttsAudioDeviceAddress) {
                        onResumeScanning()
                    }
                    onLogAction("Gesprochen: \"$textToSpeak\"")
                } else {
                    onLogAction("Sprechen (TTS nicht bereit): \"$textToSpeak\"")
                    onResumeScanning()
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
                            // onResumeScanning() is NOT called here because PageScreen's
                            // DisposableEffect automatically resumes scanning when currentPage changes!
                            onLogAction("Navigiert zu Seite: ${nextPage.name} (ID: ${action.pageId})")
                        } else {
                            onLogAction("Fehler: Seite mit ID '${action.pageId}' nicht gefunden.")
                            if (ttsHelper?.isReady == true) {
                                ttsHelper?.speak("Seite nicht gefunden") { onResumeScanning() }
                            } else {
                                onResumeScanning()
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
        }
    }
}

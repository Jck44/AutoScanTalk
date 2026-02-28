package com.example.gostalk.core

import com.example.gostalk.data.PageRepository
import com.example.gostalk.model.ButtonConfig
import com.example.gostalk.model.NavigateToPageButtonAction
import com.example.gostalk.model.Page
import com.example.gostalk.model.SpeakTextButtonAction
import com.example.gostalk.tts.TextToSpeechHelper
import com.example.gostalk.data.SettingsRepository
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

    fun executeButtonAction(buttonConfig: ButtonConfig) {
        onPauseScanning()
        
        when (val action = buttonConfig.buttonAction) {
            is SpeakTextButtonAction -> {
                val textToSpeak = buttonConfig.spokenText?.takeIf { it.isNotBlank() } ?: action.textToSpeech
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
                val feedback = buttonConfig.spokenText?.takeIf { it.isNotBlank() } ?: action.ttsFeedback
                
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

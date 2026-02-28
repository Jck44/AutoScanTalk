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
    private val onResumeScanning: () -> Unit,
    private val onLogAction: (String) -> Unit
) {

    fun executeButtonAction(buttonConfig: ButtonConfig) {
        when (val action = buttonConfig.buttonAction) {
            is SpeakTextButtonAction -> {
                val textToSpeak = buttonConfig.spokenText?.takeIf { it.isNotBlank() } ?: action.textToSpeech
                if (ttsHelper?.isReady == true) {
                    ttsHelper?.speakRouted(textToSpeak, settingsRepository.ttsAudioDeviceAddress)
                    onLogAction("Gesprochen: \"$textToSpeak\"")
                } else {
                    onLogAction("Sprechen (TTS nicht bereit): \"$textToSpeak\"")
                }
            }
            is NavigateToPageButtonAction -> {
                val feedback = buttonConfig.spokenText?.takeIf { it.isNotBlank() } ?: action.ttsFeedback
                feedback?.let { fb ->
                    if (ttsHelper?.isReady == true) {
                        ttsHelper?.speakRouted(fb, settingsRepository.cuesAudioDeviceAddress)
                        onLogAction("Navigations-Feedback: \"$fb\"")
                    } else {
                        onLogAction("Nav-Feedback (TTS nicht bereit): \"$fb\"")
                    }
                }
                scope.launch {
                    val nextPage = pageRepository.getPageById(action.pageId)
                    if (nextPage != null) {
                        onLoadPage(nextPage)
                        onResumeScanning()
                        onLogAction("Navigiert zu Seite: ${nextPage.name} (ID: ${action.pageId})")
                    } else {
                        onLogAction("Fehler: Seite mit ID '${action.pageId}' nicht gefunden.")
                        if (ttsHelper?.isReady == true) ttsHelper?.speak("Seite nicht gefunden")
                    }
                }
            }
        }
    }
}

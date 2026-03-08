package com.andreas_kratzer.ghosttalk.core.actions

import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.model.ButtonAction
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper

class SpeechActionHandler(
    private val settingsRepository: SettingsRepository,
    private val ttsHelper: TextToSpeechHelper?,
    private val log: (String) -> Unit
) : ActionHandler {

    override fun canHandle(action: ButtonAction): Boolean = action is SpeakTextButtonAction

    override fun handle(
        buttonConfig: ButtonConfig,
        action: ButtonAction,
        executionId: Int,
        onFinish: (Int) -> Unit
    ) {
        val speakAction = action as SpeakTextButtonAction
        val textToSpeak = buttonConfig.spokenText?.takeIf { it.isNotBlank() }
            ?: buttonConfig.label
            
        val targetDeviceAddress = if (buttonConfig.playActionAsAuditoryCue) {
            settingsRepository.cuesAudioDeviceAddress
        } else {
            settingsRepository.ttsAudioDeviceAddress
        }
        
        val tts = ttsHelper
        if (tts?.isReady == true) {
            tts.speakRouted(
                text = textToSpeak,
                deviceAddress = targetDeviceAddress,
                ttsMode = action.ttsMode,
                queueMode = android.speech.tts.TextToSpeech.QUEUE_FLUSH,
                isForCues = buttonConfig.playActionAsAuditoryCue,
                onDone = { onFinish(executionId) }
            )
            log("Gesprochen: \"$textToSpeak\"")
        } else {
            log("Sprechen (TTS nicht bereit): \"$textToSpeak\"")
            onFinish(executionId)
        }
    }
}

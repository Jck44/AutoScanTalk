package com.andreas_kratzer.ghosttalk.core.actions

import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction

class SpeechActionHandler(
    private val settings: SpeechSettings,
    private val ttsProxyLazy: dagger.Lazy<ActionTtsProxy>,
    private val log: (String) -> Unit
) : ActionHandler {

    override fun canHandle(action: ButtonAction): Boolean = action is SpeakTextButtonAction

    override fun handle(
        buttonConfig: ButtonConfig,
        action: ButtonAction,
        executionId: Int,
        onFinish: (Int) -> Unit
    ) {
        val textToSpeak = buttonConfig.spokenText?.takeIf { it.isNotBlank() }
            ?: buttonConfig.label
            
        val targetDeviceAddress = if (buttonConfig.playActionAsAuditoryCue) {
            settings.cuesAudioDeviceAddress
        } else {
            settings.ttsAudioDeviceAddress
        }
        
        val tts = ttsProxyLazy.get()
        if (tts.isReady) {
            tts.speakRouted(
                text = textToSpeak,
                deviceAddress = targetDeviceAddress,
                queueMode = 0, // QUEUE_FLUSH constant usually 0
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

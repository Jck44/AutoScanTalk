package com.andreas_kratzer.ghosttalk.core.actions

import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction

import javax.inject.Inject

class SpeechActionHandler @Inject constructor(
    private val settings: SpeechSettings,
    private val ttsProxyLazy: dagger.Lazy<ActionTtsProxy>,
    private val actionLogger: ActionLogger
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
                queueMode = 0,
                isForCues = buttonConfig.playActionAsAuditoryCue,
                onDone = { onFinish(executionId) }
            )
            actionLogger.log("Gesprochen: \"$textToSpeak\"")
        } else {
            actionLogger.log("Sprechen (TTS nicht bereit): \"$textToSpeak\"")
            onFinish(executionId)
        }
    }
}

package com.andreas_kratzer.ghosttalk.core.actions

import android.content.Context
import com.andreas_kratzer.ghosttalk.core.audio.RoutedAudioPlayer
import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SpokenTextMode
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class SpeechActionHandler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: SpeechSettings,
    private val ttsProxyLazy: dagger.Lazy<ActionTtsProxy>,
    private val audioPlayerLazy: dagger.Lazy<RoutedAudioPlayer>,
    private val actionLogger: ActionLogger
) : ActionHandler {

    override fun canHandle(action: ButtonAction): Boolean = action is SpeakTextButtonAction

    override fun handle(
        buttonConfig: ButtonConfig,
        action: ButtonAction,
        executionId: Int,
        onFinish: (Int) -> Unit
    ) {
        val targetDeviceAddress = if (buttonConfig.playActionAsAuditoryCue) {
            settings.cuesAudioDeviceAddress
        } else {
            settings.ttsAudioDeviceAddress
        }
        
        // 1. Play recorded custom audio if mode is AUDIO and file exists
        if (buttonConfig.spokenTextMode == SpokenTextMode.AUDIO && !buttonConfig.audioFileName.isNullOrBlank()) {
            val audioFile = File(context.filesDir.resolve("audio_recordings"), buttonConfig.audioFileName)
            if (audioFile.exists()) {
                audioPlayerLazy.get().playAudioFile(
                    file = audioFile,
                    deviceAddress = targetDeviceAddress,
                    onCompletion = { onFinish(executionId) }
                )
                actionLogger.log("Sprachaufnahme abgespielt: \"${buttonConfig.audioFileName}\"", action, buttonConfig.label)
                return
            }
        }

        // 2. Fallback to TTS (using spokenText or label)
        val textToSpeak = buttonConfig.spokenText?.takeIf { it.isNotBlank() }
            ?: buttonConfig.label
            
        actionLogger.lastSpokenText = textToSpeak

        val tts = ttsProxyLazy.get()
        if (tts.isReady) {
            tts.speakRouted(
                text = textToSpeak,
                deviceAddress = targetDeviceAddress,
                queueMode = 0,
                isForCues = buttonConfig.playActionAsAuditoryCue,
                onDone = { onFinish(executionId) }
            )
            actionLogger.log("Gesprochen: \"$textToSpeak\"", action, buttonConfig.label)
        } else {
            actionLogger.log("Sprechen (TTS nicht bereit): \"$textToSpeak\"", action, buttonConfig.label)
            onFinish(executionId)
        }
    }
}

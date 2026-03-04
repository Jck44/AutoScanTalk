package com.andreas_kratzer.ghosttalk.core.actions

import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.model.ButtonAction
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.ChangeVolumeButtonAction
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper

class VolumeActionHandler(
    private val settingsRepository: SettingsRepository,
    private val ttsHelper: TextToSpeechHelper?,
    private val log: (String) -> Unit
) : ActionHandler<ChangeVolumeButtonAction> {

    override fun canHandle(action: ButtonAction): Boolean = action is ChangeVolumeButtonAction

    override fun handle(
        buttonConfig: ButtonConfig,
        action: ChangeVolumeButtonAction,
        executionId: Int,
        onFinish: (Int) -> Unit
    ) {
        val currentVolume = if (action.isForCues) {
            settingsRepository.cuesVolumeMultiplier
        } else {
            settingsRepository.ttsVolumeMultiplier
        }
        
        val newVolume = if (action.isAbsolute) {
            action.amount.coerceIn(0.0f, 1.0f)
        } else {
            (currentVolume + action.amount).coerceIn(0.0f, 1.0f)
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
        
        ttsHelper?.speakRouted("Lautstärke ${(newVolume * 100).toInt()}%", targetDeviceAddress, action.ttsMode) {
            onFinish(executionId)
        } ?: onFinish(executionId)
    }
}

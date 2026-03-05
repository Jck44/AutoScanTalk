package com.andreas_kratzer.ghosttalk.domain.tts

import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import javax.inject.Inject

class SetTtsVolumeUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val ttsHelper: TextToSpeechHelper
) {
    fun execute(multiplier: Float, isForCues: Boolean) {
        if (isForCues) {
            settingsRepository.cuesVolumeMultiplier = multiplier
            ttsHelper.speakRouted("Hinweis Lautstärke geändert", settingsRepository.cuesAudioDeviceAddress)
        } else {
            settingsRepository.ttsVolumeMultiplier = multiplier
            ttsHelper.speakRouted("Lautstärke geändert", settingsRepository.ttsAudioDeviceAddress)
        }
    }
}
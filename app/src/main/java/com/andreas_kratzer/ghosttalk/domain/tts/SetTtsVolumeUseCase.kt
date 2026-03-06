package com.andreas_kratzer.ghosttalk.domain.tts

import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import javax.inject.Inject

class SetTtsVolumeUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val ttsHelper: TextToSpeechHelper
) {
    fun execute(multiplier: Float, isForCues: Boolean, playFeedback: Boolean = true) {
        if (isForCues) {
            settingsRepository.cuesVolumeMultiplier = multiplier
            if (playFeedback) {
                ttsHelper.speakRouted("Hinweis Lautstärke geändert", settingsRepository.cuesAudioDeviceAddress)
            }
        } else {
            settingsRepository.ttsVolumeMultiplier = multiplier
            if (playFeedback) {
                ttsHelper.speakRouted("Lautstärke geändert", settingsRepository.ttsAudioDeviceAddress)
            }
        }
    }
}
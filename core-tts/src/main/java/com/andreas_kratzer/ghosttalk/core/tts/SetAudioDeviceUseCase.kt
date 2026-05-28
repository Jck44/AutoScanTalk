package com.andreas_kratzer.ghosttalk.core.tts

import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import javax.inject.Inject

class SetAudioDeviceUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val ttsHelper: TextToSpeechHelper
) {
    fun execute(address: String?, isForCues: Boolean) {
        if (isForCues) {
            settingsRepository.cuesAudioDeviceAddress = address
            ttsHelper.speakRouted("Ausgabegerät für Feedback ausgewählt", address)
        } else {
            settingsRepository.ttsAudioDeviceAddress = address
            ttsHelper.speakRouted("Ausgabegerät für Sprechen ausgewählt", address)
        }
    }
}
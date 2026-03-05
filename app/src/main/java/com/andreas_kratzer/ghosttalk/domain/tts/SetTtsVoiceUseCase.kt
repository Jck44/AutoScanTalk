package com.andreas_kratzer.ghosttalk.domain.tts

import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import javax.inject.Inject

class SetTtsVoiceUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val ttsHelper: TextToSpeechHelper
) {
    operator fun invoke(voiceName: String?) {
        settingsRepository.ttsVoiceName = voiceName
        ttsHelper.setVoice(voiceName)
        ttsHelper.speakRouted("Stimme ausgewählt", settingsRepository.ttsAudioDeviceAddress)
    }
}
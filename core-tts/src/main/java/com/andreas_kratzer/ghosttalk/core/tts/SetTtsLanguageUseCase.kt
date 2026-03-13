package com.andreas_kratzer.ghosttalk.core.tts

import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import javax.inject.Inject

class SetTtsLanguageUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val ttsHelper: TextToSpeechHelper
) {
    operator fun invoke(languageTag: String) {
        val finalLanguage = if (languageTag == "default") null else languageTag
        settingsRepository.ttsLanguage = finalLanguage
        settingsRepository.ttsVoiceName = null
        ttsHelper.setLanguageAndVoice(languageTag, null)
        ttsHelper.speakRouted("Sprache ausgewählt", settingsRepository.ttsAudioDeviceAddress)
    }
}

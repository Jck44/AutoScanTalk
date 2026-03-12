package com.andreas_kratzer.ghosttalk.domain.tts

import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
import javax.inject.Inject

class SetTtsLanguageUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val ttsHelper: TextToSpeechHelper
) {
    operator fun invoke(languageTag: String) {
        val tagToSave = if (languageTag == "default") null else languageTag
        settingsRepository.ttsLanguage = tagToSave
        settingsRepository.ttsVoiceName = null
        ttsHelper.setLanguageAndVoice(languageTag, null)
        ttsHelper.speakRouted("Sprache geändert", settingsRepository.ttsAudioDeviceAddress)
    }
}
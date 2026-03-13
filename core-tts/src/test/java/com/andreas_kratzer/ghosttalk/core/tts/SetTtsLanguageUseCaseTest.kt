package com.andreas_kratzer.ghosttalk.core.tts

import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class SetTtsLanguageUseCaseTest {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var ttsHelper: TextToSpeechHelper
    private lateinit var useCase: SetTtsLanguageUseCase

    @Before
    fun setup() {
        settingsRepository = mockk(relaxed = true)
        ttsHelper = mockk(relaxed = true)
        useCase = SetTtsLanguageUseCase(settingsRepository, ttsHelper)
    }

    @Test
    fun `invoke with default sets ttsLanguage to null and updates ttsHelper`() {
        useCase("default")

        verify { settingsRepository.ttsLanguage = null }
        verify { settingsRepository.ttsVoiceName = null }
        verify { ttsHelper.setLanguageAndVoice("default", null) }
        verify { ttsHelper.speakRouted(any(), any()) }
    }

    @Test
    fun `invoke with specific language sets ttsLanguage and updates ttsHelper`() {
        useCase("de-DE")

        verify { settingsRepository.ttsLanguage = "de-DE" }
        verify { settingsRepository.ttsVoiceName = null }
        verify { ttsHelper.setLanguageAndVoice("de-DE", null) }
        verify { ttsHelper.speakRouted(any(), any()) }
    }
}

package com.andreas_kratzer.ghosttalk.core.tts


import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class SetTtsVoiceUseCaseTest {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var ttsHelper: TextToSpeechHelper
    private lateinit var useCase: SetTtsVoiceUseCase

    @Before
    fun setup() {
        settingsRepository = mockk(relaxed = true)
        ttsHelper = mockk(relaxed = true)
        useCase = SetTtsVoiceUseCase(settingsRepository, ttsHelper)
    }

    @Test
    fun `invoke with voice name updates settings and ttsHelper`() {
        useCase("en-us-x-sfg#male_2-local")

        verify { settingsRepository.ttsVoiceName = "en-us-x-sfg#male_2-local" }
        verify { ttsHelper.setVoice("en-us-x-sfg#male_2-local") }
        verify { ttsHelper.speakRouted(any(), any()) }
    }

    @Test
    fun `invoke with null voice name updates settings and ttsHelper`() {
        useCase(null)

        verify { settingsRepository.ttsVoiceName = null }
        verify { ttsHelper.setVoice(null) }
        verify { ttsHelper.speakRouted(any(), any()) }
    }
}

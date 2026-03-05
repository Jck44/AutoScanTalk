package com.andreas_kratzer.ghosttalk.domain

import com.andreas_kratzer.ghosttalk.domain.tts.SetTtsVolumeUseCase

import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class SetTtsVolumeUseCaseTest {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var ttsHelper: TextToSpeechHelper
    private lateinit var useCase: SetTtsVolumeUseCase

    @Before
    fun setup() {
        settingsRepository = mockk(relaxed = true)
        ttsHelper = mockk(relaxed = true)
        useCase = SetTtsVolumeUseCase(settingsRepository, ttsHelper)
    }

    @Test
    fun `execute for TTS volume updates settings and speaks`() {
        useCase.execute(0.8f, isForCues = false)

        verify { settingsRepository.ttsVolumeMultiplier = 0.8f }
        verify { ttsHelper.speakRouted("Lautstärke geändert", any()) }
    }

    @Test
    fun `execute for Cues volume updates settings and speaks`() {
        useCase.execute(0.5f, isForCues = true)

        verify { settingsRepository.cuesVolumeMultiplier = 0.5f }
        verify { ttsHelper.speakRouted("Hinweis Lautstärke geändert", any()) }
    }
}

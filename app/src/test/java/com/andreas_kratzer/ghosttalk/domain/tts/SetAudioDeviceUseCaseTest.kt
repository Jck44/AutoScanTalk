package com.andreas_kratzer.ghosttalk.domain.tts


import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class SetAudioDeviceUseCaseTest {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var ttsHelper: TextToSpeechHelper
    private lateinit var useCase: SetAudioDeviceUseCase

    @Before
    fun setup() {
        settingsRepository = mockk(relaxed = true)
        ttsHelper = mockk(relaxed = true)
        useCase = SetAudioDeviceUseCase(settingsRepository, ttsHelper)
    }

    @Test
    fun `execute for TTS device updates settings and speaks`() {
        useCase.execute("tts_device_address", isForCues = false)

        verify { settingsRepository.ttsAudioDeviceAddress = "tts_device_address" }
        verify { ttsHelper.speakRouted("Ausgabegerät für Sprechen ausgewählt", "tts_device_address") }
    }

    @Test
    fun `execute for Cues device updates settings and speaks`() {
        useCase.execute("cues_device_address", isForCues = true)

        verify { settingsRepository.cuesAudioDeviceAddress = "cues_device_address" }
        verify { ttsHelper.speakRouted("Ausgabegerät für Feedback ausgewählt", "cues_device_address") }
    }
}

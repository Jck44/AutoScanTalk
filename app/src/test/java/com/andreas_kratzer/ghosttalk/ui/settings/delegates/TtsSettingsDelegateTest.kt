package com.andreas_kratzer.ghosttalk.ui.settings.delegates

import com.andreas_kratzer.ghosttalk.core.audio.AudioDeviceManager
import com.andreas_kratzer.ghosttalk.core.model.AudioOutputDevice
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.domain.tts.GetAudioDevicesUseCase
import com.andreas_kratzer.ghosttalk.domain.tts.SetTtsLanguageUseCase
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TtsSettingsDelegateTest {

    private lateinit var context: android.content.Context
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var audioDeviceManager: AudioDeviceManager
    private lateinit var getAudioDevicesUseCase: GetAudioDevicesUseCase
    private lateinit var setTtsLanguageUseCase: SetTtsLanguageUseCase
    private lateinit var delegate: TtsSettingsDelegate

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        audioDeviceManager = mockk(relaxed = true)
        getAudioDevicesUseCase = mockk(relaxed = true)
        setTtsLanguageUseCase = mockk(relaxed = true)

        delegate = TtsSettingsDelegate(
            context,
            settingsRepository,
            audioDeviceManager,
            getAudioDevicesUseCase,
            setTtsLanguageUseCase
        )
    }

    @Test
    fun `loadAvailableAudioDevices updates state flow`() = runTest {
        delegate.initialize(this) { _, _ -> }
        val mockDevices = listOf(mockk<AudioOutputDevice>())
        coEvery { getAudioDevicesUseCase.execute() } returns mockDevices

        delegate.loadAvailableAudioDevices()
        runCurrent()

        assertEquals(mockDevices, delegate.availableAudioDevices.value)
    }

    @Test
    fun `setTtsLanguage calls use case`() {
        delegate.setTtsLanguage("de-DE")
        verify { setTtsLanguageUseCase.invoke("de-DE") }
    }

    @Test
    fun `setTtsVoice updates settings`() {
        delegate.setTtsVoice("test-voice")
        verify { settingsRepository.ttsVoiceName = "test-voice" }
    }
}

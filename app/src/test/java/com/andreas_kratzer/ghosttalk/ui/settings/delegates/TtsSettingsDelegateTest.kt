package com.andreas_kratzer.ghosttalk.ui.settings.delegates

import com.andreas_kratzer.ghosttalk.core.audio.AudioDeviceManager
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.domain.tts.GetAudioDevicesUseCase
import com.andreas_kratzer.ghosttalk.domain.tts.SetAudioDeviceUseCase
import com.andreas_kratzer.ghosttalk.domain.tts.SetTtsLanguageUseCase
import com.andreas_kratzer.ghosttalk.domain.tts.SetTtsVoiceUseCase
import com.andreas_kratzer.ghosttalk.domain.tts.SetTtsVolumeUseCase
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TtsSettingsDelegateTest {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var ttsHelper: TextToSpeechHelper
    private lateinit var audioDeviceManager: AudioDeviceManager
    private lateinit var setTtsLanguageUseCase: SetTtsLanguageUseCase
    private lateinit var setTtsVoiceUseCase: SetTtsVoiceUseCase
    private lateinit var setTtsVolumeUseCase: SetTtsVolumeUseCase
    private lateinit var setAudioDeviceUseCase: SetAudioDeviceUseCase
    private lateinit var getAudioDevicesUseCase: GetAudioDevicesUseCase
    private lateinit var delegate: TtsSettingsDelegate

    @Before
    fun setup() {
        settingsRepository = mockk(relaxed = true)
        ttsHelper = mockk(relaxed = true)
        audioDeviceManager = mockk(relaxed = true) {
            every { availableDevicesFlow } returns MutableStateFlow(emptyList())
        }
        setTtsLanguageUseCase = mockk(relaxed = true)
        setTtsVoiceUseCase = mockk(relaxed = true)
        setTtsVolumeUseCase = mockk(relaxed = true)
        setAudioDeviceUseCase = mockk(relaxed = true)
        getAudioDevicesUseCase = mockk(relaxed = true)

        delegate = TtsSettingsDelegate(
            settingsRepository,
            ttsHelper,
            audioDeviceManager,
            setTtsLanguageUseCase,
            setTtsVoiceUseCase,
            setTtsVolumeUseCase,
            setAudioDeviceUseCase,
            getAudioDevicesUseCase
        )
    }

    @Test
    fun `initialize sets up tts helper and loads data`() = runTest {
        // Use UnconfinedTestDispatcher for the background execution to avoid timing issues
        delegate.backgroundDispatcher = UnconfinedTestDispatcher(testScheduler)
        
        every { ttsHelper.isReady } returns true
        every { getAudioDevicesUseCase.execute() } returns listOf(mockk())

        // Pass backgroundScope so the infinite collect flow is cancelled when the test finishes
        delegate.initialize(backgroundScope) { _, _ -> }
        advanceUntilIdle()

        verify { ttsHelper.setLanguageAndVoice(any(), any()) }
        verify { ttsHelper.getAvailableLanguages() }
        verify { ttsHelper.getAvailableVoices(any()) }
        verify { getAudioDevicesUseCase.execute() }
        assertEquals(1, delegate.availableAudioDevices.value.size)
    }

    @Test
    fun `setTtsLanguage calls use case and reloads voices`() {
        every { ttsHelper.isReady } returns true
        every { setTtsLanguageUseCase.invoke(any()) } returns Unit
        delegate.setTtsLanguage("de-DE")
        verify { setTtsLanguageUseCase.invoke("de-DE") }
        verify { ttsHelper.getAvailableVoices(any()) } 
    }

    @Test
    fun `setTtsVolumeMultiplier calls use case for tts`() {
        delegate.setTtsVolumeMultiplier(0.8f)
        verify { setTtsVolumeUseCase.execute(0.8f, isForCues = false, playFeedback = true) }
    }

    @Test
    fun `setCuesVolumeMultiplier calls use case for cues`() {
        delegate.setCuesVolumeMultiplier(0.5f)
        verify { setTtsVolumeUseCase.execute(0.5f, isForCues = true, playFeedback = true) }
    }
}

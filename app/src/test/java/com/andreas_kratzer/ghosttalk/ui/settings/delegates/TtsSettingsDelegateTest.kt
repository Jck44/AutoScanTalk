package com.andreas_kratzer.ghosttalk.ui.settings.delegates

import com.andreas_kratzer.ghosttalk.core.audio.AudioDeviceManager
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.model.AudioOutputDevice
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class TtsSettingsDelegateTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var ttsHelper: TextToSpeechHelper
    private lateinit var audioDeviceManager: AudioDeviceManager
    private lateinit var delegate: TtsSettingsDelegate

    @Before
    fun setup() {
        settingsRepository = mockk(relaxed = true)
        ttsHelper = mockk(relaxed = true)
        audioDeviceManager = mockk(relaxed = true)
        delegate = TtsSettingsDelegate(settingsRepository, ttsHelper, audioDeviceManager)
    }

    @Test
    fun `initialize loads values from repository and helper`() = runTest {
        every { settingsRepository.ttsLanguage } returns "de"
        every { settingsRepository.ttsVoiceName } returns "de-voice"
        every { ttsHelper.isReady } returns true
        val mockLocales = listOf(Locale.GERMAN, Locale.ENGLISH)
        every { ttsHelper.getAvailableLanguages() } returns mockLocales

        delegate.initialize(testScope) { _, _ -> }

        verify { ttsHelper.setLanguageAndVoice("de", "de-voice") }
        assertEquals(mockLocales, delegate.availableLanguages.value)
    }

    @Test
    fun `setTtsLanguage resets voice and saves to repository`() = runTest {
        delegate.setTtsLanguage("en-US")

        verify { settingsRepository.ttsLanguage = "en-US" }
        verify { settingsRepository.ttsVoiceName = null }
        verify { ttsHelper.setLanguageAndVoice("en-US", null) }
    }

    @Test
    fun `loadAvailableAudioDevices merges current and cached devices`() {
        val activeDevice = AudioOutputDevice("mac1", "Speaker", 0, true)
        every { audioDeviceManager.getAvailableOutputDevices() } returns listOf(activeDevice)
        every { settingsRepository.ttsAudioDeviceAddress } returns "mac2"
        every { settingsRepository.cuesAudioDeviceAddress } returns null
        every { settingsRepository.getDeviceName("mac1") } returns "Speaker"
        every { settingsRepository.getDeviceName("mac2") } returns "Old Headset"

        delegate.loadAvailableAudioDevices()

        val devices = delegate.availableAudioDevices.value
        assertEquals("Should have 2 devices (1 active, 1 inactive)", 2, devices.size)
        assert(devices.any { it.address == "mac1" })
        assert(devices.any { it.address == "mac2" && it.name.contains("(Inaktiv)") })
    }

    @Test
    fun `getResolvedDeviceName returns correct name for active and inactive devices`() {
        val activeDevice = AudioOutputDevice("mac1", "Speaker", 0, true)
        every { audioDeviceManager.getAvailableOutputDevices() } returns listOf(activeDevice)
        delegate.loadAvailableAudioDevices()

        assertEquals("Speaker", delegate.getResolvedDeviceName("mac1"))
        
        every { settingsRepository.getDeviceName("mac2") } returns "Old Headset"
        assertEquals("Old Headset (Laden...)", delegate.getResolvedDeviceName("mac2"))
        
        assertEquals("System-Standard (Automatisch)", delegate.getResolvedDeviceName(null))
    }
}

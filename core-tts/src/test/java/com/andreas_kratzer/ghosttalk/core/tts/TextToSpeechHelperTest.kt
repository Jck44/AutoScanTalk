package com.andreas_kratzer.ghosttalk.core.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import com.andreas_kratzer.ghosttalk.core.audio.RoutedAudioPlayer
import com.andreas_kratzer.ghosttalk.core.settings.TtsSettings
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import javax.inject.Provider

@OptIn(ExperimentalCoroutinesApi::class)
class TextToSpeechHelperTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val mockContext = mockk<Context>(relaxed = true)
    private val mockSettingsRepository = mockk<TtsSettings>(relaxed = true)
    private val mockAndroidProvider = mockk<AndroidTtsProvider>(relaxed = true)
    private val mockElevenLabsProvider = mockk<ElevenLabsTtsProvider>(relaxed = true)
    
    private val androidProviderWrapper = Provider { mockAndroidProvider }
    private val elevenLabsProviderWrapper = Provider { mockElevenLabsProvider }

    private lateinit var helper: TextToSpeechHelper

    private val ttsEngineFlow = MutableStateFlow("google")

    @Before
    fun setup() {
        every { mockSettingsRepository.ttsLanguageFlow } returns MutableStateFlow("de-DE")
        every { mockSettingsRepository.ttsVoiceNameFlow } returns MutableStateFlow("default")
        every { mockSettingsRepository.ttsEngineFlow } returns ttsEngineFlow
        
        every { mockContext.cacheDir } returns File("/tmp")

        helper = TextToSpeechHelper(
            mockContext,
            testScope,
            mockSettingsRepository,
            androidProviderWrapper,
            elevenLabsProviderWrapper
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `isReady delegates to current provider`() {
        every { mockAndroidProvider.isReady } returns true
        assertTrue(helper.isReady)
        
        every { mockAndroidProvider.isReady } returns false
        assertFalse(helper.isReady)
    }

    @Test
    fun `speak delegates to current provider`() {
        helper.speak("Hello")
        verify { mockAndroidProvider.speak("Hello", any(), any(), any()) }
    }

    @Test
    fun `speakRouted delegates to current provider`() {
        helper.speakRouted("Hello", "device_addr")
        verify { mockAndroidProvider.speakRouted("Hello", "device_addr", any(), any(), any(), any()) }
    }

    @Test
    fun `switching engine updates current provider`() {
        // Switch to elevenlabs
        ttsEngineFlow.value = "elevenlabs"
        
        helper.speak("Cloud Hello")
        verify { mockElevenLabsProvider.speak("Cloud Hello", any(), any(), any()) }
        verify(exactly = 0) { mockAndroidProvider.speak("Cloud Hello", any(), any(), any()) }
    }

    @Test
    fun `stopAll delegates to current provider`() {
        helper.stopAll()
        verify { mockAndroidProvider.stopAll() }
    }

    @Test
    fun `stopNotificationTTS stops only if reading notification`() {
        // Case 1: Not reading
        helper.isReadingNotification = false
        helper.stopNotificationTTS()
        verify(exactly = 0) { mockAndroidProvider.stopAll() }
        
        // Case 2: Is reading
        helper.isReadingNotification = true
        helper.stopNotificationTTS()
        verify(exactly = 1) { mockAndroidProvider.stopAll() }
    }

    @Test
    fun `fallback to android when elevenlabs fails`() {
        // Switch to elevenlabs
        ttsEngineFlow.value = "elevenlabs"
        
        // Capture the error callback from ElevenLabs.speak
        val errorSlot = io.mockk.slot<(String) -> Unit>()
        every { 
            mockElevenLabsProvider.speak(any(), any(), any(), capture(errorSlot)) 
        } answers {
            // Simulate error by calling the captured error callback
            errorSlot.captured.invoke("API Error")
        }
        
        helper.speak("Fallback Test")
        
        // Verify ElevenLabs was tried
        verify { mockElevenLabsProvider.speak("Fallback Test", any(), any(), any()) }
        // Verify Android TTS was called as fallback
        verify { mockAndroidProvider.speak("Fallback Test", any(), any(), any()) }
    }
}

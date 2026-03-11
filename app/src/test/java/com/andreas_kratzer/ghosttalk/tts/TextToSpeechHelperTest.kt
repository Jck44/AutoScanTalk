package com.andreas_kratzer.ghosttalk.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import com.andreas_kratzer.ghosttalk.core.audio.RoutedAudioPlayer
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
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

@OptIn(ExperimentalCoroutinesApi::class)
class TextToSpeechHelperTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val mockContext = mockk<Context>(relaxed = true)
    private val mockSettingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val mockAudioPlayer = mockk<RoutedAudioPlayer>(relaxed = true)
    private val mockVoiceManager = mockk<TtsVoiceManager>(relaxed = true)

    private lateinit var helper: TextToSpeechHelper

    @Before
    fun setup() {
        mockkConstructor(TextToSpeech::class)
        // Ensure any new TextToSpeech instance uses our mock behavior
        every { anyConstructed<TextToSpeech>().setLanguage(any()) } returns TextToSpeech.LANG_AVAILABLE
        
        every { mockSettingsRepository.ttsLanguageFlow } returns MutableStateFlow("de-DE")
        every { mockSettingsRepository.ttsVoiceNameFlow } returns MutableStateFlow("default")
        
        every { mockContext.cacheDir } returns File("/tmp")

        helper = TextToSpeechHelper(
            mockContext,
            testScope,
            mockSettingsRepository,
            mockAudioPlayer,
            mockVoiceManager
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `isReady is false initially`() {
        assertFalse(helper.isReady)
    }

    @Test
    fun `onInit success sets initialized to true`() {
        helper.onInit(TextToSpeech.SUCCESS)
        assertTrue(helper.isReady)
    }

    @Test
    fun `onInit failure resets state`() {
        helper.onInit(TextToSpeech.ERROR)
        assertFalse(helper.isReady)
    }

    @Test
    fun `speak invokes onDone immediately if not initialized`() {
        val onDone = mockk<() -> Unit>(relaxed = true)
        helper.speak("Hello", onDone = onDone)
        verify { onDone.invoke() }
    }

    @Test
    fun `speak with QUEUE_FLUSH stops audio player`() {
        helper.onInit(TextToSpeech.SUCCESS)
        helper.speak("Hello", queueMode = TextToSpeech.QUEUE_FLUSH)
        
        verify { mockAudioPlayer.stopAll() }
    }

    @Test
    fun `speak with deviceAddress uses synthesizeToFile`() {
        helper.onInit(TextToSpeech.SUCCESS)
        helper.speakRouted("Hello", "device_addr")
        
        verify { anyConstructed<TextToSpeech>().synthesizeToFile(any(), any(), any<File>(), any()) }
    }

    @Test
    fun `speak without deviceAddress uses speak`() {
        helper.onInit(TextToSpeech.SUCCESS)
        helper.speakRouted("Hello", null)
        
        verify { anyConstructed<TextToSpeech>().speak("Hello", any(), any<Bundle>(), any()) }
    }


    @Test
    fun `stopNotificationTTS stops only if reading notification`() {
        helper.onInit(TextToSpeech.SUCCESS)
        
        // Case 1: Not reading
        helper.isReadingNotification = false
        helper.stopNotificationTTS()
        verify(exactly = 0) { anyConstructed<TextToSpeech>().stop() }
        
        // Case 2: Is reading
        helper.isReadingNotification = true
        helper.stopNotificationTTS()
        verify(exactly = 1) { anyConstructed<TextToSpeech>().stop() }
        verify(exactly = 1) { mockAudioPlayer.stopAll() }
    }

    @Test
    fun `setLanguageAndVoice handles unsupported language`() {
        helper.onInit(TextToSpeech.SUCCESS)
        every { anyConstructed<TextToSpeech>().setLanguage(any()) } returns TextToSpeech.LANG_NOT_SUPPORTED
        
        helper.setLanguageAndVoice("unsupported-LANG")
        
        // Should still call setLanguage
        verify { anyConstructed<TextToSpeech>().language = any() }
    }
}

package com.andreas_kratzer.ghosttalk.tts

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

class TtsVoiceManagerTest {

    private lateinit var ttsVoiceManager: TtsVoiceManager
    private val mockTts = mockk<TextToSpeech>(relaxed = true)

    @Before
    fun setup() {
        ttsVoiceManager = TtsVoiceManager()
    }

    @Test
    fun `getAvailableLanguages returns sorted list of locales by display name`() {
        val locales = setOf(Locale.US, Locale.GERMANY, Locale.FRANCE)
        every { mockTts.availableLanguages } returns locales

        val result = ttsVoiceManager.getAvailableLanguages(mockTts)

        assertEquals(3, result.size)
        // Check that it's sorted by display name
        assertTrue(result[0].displayName <= result[1].displayName)
        assertTrue(result[1].displayName <= result[2].displayName)
    }

    @Test
    fun `getAvailableLanguages returns empty list on exception`() {
        every { mockTts.availableLanguages } throws RuntimeException("TTS Error")
        
        val result = ttsVoiceManager.getAvailableLanguages(mockTts)
        
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getAvailableVoices returns filtered and sorted voices`() {
        // Voice objects are tricky to mock because they are final in some Android versions.
        // However, MockK can usually handle this if it's running on a JVM with proper setup.
        val voice1 = mockk<Voice>()
        every { voice1.locale } returns Locale.US
        every { voice1.name } returns "B_Voice"

        val voice2 = mockk<Voice>()
        every { voice2.locale } returns Locale.US
        every { voice2.name } returns "A_Voice"

        val voice3 = mockk<Voice>()
        every { voice3.locale } returns Locale.GERMANY
        every { voice3.name } returns "German_Voice"

        every { mockTts.voices } returns setOf(voice1, voice2, voice3)

        val result = ttsVoiceManager.getAvailableVoices(mockTts, "en-US")

        assertEquals(2, result.size)
        assertEquals("A_Voice", result[0].name)
        assertEquals("B_Voice", result[1].name)
    }

    @Test
    fun `findVoice returns correct voice by name`() {
        val voice1 = mockk<Voice>()
        every { voice1.name } returns "TargetVoice"

        val voice2 = mockk<Voice>()
        every { voice2.name } returns "OtherVoice"

        every { mockTts.voices } returns setOf(voice1, voice2)

        val result = ttsVoiceManager.findVoice(mockTts, "TargetVoice")

        assertEquals(voice1, result)
    }

    @Test
    fun `findVoice returns null if voice not found`() {
        every { mockTts.voices } returns emptySet()
        val result = ttsVoiceManager.findVoice(mockTts, "NonExistent")
        assertTrue(result == null)
    }
}

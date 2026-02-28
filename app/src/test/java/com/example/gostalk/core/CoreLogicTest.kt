package com.example.gostalk.core

import android.content.Context
import com.example.gostalk.model.AuditoryCue
import com.example.gostalk.model.ButtonConfig
import com.example.gostalk.model.NavigateToPageButtonAction
import com.example.gostalk.model.SpeakTextButtonAction
import com.example.gostalk.tts.TextToSpeechHelper
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoreLogicTest {

    private lateinit var ttsHelper: TextToSpeechHelper
    private lateinit var context: Context

    @Before
    fun setup() {
        ttsHelper = mockk(relaxed = true)
        context = mockk(relaxed = true)
        
        // Mock default TTS behavior
        every { ttsHelper.speak(any(), any()) } just Runs
    }

    @Test
    fun testAuditoryCue_WhenNoExplicitText_UsesLabelFallback() {
        val config = ButtonConfig(
            id = "b1",
            label = "Apple",
            spokenText = null,
            auditoryCue = null, // No explicit cue provided
            buttonAction = SpeakTextButtonAction("dummy")
        )

        // Simulate logic that will be extracted to ScannerEngine handling AudioRouting
        val cueText = config.auditoryCue?.let { 
            when (it) {
                is AuditoryCue.TextToSpeechCue -> it.text
            }
        } ?: config.label // FALLBACK

        assertEquals("Apple", cueText)
    }

    @Test
    fun testAuditoryCue_WhenExplicitTextProvided_IgnoresLabel() {
        val config = ButtonConfig(
            id = "b1",
            label = "Apple",
            spokenText = null,
            auditoryCue = AuditoryCue.TextToSpeechCue("Red fruit"),
            buttonAction = SpeakTextButtonAction("dummy")
        )

        val cueText = config.auditoryCue?.let { 
            when (it) {
                is AuditoryCue.TextToSpeechCue -> it.text
            }
        } ?: config.label

        assertEquals("Red fruit", cueText)
    }

    @Test
    fun testSpeakAction_PrefersSpokenTextOverLabel() = runTest {
        val action = SpeakTextButtonAction(textToSpeech = "Speak me instead")
        val config = ButtonConfig(
            id = "b1",
            label = "Short Label",
            spokenText = "Speak me instead",
            auditoryCue = null,
            buttonAction = action
        )

        val expectedSpeech = config.spokenText ?: config.label
        assertEquals("Speak me instead", expectedSpeech)
    }

    @Test
    fun testNavigateAction_HasData() = runTest {
        val action = NavigateToPageButtonAction(pageId = "page2", ttsFeedback = null)
        assertEquals("page2", action.pageId)
        assertEquals(null, action.ttsFeedback)
    }
}

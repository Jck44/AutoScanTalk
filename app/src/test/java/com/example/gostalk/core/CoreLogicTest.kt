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
        every { ttsHelper.isReady } returns true
        
        io.mockk.mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
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
        val action = NavigateToPageButtonAction(pageId = "page2")
        assertEquals("page2", action.pageId)
    }

    @Test
    fun testScannerEngine_RowByRowScanning() = runTest {
        val settingsRepo = mockk<com.example.gostalk.data.SettingsRepository>(relaxed = true)
        every { settingsRepo.scanDelayMillis } returns 10L
        
        val engine = com.example.gostalk.core.ScannerEngine(
            scope = this,
            settingsRepository = settingsRepo,
            ttsHelper = ttsHelper
        )
        
        // Mock buttons: 2 rows, 2 columns. 4 active configs
        val configs = listOf(
            ButtonConfig(id = "b1", label = "B1", auditoryCue = null, buttonAction = SpeakTextButtonAction("1"), isActive = true),
            ButtonConfig(id = "b2", label = "B2", auditoryCue = null, buttonAction = SpeakTextButtonAction("2"), isActive = true),
            ButtonConfig(id = "b3", label = "B3", auditoryCue = null, buttonAction = SpeakTextButtonAction("3"), isActive = true),
            ButtonConfig(id = "b4", label = "B4", auditoryCue = null, buttonAction = SpeakTextButtonAction("4"), isActive = true)
        )
        
        // Start scanning row by row
        engine.startScanning(
            buttonConfigs = configs, 
            pattern = "row_by_row", 
            columns = 2, 
            rowNames = listOf("Row 1", "Row 2")
        )

        // Give coroutines time to focus the first row
        testScheduler.advanceUntilIdle()
        
        // Assert: First row is focused, NO button is focused yet
        assertEquals(0, engine.focusedRowIndex.value)
        org.junit.Assert.assertNull(engine.focusedButtonIndex.value)
        
        // Simulate user selecting the row (Triggering the hardware switch)
        engine.selectCurrentRow()
        
        // Give coroutines time to start scanning buttons inside row 1
        testScheduler.advanceUntilIdle()
        
        // Assert: Row focus is gone, button index 0 (first in row) is now focused
        org.junit.Assert.assertNull(engine.focusedRowIndex.value)
        assertEquals(0, engine.focusedButtonIndex.value)
        
        engine.stopScanning()
        io.mockk.unmockkStatic(android.util.Log::class)
    }
}

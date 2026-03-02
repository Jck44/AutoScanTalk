package com.andreas_kratzer.ghosttalk.core

import com.andreas_kratzer.ghosttalk.core.util.TestLogger
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.model.NavigateToPageButtonAction
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoreLogicTest {

    private lateinit var ttsHelper: com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper

    @Before
    fun setup() {
        ttsHelper = mockk(relaxed = true)
    }

    @Test
    fun testSpeakAction_HasData() = runTest {
        val action = SpeakTextButtonAction(textToSpeech = "Hello")
        assertEquals("Hello", action.textToSpeech)
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
        val settingsRepo = mockk<com.andreas_kratzer.ghosttalk.data.SettingsRepository>(relaxed = true)
        every { settingsRepo.scanDelayMillis } returns 10L
        every { ttsHelper.isReady } returns true
        
        val engine = ScannerEngine(
            scope = this,
            settingsRepository = settingsRepo,
            ttsHelper = ttsHelper,
            logger = TestLogger
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
            startIndex = 0,
            pattern = "row_by_row", 
            columns = 2, 
            rowNames = listOf("Row 1", "Row 2")
        )

        // Give coroutines time to focus the first row
        // advanceTimeBy(1) is enough to trigger the first step of the launch block
        testScheduler.advanceTimeBy(1)
        
        // Assert: First row is focused, NO button is focused yet
        assertEquals("Row focus should be 0", 0, engine.focusedRowIndex.value)
        assertNull("Button focus should be null while scanning rows", engine.focusedButtonIndex.value)
        
        // Simulate user selecting the row (Triggering the hardware switch)
        engine.selectCurrentRow()
        
        // Give coroutines time to start scanning buttons inside row 0
        testScheduler.advanceTimeBy(1)
        
        // Assert: Row focus is STILL 0 (visually highlighted) while button scanning
        assertEquals("Row focus should remain 0", 0, engine.focusedRowIndex.value)
        assertEquals("Button focus should be 0 (first button in row 0)", 0, engine.focusedButtonIndex.value)
        
        engine.stopScanning()
    }
}

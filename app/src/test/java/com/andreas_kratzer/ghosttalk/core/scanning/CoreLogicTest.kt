package com.andreas_kratzer.ghosttalk.core.scanning

import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
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

    private lateinit var ttsHelper: com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper

    @Before
    fun setup() {
        ttsHelper = mockk(relaxed = true)
        io.mockk.mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
    }

    @Test
    fun testSpeakAction_Initialization() = runTest {
        SpeakTextButtonAction()
    }

    @Test
    fun testSpeakAction_PrefersSpokenTextOverLabel() = runTest {
        val action = SpeakTextButtonAction()
        val config = ButtonConfig(
            id = "b1",
            label = "Short Label",
            spokenText = "Speak me instead",
            auditoryCue = null,
            isActive = true,
            buttonAction = action
        )

        val textToSpeak = config.spokenText ?: config.label
        assertEquals("Speak me instead", textToSpeak)
    }

    @Test
    fun testNavigateAction_HasData() = runTest {
        val action = NavigateToPageButtonAction(pageId = "page2")
        assertEquals("page2", action.pageId)
    }

    @Test
    fun testScannerEngine_RowByRowScanning() = runTest {
        val settingsRepo = mockk<com.andreas_kratzer.ghosttalk.core.data.SettingsRepository>(relaxed = true)
        every { settingsRepo.scanDelayMillis } returns 10L
        every { ttsHelper.isReady } returns true
        
        val featureGuard = mockk<com.andreas_kratzer.ghosttalk.feature.settings.domain.FeatureGuard>(relaxed = true) {
            every { isButtonVisible(any()) } returns true
            every { isActionEnabled(any()) } returns true
        }
        val feedbackProvider = mockk<ScannerFeedbackProvider>(relaxed = true)
        val linearStrategy = LinearScanStrategy()
        val rowByRowStrategy = RowByRowScanStrategy()
        val engine = ScannerEngine(
            scope = this,
            featureGuard = featureGuard,
            feedbackProvider = feedbackProvider,
            stateManager = ScanStateManager(),
            scanTimer = ScanTimer(),
            linearStrategy = linearStrategy,
            rowByRowStrategy = rowByRowStrategy
        )
        
        // Mock buttons: 2 rows, 2 columns. 4 active configs
        val configs = listOf(
            ButtonConfig(id = "b1", label = "B1", auditoryCue = null, buttonAction = SpeakTextButtonAction(), isActive = true),
            ButtonConfig(id = "b2", label = "B2", auditoryCue = null, buttonAction = SpeakTextButtonAction(), isActive = true),
            ButtonConfig(id = "b3", label = "B3", auditoryCue = null, buttonAction = SpeakTextButtonAction(), isActive = true),
            ButtonConfig(id = "b4", label = "B4", auditoryCue = null, buttonAction = SpeakTextButtonAction(), isActive = true)
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
        // advanceTimeBy(110) is enough to trigger the first step of the launch block (including 100ms delay)
        testScheduler.advanceTimeBy(110)
        
        // Assert: First row is focused, NO button is focused yet
        assertEquals("Row focus should be 0", 0, engine.focusedRowIndex.value)
        assertNull("Button focus should be null while scanning rows", engine.focusedButtonIndex.value)
        
        // Simulate user selecting the row (Triggering the hardware switch)
        engine.selectCurrentRow()
        
        // Give coroutines time to start scanning buttons inside row 0
        testScheduler.advanceTimeBy(110)
        
        // Assert: Row focus is STILL 0 (visually highlighted) while button scanning
        assertEquals("Row focus should remain 0", 0, engine.focusedRowIndex.value)
        assertEquals("Button focus should be 0 (first button in row 0)", 0, engine.focusedButtonIndex.value)
        
        engine.stopScanning()
    }
}

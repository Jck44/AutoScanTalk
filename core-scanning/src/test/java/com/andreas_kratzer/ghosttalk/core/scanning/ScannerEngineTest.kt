package com.andreas_kratzer.ghosttalk.core.scanning

import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScannerEngineTest {

    private lateinit var featureGuard: FeatureGuardProxy
    private lateinit var feedbackProvider: ScannerFeedbackProvider

    @Before
    fun setup() {
        io.mockk.mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0

        featureGuard = mockk(relaxed = true) {
            every { isButtonVisible(any()) } returns true
        }
        feedbackProvider = mockk(relaxed = true)
    }

    /**
     * Creates configs where buttons at specified global indices are active.
     * Uses 49 slots (7x7) as base capacity.
     */
    private fun createConfigs(activeIndices: List<Int>): List<ButtonConfig> {
        return (0 until 49).map { i ->
            ButtonConfig(
                id = "b${i + 1}",
                label = "B${i + 1}",
                spokenText = "B${i + 1}",
                auditoryCue = null,
                buttonAction = SpeakTextButtonAction(),
                isActive = i in activeIndices
            )
        }
    }

    private fun createEngine(scope: kotlinx.coroutines.CoroutineScope, delayMs: Long = 100L): ScannerEngine {
        val engine = ScannerEngine(
            scope = scope,
            featureGuard = featureGuard,
            feedbackProvider = feedbackProvider
        )
        engine.scanDelayMillis = delayMs
        return engine
    }

    // --- Idempotency ---

    @Test
    fun `startScanning with same params does not restart scan`() = runTest {
        val engine = createEngine(this)
        // Active buttons at (0,0) and (1,0) -> Global indices 0 and 7 (7x7 grid)
        val configs = createConfigs(listOf(0, 7))

        engine.startScanning(
            buttonConfigs = configs,
            startIndex = 0,
            pattern = "linear",
            columns = 2,
            rowNames = emptyList(),
            pageId = "page1"
        )
        runCurrent()
        advanceTimeBy(110) // Focus on 0

        assertEquals(0, engine.focusedButtonIndex.value)

        // Call startScanning again with identical parameters — should be idempotent
        engine.startScanning(
            buttonConfigs = configs,
            startIndex = 0,
            pattern = "linear",
            columns = 2,
            rowNames = emptyList(),
            pageId = "page1"
        )

        // Advance time — if the scan restarted, focus would stay/reset at 0 (after delay)
        // If it continues, it moves to 7.
        advanceTimeBy(100)
        val focus = engine.focusedButtonIndex.value

        assertEquals("Focus should advance to 7, not reset to 0", 7, focus)

        engine.stopScanning()
    }

    // --- stopScanning ---

    @Test
    fun `stopScanning clears focus indices`() = runTest {
        val engine = createEngine(this)
        val configs = createConfigs(listOf(0))

        engine.startScanning(
            buttonConfigs = configs,
            startIndex = 0,
            pattern = "linear",
            columns = 2,
            pageId = "page1"
        )
        advanceTimeBy(110)

        // Verify scanning is active
        assertEquals(0, engine.focusedButtonIndex.value)

        // Stop scanning
        engine.stopScanning()
        runCurrent()

        // Both focus indices must be null
        assertNull("focusedButtonIndex should be null after stop", engine.focusedButtonIndex.value)
        assertNull("focusedRowIndex should be null after stop", engine.focusedRowIndex.value)
    }

    // --- pauseScanning ---

    @Test
    fun `pauseScanning cancels job but keeps focus indices`() = runTest {
        val engine = createEngine(this)
        val configs = createConfigs(listOf(0))

        engine.startScanning(
            buttonConfigs = configs,
            startIndex = 0,
            pattern = "linear",
            columns = 2,
            pageId = "page1"
        )
        advanceTimeBy(110)

        val focusBefore = engine.focusedButtonIndex.value
        assertEquals(0, focusBefore)

        // Pause scanning
        engine.pauseScanning()
        runCurrent()

        // Focus should still be at the last known position (not cleared)
        assertEquals(
            "focusedButtonIndex should remain after pause",
            focusBefore,
            engine.focusedButtonIndex.value
        )

        // Scanning should not advance anymore
        advanceTimeBy(500)
        assertEquals(
            "focusedButtonIndex should not advance after pause",
            focusBefore,
            engine.focusedButtonIndex.value
        )
    }

    // --- Restart with different params ---

    @Test
    fun `startScanning with different pageId cancels old scan and starts new one`() = runTest {
        val engine = createEngine(this)
        val configs = createConfigs(listOf(0, 7))

        // Start first scan
        engine.startScanning(
            buttonConfigs = configs,
            startIndex = 0,
            pattern = "linear",
            columns = 2,
            pageId = "page1"
        )
        advanceTimeBy(110)
        assertEquals(0, engine.focusedButtonIndex.value)

        // Start a different scan (different pageId)
        engine.startScanning(
            buttonConfigs = configs,
            startIndex = 0,
            pattern = "linear",
            columns = 2,
            pageId = "page2"
        )
        advanceTimeBy(110)

        // The new scan should restart from index 0
        assertEquals("New scan should start from index 0", 0, engine.focusedButtonIndex.value)

        engine.stopScanning()
    }

    // --- Row-by-Row Scanning ---

    @Test
    fun `startScanning with row_by_row pattern focuses on rows`() = runTest {
        val engine = createEngine(this)
        // Buttons in Row 0 and Row 1 (Index 7 in 7x7)
        val configs = createConfigs(listOf(0, 7))

        // Start row scanning
        engine.startScanning(
            buttonConfigs = configs,
            startIndex = 0,
            pattern = "row_by_row",
            columns = 2,
            pageId = "page1"
        )
        advanceTimeBy(110)

        // Focus should be on Row 0
        assertEquals(0, engine.focusedRowIndex.value)
        assertNull(engine.focusedButtonIndex.value)

        // Advance to Row 1
        advanceTimeBy(100)
        assertEquals(1, engine.focusedRowIndex.value)

        engine.stopScanning()
    }

    @Test
    fun `selectCurrentRow starts button scanning within row`() = runTest {
        val engine = createEngine(this)
        // Row 0 has buttons at index 0 and 1
        val configs = createConfigs(listOf(0, 1))

        // Start row scanning
        engine.startScanning(
            buttonConfigs = configs,
            startIndex = 0,
            pattern = "row_by_row",
            columns = 2,
            pageId = "page1"
        )
        advanceTimeBy(110)

        // Focus is on Row 0
        assertEquals(0, engine.focusedRowIndex.value)

        // Select Row 0
        engine.selectCurrentRow()
        advanceTimeBy(110) // Initial delay(100) inside executeButtonScanInRow

        // Focus should now be on Button 0 (index 0)
        assertEquals(0, engine.focusedButtonIndex.value)

        // Advance to Button 1
        advanceTimeBy(100)
        assertEquals(1, engine.focusedButtonIndex.value)

        engine.stopScanning()
    }

    // --- clear ---

    @Test
    fun `clear cancels job and resets all indices`() = runTest {
        val engine = createEngine(this)
        val configs = createConfigs(listOf(0))

        engine.startScanning(
            buttonConfigs = configs,
            startIndex = 0,
            pattern = "linear",
            columns = 4,
            pageId = "page1"
        )
        advanceTimeBy(110)
        assertEquals(0, engine.focusedButtonIndex.value)

        engine.clear()
        runCurrent()

        assertNull(engine.focusedButtonIndex.value)
        assertNull(engine.focusedRowIndex.value)
    }
}

package com.andreas_kratzer.ghosttalk.core.scanning

import com.andreas_kratzer.ghosttalk.domain.settings.FeatureGuard
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.SpeakTextButtonAction
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RowByRowScanStrategyTest {

    private lateinit var strategy: RowByRowScanStrategy
    private lateinit var featureGuard: FeatureGuard
    private val focusedButtonIndex = MutableStateFlow<Int?>(null)
    private val focusedRowIndex = MutableStateFlow<Int?>(null)

    @Before
    fun setup() {
        strategy = RowByRowScanStrategy()
        featureGuard = mockk(relaxed = true)
        focusedButtonIndex.value = null
        focusedRowIndex.value = null
    }

    private fun createConfigs(count: Int): List<ButtonConfig> {
        return (0 until 36).map { i ->
            ButtonConfig(id = "$i", label = "B$i", isActive = i < count, auditoryCue = null, buttonAction = SpeakTextButtonAction())
        }
    }

    @Test
    fun `executeScan skips rows without active or visible buttons`() = runTest {
        // Col=2
        // Row 0: B0 (inactive), B1 (invisible) -> Row 0 skip
        // Row 1: B6 (active), B7 (active) -> Row 1 keep
        
        val configs = createConfigs(10)
        val inactiveB0 = configs[0].copy(isActive = false)
        val invisibleB1 = configs[1]
        
        val modifiedConfigs = configs.toMutableList()
        modifiedConfigs[0] = inactiveB0
        
        every { featureGuard.isButtonVisible(any()) } returns true
        every { featureGuard.isButtonVisible(invisibleB1) } returns false

        val cues = mutableListOf<String>()
        val job = launch {
            strategy.executeScan(
                buttonConfigs = modifiedConfigs,
                rows = 18,
                columns = 2,
                rowNames = listOf("R1", "R2"),
                startIndex = 0,
                focusedButtonIndex = focusedButtonIndex,
                focusedRowIndex = focusedRowIndex,
                onSpeakCue = { cues.add(it) },
                delayMillis = 1000,
                featureGuard = featureGuard
            )
        }

        advanceTimeBy(150) // Initial delay(100)
        // Focus should be on Row 1 because Row 0 is effectively empty (visible buttons 0,1 are inactive/invisible)
        assertEquals(1, focusedRowIndex.value)
        assertEquals("R2", cues.last())
        
        job.cancel()
    }

    @Test
    fun `executeScan uses default row name if not provided`() = runTest {
        val configs = createConfigs(1)
        every { featureGuard.isButtonVisible(any()) } returns true

        val cues = mutableListOf<String>()
        val job = launch {
            strategy.executeScan(
                buttonConfigs = configs,
                rows = 36,
                columns = 1,
                rowNames = emptyList(),
                startIndex = 0,
                focusedButtonIndex = focusedButtonIndex,
                focusedRowIndex = focusedRowIndex,
                onSpeakCue = { cues.add(it) },
                delayMillis = 1000,
                featureGuard = featureGuard
            )
        }

        advanceTimeBy(150)
        assertEquals("Zeile 1", cues.last())
        
        job.cancel()
    }

    @Test
    fun `executeButtonScanInRow scans only buttons in specified row`() = runTest {
        // Dynamic grid: Col=2, Row 1 (index 1) has Global Index 6, 7 (as Row 0 is 0-5)
        val configs = createConfigs(10) 
        every { featureGuard.isButtonVisible(any()) } returns true

        val cues = mutableListOf<String>()
        val job = launch {
            strategy.executeButtonScanInRow(
                buttonConfigs = configs,
                rows = 18,
                columns = 2,
                rowIndex = 1,
                focusedButtonIndex = focusedButtonIndex,
                onSpeakCue = { cues.add(it) },
                delayMillis = 1000,
                featureGuard = featureGuard
            )
        }

        advanceTimeBy(150) // Initial delay(100)
        assertEquals(6, focusedButtonIndex.value)
        assertEquals("B6", cues.last())

        advanceTimeBy(1000)
        assertEquals(7, focusedButtonIndex.value)
        assertEquals("B7", cues.last())

        advanceTimeBy(1000)
        assertEquals(6, focusedButtonIndex.value) // Should loop back to B6
        
        job.cancel()
    }

    @Test
    fun `executeButtonScanInRow handles empty row`() = runTest {
        val configs = listOf(
            ButtonConfig(id = "1", label = "B1", isActive = false, auditoryCue = null, buttonAction = SpeakTextButtonAction())
        )
        
        strategy.executeButtonScanInRow(
            buttonConfigs = configs,
            rows = 36,
            columns = 1,
            rowIndex = 0,
            focusedButtonIndex = focusedButtonIndex,
            onSpeakCue = { },
            delayMillis = 1000,
            featureGuard = featureGuard
        )
        
        assertNull(focusedButtonIndex.value)
    }
}
// dummy comment

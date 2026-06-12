package com.andreas_kratzer.ghosttalk.core.scanning

import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RowByRowScanStrategyTest {

    private lateinit var strategy: RowByRowScanStrategy
    private lateinit var featureGuard: FeatureGuardProxy
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
        return (0 until 49).map { i ->
            ButtonConfig(id = "$i", label = "B$i", spokenText = "B$i", isActive = i < count, auditoryCue = null, buttonAction = SpeakTextButtonAction())
        }
    }

    private fun createContext(
        scope: kotlinx.coroutines.CoroutineScope,
        buttonConfigs: List<ButtonConfig?>,
        rows: Int = 7,
        columns: Int = 2,
        rowNames: List<String> = emptyList(),
        startIndex: Int = 0,
        onSpeakCue: suspend (String) -> Unit = {},
        onPrefetchCue: suspend (String) -> Unit = {},
        onCycleCompleted: suspend () -> Unit = {},
        delayMillis: () -> Long = { 1000L }
    ) = ScanContext(
        scope = scope,
        buttonConfigs = buttonConfigs,
        rows = rows,
        columns = columns,
        rowNames = rowNames,
        startIndex = startIndex,
        focusedButtonIndex = focusedButtonIndex,
        focusedRowIndex = focusedRowIndex,
        onSpeakCue = onSpeakCue,
        onPrefetchCue = onPrefetchCue,
        onCycleCompleted = onCycleCompleted,
        delayMillis = delayMillis,
        featureGuard = featureGuard
    )

    @Test
    fun `executeScan skips rows without active or visible buttons`() = runTest {
        // MAX_GRID_SIZE is 7
        // Col=2
        // Row 0: B0 (inactive), B1 (invisible) -> Row 0 skip
        // Row 1: B7 (active), B8 (active) -> Row 1 keep
        
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
                createContext(
                    scope = this,
                    buttonConfigs = modifiedConfigs,
                    rows = 7,
                    columns = 2,
                    rowNames = listOf("R1", "R2"),
                    onSpeakCue = { cues.add(it) }
                )
            )
        }

        advanceTimeBy(150) // Initial delay(100)
        // Focus should be on Row 1 because Row 0 visible slots (0,1) are inactive/invisible
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
                createContext(
                    scope = this,
                    buttonConfigs = configs,
                    rows = 7,
                    columns = 1,
                    onSpeakCue = { cues.add(it) }
                )
            )
        }

        advanceTimeBy(150)
        assertEquals("Zeile 1", cues.last())
        
        job.cancel()
    }

    @Test
    fun `executeButtonScanInRow scans only buttons in specified row`() = runTest {
        // Grid: Col=2, Row 1 (index 1) has Global Index 7, 8 (as Row 0 is 0-6)
        val configs = createConfigs(10) 
        every { featureGuard.isButtonVisible(any()) } returns true

        val cues = mutableListOf<String>()
        val job = launch {
            strategy.executeButtonScanInRow(
                createContext(
                    scope = this,
                    buttonConfigs = configs,
                    rows = 7,
                    columns = 2,
                    onSpeakCue = { cues.add(it) }
                ),
                rowIndex = 1
            )
        }

        advanceTimeBy(150) // Initial delay(100)
        assertEquals(7, focusedButtonIndex.value)
        assertEquals("B7", cues.last())

        advanceTimeBy(1000)
        assertEquals(8, focusedButtonIndex.value)
        assertEquals("B8", cues.last())

        advanceTimeBy(1000)
        assertEquals(7, focusedButtonIndex.value) // Should loop back to B7
        
        job.cancel()
    }

    @Test
    fun `executeButtonScanInRow handles empty row`() = runTest {
        val configs = (0 until 49).map { i ->
             ButtonConfig(id = "$i", label = "B$i", spokenText = "B$i", isActive = false, auditoryCue = null, buttonAction = SpeakTextButtonAction())
        }
        
        strategy.executeButtonScanInRow(
            createContext(
                scope = this,
                buttonConfigs = configs,
                rows = 7,
                columns = 1
            ),
            rowIndex = 0
        )
        
        assertNull(focusedButtonIndex.value)
    }

    @Test
    fun `RowByRowScanStrategy regression - should have initial delay of 100ms to settle race conditions`() = runTest {
        val buttonConfigs = (0 until 49).map { 
            if (it == 0) ButtonConfig(id = "1", label = "Test", isActive = true) else null 
        }
        
        val focusedButtonIndex = MutableStateFlow<Int?>(null)
        val focusedRowIndex = MutableStateFlow<Int?>(null)
        val speakCount = MutableStateFlow(0)

        val customFeatureGuard = mockk<FeatureGuardProxy>(relaxed = true) {
            every { isButtonVisible(any()) } returns true
        }

        // Use backgroundScope from runTest to ensure cleanup
        backgroundScope.launch {
            strategy.executeScan(
                ScanContext(
                    scope = this,
                    buttonConfigs = buttonConfigs,
                    rows = 1,
                    columns = 1,
                    rowNames = emptyList(),
                    startIndex = 0,
                    focusedButtonIndex = focusedButtonIndex,
                    focusedRowIndex = focusedRowIndex,
                    onSpeakCue = { speakCount.value++ },
                    onPrefetchCue = {},
                    onCycleCompleted = {},
                    delayMillis = { 500 },
                    featureGuard = customFeatureGuard
                )
            )
        }

        // Initially 0
        runCurrent()
        assertEquals("Should not have spoken at T=0", 0, speakCount.value)
        
        advanceTimeBy(50)
        runCurrent()
        assertEquals("Should still not have spoken at T=50", 0, speakCount.value)

        advanceTimeBy(51)
        runCurrent()
        assertEquals("Should have spoken after 100ms settle delay (at T=101)", 1, speakCount.value)
    }

    @Test
    fun `resume at row skips static row buttons and starts at matching row step`() = runTest {
        val configs = MutableList<ButtonConfig?>(98) { null }
        configs[0] = ButtonConfig(id = "s0", label = "S0", isActive = true)
        configs[49] = ButtonConfig(id = "m0", label = "M0", isActive = true)
        configs[56] = ButtonConfig(id = "m7", label = "M7", isActive = true)
        every { featureGuard.isButtonVisible(any()) } returns true

        val cues = mutableListOf<String>()
        val job = launch {
            strategy.executeScan(
                ScanContext(
                    scope = this,
                    buttonConfigs = configs,
                    rows = 2,
                    columns = 2,
                    rowNames = listOf("Static Row", "Main Row 1", "Main Row 2"),
                    startIndex = 0,
                    focusedButtonIndex = focusedButtonIndex,
                    focusedRowIndex = focusedRowIndex,
                    onSpeakCue = { cues.add(it) },
                    onPrefetchCue = {},
                    onCycleCompleted = {},
                    delayMillis = { 1000L },
                    featureGuard = featureGuard,
                    hasStaticRow = true,
                    staticRowPattern = "row_by_row",
                    pagePattern = "row_by_row",
                    mainRows = 2,
                    mainColumns = 2,
                    resumePoint = ResumePoint.AtRow(1)
                )
            )
        }

        advanceTimeBy(150)
        assertEquals(1, focusedRowIndex.value)
        assertEquals("Main Row 1", cues.last())
        job.cancel()
    }

    @Test
    fun `resume at button with static row does not match row steps`() = runTest {
        val configs = MutableList<ButtonConfig?>(98) { null }
        configs[0] = ButtonConfig(id = "s0", label = "S0", isActive = true)
        configs[1] = ButtonConfig(id = "s1", label = "S1", isActive = true)
        configs[49] = ButtonConfig(id = "m0", label = "M0", isActive = true)
        every { featureGuard.isButtonVisible(any()) } returns true

        val cues = mutableListOf<String>()
        val job = launch {
            strategy.executeScan(
                ScanContext(
                    scope = this,
                    buttonConfigs = configs,
                    rows = 2,
                    columns = 2,
                    rowNames = listOf("Static Row", "Main Row 1", "Main Row 2"),
                    startIndex = 0,
                    focusedButtonIndex = focusedButtonIndex,
                    focusedRowIndex = focusedRowIndex,
                    onSpeakCue = { cues.add(it) },
                    onPrefetchCue = {},
                    onCycleCompleted = {},
                    delayMillis = { 1000L },
                    featureGuard = featureGuard,
                    hasStaticRow = true,
                    staticRowPattern = "linear",
                    pagePattern = "row_by_row",
                    mainRows = 2,
                    mainColumns = 2,
                    resumePoint = ResumePoint.AtButton(1)
                )
            )
        }

        advanceTimeBy(150)
        assertEquals(1, focusedButtonIndex.value)
        assertEquals("S1", cues.last())
        job.cancel()
    }

    @Test
    fun `resume falls back to first step when resume point not found`() = runTest {
        val configs = createConfigs(5)
        every { featureGuard.isButtonVisible(any()) } returns true

        val cues = mutableListOf<String>()
        val job = launch {
            strategy.executeScan(
                ScanContext(
                    scope = this,
                    buttonConfigs = configs,
                    rows = 2,
                    columns = 2,
                    rowNames = listOf("Main Row 1", "Main Row 2"),
                    startIndex = 0,
                    focusedButtonIndex = focusedButtonIndex,
                    focusedRowIndex = focusedRowIndex,
                    onSpeakCue = { cues.add(it) },
                    onPrefetchCue = {},
                    onCycleCompleted = {},
                    delayMillis = { 1000L },
                    featureGuard = featureGuard,
                    hasStaticRow = false,
                    pagePattern = "row_by_row",
                    mainRows = 2,
                    mainColumns = 2,
                    resumePoint = ResumePoint.AtButton(999)
                )
            )
        }

        advanceTimeBy(150)
        assertEquals(0, focusedRowIndex.value)
        assertEquals("Main Row 1", cues.last())
        job.cancel()
    }
}


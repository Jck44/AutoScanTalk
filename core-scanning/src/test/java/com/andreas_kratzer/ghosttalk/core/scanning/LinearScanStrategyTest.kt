package com.andreas_kratzer.ghosttalk.core.scanning

import com.andreas_kratzer.ghosttalk.core.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LinearScanStrategyTest {

    private lateinit var strategy: LinearScanStrategy
    private lateinit var featureGuard: FeatureGuardProxy
    private val focusedButtonIndex = MutableStateFlow<Int?>(null)
    private val focusedRowIndex = MutableStateFlow<Int?>(null)

    @Before
    fun setup() {
        strategy = LinearScanStrategy()
        featureGuard = mockk(relaxed = true)
        focusedButtonIndex.value = null
        focusedRowIndex.value = null
    }

    private fun createContext(
        scope: kotlinx.coroutines.CoroutineScope,
        buttonConfigs: List<ButtonConfig?>,
        rows: Int = 4,
        columns: Int = 4,
        rowNames: List<String> = emptyList(),
        startIndex: Int = 0,
        onSpeakCue: suspend (String) -> Unit = {},
        onPrefetchCue: suspend (String) -> Unit = {},
        onCycleCompleted: suspend () -> Unit = {},
        delayMillis: Long = 1000L
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
    fun `executeScan with empty configs sets focus to null`() = runTest {
        strategy.executeScan(
            createContext(
                scope = this,
                buttonConfigs = emptyList()
            )
        )
        assertNull(focusedButtonIndex.value)
    }

    @Test
    fun `executeScan skips inactive or invisible buttons`() = runTest {
        val configs = listOf(
            ButtonConfig(id = "1", label = "B1", isActive = false, auditoryCue = null, buttonAction = SpeakTextButtonAction()),
            ButtonConfig(id = "2", label = "B2", isActive = true, auditoryCue = null, buttonAction = SpeakTextButtonAction()),
            ButtonConfig(id = "3", label = "B3", isActive = true, auditoryCue = null, buttonAction = SpeakTextButtonAction())
        )
        
        every { featureGuard.isButtonVisible(configs[2]) } returns false
        every { featureGuard.isButtonVisible(configs[1]) } returns true

        val cues = mutableListOf<String>()
        
        // Use a background scope for the infinite scan loop
        val job = launch {
            strategy.executeScan(
                createContext(
                    scope = this,
                    buttonConfigs = configs,
                    rows = 1,
                    columns = 4,
                    onSpeakCue = { cues.add(it) }
                )
            )
        }

        advanceTimeBy(150) // Initial delay(100) + a bit
        assertEquals(1, focusedButtonIndex.value)
        assertEquals("B2", cues.last())

        advanceTimeBy(1000)
        // Should loop back to B2 as it's the only active/visible one
        assertEquals(1, focusedButtonIndex.value)
        assertEquals(2, cues.size)
        
        job.cancel()
    }

    @Test
    fun `executeScan with static row still scans main page buttons (regression)`() = runTest {
        // Regression fuer den Fall: statische Zeile aktiv + lineares Scanning.
        // Kombinierte Liste: Slots 0..48 = statische Zeile, ab Slot 49 = Hauptseite.
        every { featureGuard.isButtonVisible(any()) } returns true

        val combined = MutableList<ButtonConfig?>(49) { null }
        combined[0] = ButtonConfig(id = "s0", label = "Static0", isActive = true, buttonAction = SpeakTextButtonAction())
        val main = MutableList<ButtonConfig?>(49) { null }
        main[0] = ButtonConfig(id = "m0", label = "Main0", isActive = true, buttonAction = SpeakTextButtonAction()) // -> 49
        main[1] = ButtonConfig(id = "m1", label = "Main1", isActive = true, buttonAction = SpeakTextButtonAction()) // -> 50
        combined.addAll(main)

        val focused = mutableListOf<Int>()
        val collector = launch { focusedButtonIndex.collect { it?.let(focused::add) } }

        val job = launch {
            strategy.executeScan(
                ScanContext(
                    scope = this,
                    buttonConfigs = combined,
                    rows = 1 + 4,           // totalRows
                    columns = 4,            // totalCols
                    rowNames = emptyList(),
                    startIndex = 0,
                    focusedButtonIndex = focusedButtonIndex,
                    focusedRowIndex = focusedRowIndex,
                    onSpeakCue = {},
                    onPrefetchCue = {},
                    onCycleCompleted = {},
                    delayMillis = 1000L,
                    featureGuard = featureGuard,
                    hasStaticRow = true,
                    staticRowPattern = "linear",
                    pagePattern = "linear",
                    mainRows = 4,
                    mainColumns = 4
                )
            )
        }

        advanceTimeBy(4000)
        job.cancel()
        collector.cancel()

        assertTrue("Statische Zeile (Index 0) muss gescannt werden: $focused", focused.contains(0))
        assertTrue("Hauptseiten-Button (Index 49) muss gescannt werden: $focused", focused.contains(49))
        assertTrue("Hauptseiten-Button (Index 50) muss gescannt werden: $focused", focused.contains(50))
    }

    @Test
    fun `executeScan uses auditory cue text if available`() = runTest {
        val cue = AuditoryCue.TextToSpeechCue(text = "Custom Cue")
        val config = ButtonConfig(
            id = "1", 
            label = "Label", 
            isActive = true, 
            auditoryCue = cue,
            buttonAction = SpeakTextButtonAction()
        )
        
        every { featureGuard.isButtonVisible(config) } returns true

        val cues = mutableListOf<String>()
        val job = launch {
            strategy.executeScan(
                createContext(
                    scope = this,
                    buttonConfigs = listOf(config),
                    rows = 1,
                    columns = 4,
                    onSpeakCue = { cues.add(it) }
                )
            )
        }

        advanceTimeBy(150)
        assertEquals("Custom Cue", cues.last())
        
        job.cancel()
    }

    @Test
    fun `executeScan handles startIndex correctly`() = runTest {
        val configs = (0..3).map { i ->
            ButtonConfig(id = "$i", label = "B$i", isActive = true, auditoryCue = null, buttonAction = SpeakTextButtonAction())
        }
        every { featureGuard.isButtonVisible(any()) } returns true

        val job = launch {
            strategy.executeScan(
                createContext(
                    scope = this,
                    buttonConfigs = configs,
                    rows = 1,
                    columns = 4,
                    startIndex = 2
                )
            )
        }

        advanceTimeBy(150)
        assertEquals(2, focusedButtonIndex.value)
        
        advanceTimeBy(1000)
        assertEquals(3, focusedButtonIndex.value)

        advanceTimeBy(1000)
        assertEquals(0, focusedButtonIndex.value) // Should wrap around to 0
        
        job.cancel()
    }

    @Test
    fun `executeScan triggers onCycleCompleted after one full cycle`() = runTest {
        val config = ButtonConfig(id = "1", label = "B1", isActive = true, auditoryCue = null, buttonAction = SpeakTextButtonAction())
        every { featureGuard.isButtonVisible(config) } returns true

        var cycleCount = 0
        val job = launch {
            strategy.executeScan(
                createContext(
                    scope = this,
                    buttonConfigs = listOf(config),
                    rows = 1,
                    columns = 1,
                    onCycleCompleted = { cycleCount++ },
                    delayMillis = 100
                )
            )
        }

        advanceTimeBy(250) // delay(100) + strategy delay + settling
        assertEquals(1, cycleCount)

        advanceTimeBy(100) // next iteration delay(100)
        assertEquals(2, cycleCount)

        job.cancel()
    }
}

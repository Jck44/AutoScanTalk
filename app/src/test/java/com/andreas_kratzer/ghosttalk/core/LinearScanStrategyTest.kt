package com.andreas_kratzer.ghosttalk.core

import com.andreas_kratzer.ghosttalk.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.SpeakTextButtonAction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LinearScanStrategyTest {

    private val strategy = LinearScanStrategy()

    private fun btn(id: String, label: String, active: Boolean = true, cueText: String? = null): ButtonConfig {
        return ButtonConfig(
            id = id,
            label = label,
            auditoryCue = cueText?.let { AuditoryCue.TextToSpeechCue(it) },
            buttonAction = SpeakTextButtonAction(label),
            isActive = active
        )
    }

    @Test
    fun `scans active buttons in order`() = runTest {
        val focusedButton = MutableStateFlow<Int?>(null)
        val focusedRow = MutableStateFlow<Int?>(null)
        val spokenCues = mutableListOf<String>()

        val configs = listOf(btn("b1", "A"), btn("b2", "B"), btn("b3", "C"))

        val job = launch {
            strategy.executeScan(
                buttonConfigs = configs,
                columns = 3,
                rowNames = emptyList(),
                startIndex = 0,
                focusedButtonIndex = focusedButton,
                focusedRowIndex = focusedRow,
                onSpeakCue = { spokenCues.add(it) },
                delayMillis = 100
            )
        }

        // First button
        advanceTimeBy(1)
        assertEquals(0, focusedButton.value)
        assertEquals("A", spokenCues.last())
        assertNull(focusedRow.value) // Linear clears row focus

        // Second button
        advanceTimeBy(100)
        assertEquals(1, focusedButton.value)
        assertEquals("B", spokenCues.last())

        // Third button
        advanceTimeBy(100)
        assertEquals(2, focusedButton.value)
        assertEquals("C", spokenCues.last())

        // Wraps back to first (second pass)
        advanceTimeBy(100)
        assertEquals(0, focusedButton.value)
        assertEquals(4, spokenCues.size) // A, B, C, A

        job.cancel()
    }

    @Test
    fun `skips inactive and null buttons`() = runTest {
        val focusedButton = MutableStateFlow<Int?>(null)
        val focusedRow = MutableStateFlow<Int?>(null)
        val spokenCues = mutableListOf<String>()

        val configs: List<ButtonConfig?> = listOf(
            btn("b1", "Active1"),
            null,
            btn("b3", "Inactive", active = false),
            btn("b4", "Active2")
        )

        val job = launch {
            strategy.executeScan(
                buttonConfigs = configs,
                columns = 4,
                rowNames = emptyList(),
                startIndex = 0,
                focusedButtonIndex = focusedButton,
                focusedRowIndex = focusedRow,
                onSpeakCue = { spokenCues.add(it) },
                delayMillis = 100
            )
        }

        advanceTimeBy(1)
        assertEquals(0, focusedButton.value) // index 0 = Active1

        advanceTimeBy(100)
        assertEquals(3, focusedButton.value) // index 3 = Active2, skipped null & inactive

        assertEquals(listOf("Active1", "Active2"), spokenCues)

        job.cancel()
    }

    @Test
    fun `uses auditory cue text when available`() = runTest {
        val focusedButton = MutableStateFlow<Int?>(null)
        val focusedRow = MutableStateFlow<Int?>(null)
        val spokenCues = mutableListOf<String>()

        val configs = listOf(
            btn("b1", "Label A", cueText = "Cue A"),
            btn("b2", "Label B") // no cue, should use label
        )

        val job = launch {
            strategy.executeScan(
                buttonConfigs = configs,
                columns = 2,
                rowNames = emptyList(),
                startIndex = 0,
                focusedButtonIndex = focusedButton,
                focusedRowIndex = focusedRow,
                onSpeakCue = { spokenCues.add(it) },
                delayMillis = 100
            )
        }

        advanceTimeBy(1)
        assertEquals("Cue A", spokenCues[0]) // Uses cue text

        advanceTimeBy(100)
        assertEquals("Label B", spokenCues[1]) // Falls back to label

        job.cancel()
    }

    @Test
    fun `respects startIndex to begin scanning from a specific button`() = runTest {
        val focusedButton = MutableStateFlow<Int?>(null)
        val focusedRow = MutableStateFlow<Int?>(null)
        val spokenCues = mutableListOf<String>()

        val configs = listOf(btn("b1", "A"), btn("b2", "B"), btn("b3", "C"))

        val job = launch {
            strategy.executeScan(
                buttonConfigs = configs,
                columns = 3,
                rowNames = emptyList(),
                startIndex = 1, // Start from button B
                focusedButtonIndex = focusedButton,
                focusedRowIndex = focusedRow,
                onSpeakCue = { spokenCues.add(it) },
                delayMillis = 100
            )
        }

        advanceTimeBy(1)
        assertEquals(1, focusedButton.value) // Starts at B, not A

        advanceTimeBy(100)
        assertEquals(2, focusedButton.value) // Then C

        job.cancel()
    }

    @Test
    fun `returns immediately when no active buttons`() = runTest {
        val focusedButton = MutableStateFlow<Int?>(null)
        val focusedRow = MutableStateFlow<Int?>(null)

        val configs: List<ButtonConfig?> = listOf(null, null)

        strategy.executeScan(
            buttonConfigs = configs,
            columns = 2,
            rowNames = emptyList(),
            startIndex = 0,
            focusedButtonIndex = focusedButton,
            focusedRowIndex = focusedRow,
            onSpeakCue = { },
            delayMillis = 100
        )

        assertNull(focusedButton.value)
    }
}

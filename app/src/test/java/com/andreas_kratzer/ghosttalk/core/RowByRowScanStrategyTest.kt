

import com.andreas_kratzer.ghosttalk.core.scanning.RowByRowScanStrategy
import com.andreas_kratzer.ghosttalk.domain.FeatureGuard
import com.andreas_kratzer.ghosttalk.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.SmartPredictionButtonAction
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

    private val strategy = RowByRowScanStrategy()
    private val featureGuard = mockk<FeatureGuard>()

    @Before
    fun setup() {
        // Default: everything visible
        every { featureGuard.isButtonVisible(any()) } returns true
        every { featureGuard.isActionEnabled(any()) } returns true
    }

    private fun btn(id: String, label: String, active: Boolean = true, cueText: String? = null): ButtonConfig {
        return ButtonConfig(
            id = id,
            label = label,
            auditoryCue = cueText?.let { AuditoryCue.TextToSpeechCue(it) },
            buttonAction = SpeakTextButtonAction(),
            isActive = active
        )
    }

    // --- executeScan (row scanning) ---

    @Test
    fun `executeScan iterates through active rows`() = runTest {
        val focusedButton = MutableStateFlow<Int?>(null)
        val focusedRow = MutableStateFlow<Int?>(null)
        val spokenCues = mutableListOf<String>()

        // 2 rows x 2 columns = 4 buttons, all active
        val configs = listOf(btn("b1", "A"), btn("b2", "B"), btn("b3", "C"), btn("b4", "D"))

        val job = launch {
            strategy.executeScan(
                buttonConfigs = configs,
                columns = 2,
                rowNames = listOf("Row 1", "Row 2"),
                startIndex = 0,
                focusedButtonIndex = focusedButton,
                focusedRowIndex = focusedRow,
                onSpeakCue = { cue: String -> spokenCues.add(cue) },
                delayMillis = 100,
                featureGuard = featureGuard
            )
        }

        // First row
        advanceTimeBy(101)
        assertEquals(0, focusedRow.value)
        assertNull(focusedButton.value) // No button focused in row scanning
        assertEquals("Row 1", spokenCues.last())

        // Second row
        advanceTimeBy(100)
        assertEquals(1, focusedRow.value)
        assertEquals("Row 2", spokenCues.last())

        // Wraps back to first row
        advanceTimeBy(100)
        assertEquals(0, focusedRow.value)
        assertEquals(3, spokenCues.size)

        job.cancel()
    }

    @Test
    fun `executeScan skips rows with only inactive or null buttons`() = runTest {
        val focusedButton = MutableStateFlow<Int?>(null)
        val focusedRow = MutableStateFlow<Int?>(null)
        val spokenCues = mutableListOf<String>()

        // 3 rows x 2 columns: row 0 active, row 1 all null/inactive, row 2 active
        val configs: List<ButtonConfig?> = listOf(
            btn("b1", "A"), btn("b2", "B"),               // row 0 - active
            null, btn("b4", "D", active = false),          // row 1 - inactive/null
            btn("b5", "E"), btn("b6", "F")                 // row 2 - active
        )

        val job = launch {
            strategy.executeScan(
                buttonConfigs = configs,
                columns = 2,
                rowNames = listOf("R1", "R2", "R3"),
                startIndex = 0,
                focusedButtonIndex = focusedButton,
                focusedRowIndex = focusedRow,
                onSpeakCue = { cue: String -> spokenCues.add(cue) },
                delayMillis = 100,
                featureGuard = featureGuard
            )
        }

        advanceTimeBy(101)
        assertEquals(0, focusedRow.value) // Row 0

        advanceTimeBy(100)
        assertEquals(2, focusedRow.value) // Skipped row 1, jumped to row 2

        assertEquals(listOf("R1", "R3"), spokenCues)

        job.cancel()
    }

    @Test
    fun `executeScan uses default row name when no name provided`() = runTest {
        val focusedButton = MutableStateFlow<Int?>(null)
        val focusedRow = MutableStateFlow<Int?>(null)
        val spokenCues = mutableListOf<String>()

        val configs = listOf(btn("b1", "A"), btn("b2", "B"))

        val job = launch {
            strategy.executeScan(
                buttonConfigs = configs,
                columns = 2,
                rowNames = emptyList(), // No names
                startIndex = 0,
                focusedButtonIndex = focusedButton,
                focusedRowIndex = focusedRow,
                onSpeakCue = { cue: String -> spokenCues.add(cue) },
                delayMillis = 100,
                featureGuard = featureGuard
            )
        }

        advanceTimeBy(101)
        assertEquals("Zeile 1", spokenCues[0]) // Falls back to "Zeile 1"

        job.cancel()
    }

    @Test
    fun `executeScan returns immediately when no active rows`() = runTest {
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
            delayMillis = 100,
            featureGuard = featureGuard
        )

        assertNull(focusedRow.value)
    }

    // --- executeButtonScanInRow ---

    @Test
    fun `executeButtonScanInRow scans active buttons within row`() = runTest {
        val focusedButton = MutableStateFlow<Int?>(null)
        MutableStateFlow<Int?>(0)
        val spokenCues = mutableListOf<String>()

        // 2 rows x 2 columns
        val configs = listOf(btn("b1", "A"), btn("b2", "B"), btn("b3", "C"), btn("b4", "D"))

        val job = launch {
            strategy.executeButtonScanInRow(
                buttonConfigs = configs,
                columns = 2,
                rowIndex = 0, // Scan row 0
                focusedButtonIndex = focusedButton,
                onSpeakCue = { cue: String -> spokenCues.add(cue) },
                delayMillis = 100,
                featureGuard = featureGuard
            )
        }

        // First button in row 0 (global index 0)
        advanceTimeBy(101)
        assertEquals(0, focusedButton.value)
        assertEquals("A", spokenCues.last())

        // Second button in row 0 (global index 1)
        advanceTimeBy(100)
        assertEquals(1, focusedButton.value)
        assertEquals("B", spokenCues.last())

        // Should NOT scan row 1 buttons; wraps within row 0
        advanceTimeBy(100)
        assertEquals(0, focusedButton.value)
        assertEquals(3, spokenCues.size) // A, B, A

        job.cancel()
    }

    @Test
    fun `executeButtonScanInRow skips inactive buttons in row`() = runTest {
        val focusedButton = MutableStateFlow<Int?>(null)
        val spokenCues = mutableListOf<String>()

        // 2 rows x 3 columns
        val configs: List<ButtonConfig?> = listOf(
            btn("b1", "A"), btn("b2", "B"), btn("b3", "C"),       // row 0
            null, btn("b5", "E"), btn("b6", "F", active = false)   // row 1: null, active, inactive
        )

        val job = launch {
            strategy.executeButtonScanInRow(
                buttonConfigs = configs,
                columns = 3,
                rowIndex = 1,
                focusedButtonIndex = focusedButton,
                onSpeakCue = { cue: String -> spokenCues.add(cue) },
                delayMillis = 100,
                featureGuard = featureGuard
            )
        }

        advanceTimeBy(101)
        assertEquals(4, focusedButton.value) // Only button E (global index 4) is active
        assertEquals("E", spokenCues.last())

        // Wraps back to same button
        advanceTimeBy(100)
        assertEquals(4, focusedButton.value)

        job.cancel()
    }

    @Test
    fun `executeButtonScanInRow uses auditory cue when available`() = runTest {
        val focusedButton = MutableStateFlow<Int?>(null)
        MutableStateFlow<Int?>(0)
        val spokenCues = mutableListOf<String>()

        val configs = listOf(btn("b1", "Label", cueText = "Custom Cue"))

        val job = launch {
            strategy.executeButtonScanInRow(
                buttonConfigs = configs,
                columns = 1,
                rowIndex = 0,
                focusedButtonIndex = focusedButton,
                onSpeakCue = { cue: String -> spokenCues.add(cue) },
                delayMillis = 100,
                featureGuard = featureGuard
            )
        }

        advanceTimeBy(101)
        assertEquals("Custom Cue", spokenCues[0])

        job.cancel()
    }

    @Test
    fun `executeButtonScanInRow returns immediately when no active buttons in row`() = runTest {
        val focusedButton = MutableStateFlow<Int?>(null)
        MutableStateFlow<Int?>(0)

        val configs: List<ButtonConfig?> = listOf(null, null)

        strategy.executeButtonScanInRow(
            buttonConfigs = configs,
            columns = 2,
            rowIndex = 0,
            focusedButtonIndex = focusedButton,
            onSpeakCue = { },
            delayMillis = 100,
            featureGuard = featureGuard
        )

        assertNull(focusedButton.value)
    }

    @Test
    fun `executeScan skips rows that only contain disabled smart buttons`() = runTest {
        val focusedRow = MutableStateFlow<Int?>(null)
        val spokenCues = mutableListOf<String>()

        // Row 0: Regular
        // Row 1: Only Smart
        val configs = listOf(
            btn("b1", "A"), btn("b2", "B"),
            ButtonConfig(id="s1", label="S1", auditoryCue=null, buttonAction= SmartPredictionButtonAction(1), isActive=true),
            ButtonConfig(id="s2", label="S2", auditoryCue=null, buttonAction= SmartPredictionButtonAction(2), isActive=true)
        )

        val job = launch {
            every { featureGuard.isButtonVisible(match { it.buttonAction is SmartPredictionButtonAction }) } returns false
            strategy.executeScan(
                buttonConfigs = configs,
                columns = 2,
                rowNames = listOf("R1", "R2"),
                startIndex = 0,
                focusedButtonIndex = MutableStateFlow<Int?>(null),
                focusedRowIndex = focusedRow,
                onSpeakCue = { cue: String -> spokenCues.add(cue) },
                delayMillis = 100,
                featureGuard = featureGuard
            )
        }

        advanceTimeBy(101)
        assertEquals(0, focusedRow.value) // R1

        advanceTimeBy(100)
        assertEquals(0, focusedRow.value) // Still R1 because R2 is empty (only disabled smart buttons)

        assertEquals(listOf("R1", "R1"), spokenCues)

        job.cancel()
    }

    @Test
    fun `executeButtonScanInRow skips disabled smart buttons`() = runTest {
        val focusedButton = MutableStateFlow<Int?>(null)
        val spokenCues = mutableListOf<String>()

        val configs = listOf(
            btn("b1", "A"),
            ButtonConfig(id="s1", label="S1", auditoryCue=null, buttonAction= SmartPredictionButtonAction(1), isActive=true),
            btn("b3", "C")
        )

        val job = launch {
            every { featureGuard.isButtonVisible(match { it.buttonAction is SmartPredictionButtonAction }) } returns false
            strategy.executeButtonScanInRow(
                buttonConfigs = configs,
                columns = 3,
                rowIndex = 0,
                focusedButtonIndex = focusedButton,
                onSpeakCue = { cue: String -> spokenCues.add(cue) },
                delayMillis = 100,
                featureGuard = featureGuard
            )
        }

        advanceTimeBy(101)
        assertEquals(0, focusedButton.value) // A

        advanceTimeBy(100)
        assertEquals(2, focusedButton.value) // C (skipped S1 at index 1)

        assertEquals(listOf("A", "C"), spokenCues)

        job.cancel()
    }
}

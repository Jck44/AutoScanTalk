package com.andreas_kratzer.ghosttalk.core.domain.actions

import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.model.ActionLogEntry
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.andreas_kratzer.ghosttalk.core.util.TestLogger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@ExperimentalCoroutinesApi
class ActionLogUseCaseTest {

    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val logger: Logger = TestLogger()
    private val useCase = ActionLogUseCase(settingsRepository, logger)

    @Test
    fun `formatAndAddEntry adds entry with current timestamp`() = runTest {
        every { settingsRepository.persistActionLogs } returns false

        val result = useCase.formatAndAddEntry("Button pressed", emptyList(), 100)

        assertEquals(1, result.size)
        assertEquals("Button pressed", result[0].message)
        assertTrue(result[0].timestamp > 0)
    }

    @Test
    fun `formatAndAddEntry limits list to given limit`() = runTest {
        every { settingsRepository.persistActionLogs } returns false
        val existingLogs = (1..10).map { ActionLogEntry("Entry $it", System.currentTimeMillis()) }

        val result = useCase.formatAndAddEntry("New Entry", existingLogs, 5)

        assertEquals(5, result.size)
        assertEquals("New Entry", result[0].message)
    }

    @Test
    fun `formatAndAddEntry caps at global MAX_LOG_SIZE even if larger limit requested`() = runTest {
        every { settingsRepository.persistActionLogs } returns false
        val existingLogs = (1..30).map { ActionLogEntry("Entry $it", System.currentTimeMillis()) }

        val result = useCase.formatAndAddEntry("New Entry", existingLogs, 100)

        assertEquals(20, result.size) // Capped at MAX_LOG_SIZE (20)
    }

    @Test
    fun `formatAndAddEntry persists when enabled`() = runTest {
        every { settingsRepository.persistActionLogs } returns true

        useCase.formatAndAddEntry("Persisted", emptyList(), 100)

        verify { settingsRepository.actionLogsStorage = any() }
    }

    @Test
    fun `loadSavedLogs returns empty when persistence disabled`() = runTest {
        every { settingsRepository.persistActionLogs } returns false

        val result = useCase.loadSavedLogs()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `loadSavedLogs handles JSON migration from old format`() = runTest {
        every { settingsRepository.persistActionLogs } returns true
        // Old format was List<String>
        every { settingsRepository.actionLogsStorage } returns """["Old message 1","Old message 2"]"""

        val result = useCase.loadSavedLogEntries()

        assertEquals(2, result.size)
        assertEquals("Old message 1", result[0].message)
        assertEquals("Old message 2", result[1].message)
    }

    @Test
    fun `loadSavedLogs returns current entries`() = runTest {
        every { settingsRepository.persistActionLogs } returns true
        every { settingsRepository.actionLogsStorage } returns """[{"message":"New message","timestamp":123456789}]"""

        val result = useCase.loadSavedLogEntries()

        assertEquals(1, result.size)
        assertEquals("New message", result[0].message)
        assertEquals(123456789L, result[0].timestamp)
    }

    @Test
    fun `formatEntryForDisplay matches expected format`() {
        val entry = ActionLogEntry("Hello", System.currentTimeMillis())
        val formatted = useCase.formatEntryForDisplay(entry)
        
        // Matches [HH:mm:ss] Hello or [dd.MM.yyyy HH:mm:ss] Hello
        assertTrue(formatted.matches(Regex("\\[(\\d{2}:\\d{2}:\\d{2}|\\d{2}\\.\\d{2}\\.\\d{4} \\d{2}:\\d{2}:\\d{2})] Hello")))
    }

    @Test
    fun `formatAndAddEntry stores ButtonAction when provided`() = runTest {
        every { settingsRepository.persistActionLogs } returns true
        val action = SpeakTextButtonAction()
        
        val result = useCase.formatAndAddEntry("Button pressed", emptyList(), 100, action)
        
        assertEquals(1, result.size)
        assertEquals(action, result[0].action)
        verify { settingsRepository.actionLogsStorage = any() }
    }

    @Test
    fun `loadSavedLogEntries decodes ButtonAction correctly`() = runTest {
        every { settingsRepository.persistActionLogs } returns true
        every { settingsRepository.actionLogsStorage } returns """[{"message":"Speak","timestamp":123456,"action":{"type":"SpeakTextButtonAction","version":1}}]"""

        val result = useCase.loadSavedLogEntries()

        assertEquals(1, result.size)
        assertTrue(result[0].action is SpeakTextButtonAction)
    }

    @Test
    fun `clearLogs resets storage when persistence enabled`() = runTest {
        every { settingsRepository.persistActionLogs } returns true

        useCase.clearLogs()

        verify { settingsRepository.actionLogsStorage = "[]" }
    }
}

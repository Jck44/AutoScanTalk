package com.andreas_kratzer.ghosttalk.domain.actions

import com.andreas_kratzer.ghosttalk.domain.actions.ActionLogUseCase

import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.andreas_kratzer.ghosttalk.core.util.TestLogger
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionLogUseCaseTest {

    private val settingsRepository: SettingsRepository = mockk(relaxed = true)
    private val logger: Logger = TestLogger()
    private val useCase = ActionLogUseCase(settingsRepository, logger)

    @Test
    fun `formatAndAddEntry adds timestamped entry to front`() {
        every { settingsRepository.persistActionLogs } returns false

        val result = useCase.formatAndAddEntry("Button pressed", emptyList())

        assertEquals(1, result.size)
        assertTrue(result[0].contains("Button pressed"))
        assertTrue(result[0].matches(Regex("\\[\\d{2}:\\d{2}:\\d{2}] Button pressed")))
    }

    @Test
    fun `formatAndAddEntry limits list to 100 entries`() {
        every { settingsRepository.persistActionLogs } returns false
        val existingLogs = (1..100).map { "[$it] Entry $it" }

        val result = useCase.formatAndAddEntry("New Entry", existingLogs)

        assertEquals(100, result.size)
        assertTrue(result[0].contains("New Entry"))
    }

    @Test
    fun `formatAndAddEntry persists when enabled`() {
        every { settingsRepository.persistActionLogs } returns true

        useCase.formatAndAddEntry("Persisted", emptyList())

        verify { settingsRepository.actionLogsStorage = any() }
    }

    @Test
    fun `loadSavedLogs returns empty when persistence disabled`() {
        every { settingsRepository.persistActionLogs } returns false

        val result = useCase.loadSavedLogs()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `loadSavedLogs returns saved entries`() {
        every { settingsRepository.persistActionLogs } returns true
        every { settingsRepository.actionLogsStorage } returns """["entry1","entry2"]"""

        val result = useCase.loadSavedLogs()

        assertEquals(2, result.size)
        assertEquals("entry1", result[0])
    }

    @Test
    fun `loadSavedLogs handles invalid JSON gracefully`() {
        every { settingsRepository.persistActionLogs } returns true
        every { settingsRepository.actionLogsStorage } returns "not valid json"

        val result = useCase.loadSavedLogs()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `clearLogs resets storage when persistence enabled`() {
        every { settingsRepository.persistActionLogs } returns true

        useCase.clearLogs()

        verify { settingsRepository.actionLogsStorage = "[]" }
    }
}

package com.andreas_kratzer.ghosttalk.data

import com.andreas_kratzer.ghosttalk.model.PageTemplate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateRepositoryTest {

    private val templateDao = mockk<TemplateDao>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val repository = TemplateRepository(templateDao, settingsRepository)

    @Test
    fun `ensureBuiltInTemplates creates both templates if missing`() = runTest {
        coEvery { templateDao.getTemplateById(any()) } returns null

        repository.ensureBuiltInTemplates()

        val templates = mutableListOf<PageTemplate>()
        coVerify(exactly = 2) { templateDao.insertTemplate(capture(templates)) }

        val frequentTemplate = templates.find { it.id == "builtin_frequent" }!!
        val yesNoTemplate = templates.find { it.id == "builtin_yesno" }!!

        assertEquals("Vorlage: Häufigste Aktionen", frequentTemplate.name)
        assertTrue(frequentTemplate.isBuiltIn)
        assertEquals(16, frequentTemplate.buttonConfigs.size)
        // Ensure slots are populated
        assertTrue(frequentTemplate.buttonConfigs.all { it != null })

        assertEquals("Vorlage: Ja / Nein", yesNoTemplate.name)
        assertTrue(yesNoTemplate.isBuiltIn)
        assertEquals(16, yesNoTemplate.buttonConfigs.size)
        // Ensure only specific slots are populated (0, 1, 15)
        assertTrue(yesNoTemplate.buttonConfigs[0] != null) // Ja
        assertTrue(yesNoTemplate.buttonConfigs[1] != null) // Nein
        assertTrue(yesNoTemplate.buttonConfigs[15] != null) // Zurück
        assertTrue(yesNoTemplate.buttonConfigs.filterNotNull().size == 3)
    }

    @Test
    fun `ensureBuiltInTemplates skips creation if already exists`() = runTest {
        coEvery { templateDao.getTemplateById("builtin_frequent") } returns mockk()
        coEvery { templateDao.getTemplateById("builtin_yesno") } returns mockk()

        repository.ensureBuiltInTemplates()

        coVerify(exactly = 0) { templateDao.insertTemplate(any()) }
    }

    @Test
    fun `delete ignores builtin templates`() = runTest {
        val builtIn = PageTemplate(id = "builtin_1", name = "Test", rows = 1, columns = 1, buttonConfigs = emptyList(), isBuiltIn = true)
        val custom = PageTemplate(id = "custom_1", name = "Test", rows = 1, columns = 1, buttonConfigs = emptyList(), isBuiltIn = false)

        repository.delete(builtIn)
        coVerify(exactly = 0) { templateDao.deleteTemplate(builtIn) }

        repository.delete(custom)
        coVerify(exactly = 1) { templateDao.deleteTemplate(custom) }
    }

    @Test
    fun `get functions delegate to dao`() = runTest {
        io.mockk.every { templateDao.getAllTemplatesFlow() } returns kotlinx.coroutines.flow.flowOf(emptyList())
        
        repository.getAllTemplates()
        io.mockk.verify(exactly = 1) { templateDao.getAllTemplatesFlow() }

        repository.getById("test")
        coVerify(exactly = 1) { templateDao.getTemplateById("test") }
    }
}

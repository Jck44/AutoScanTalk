package com.andreas_kratzer.ghosttalk.core.database

import com.andreas_kratzer.ghosttalk.core.data.impl.TemplateRepositoryImpl
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import com.andreas_kratzer.ghosttalk.core.settings.DatabaseSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import io.mockk.every
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TemplateRepositoryTest {

    private val mockTemplateDao = mockk<TemplateDao>(relaxed = true)
    private val mockSettings = mockk<DatabaseSettings>(relaxed = true)
    private lateinit var templateRepository: TemplateRepositoryImpl

    @Before
    fun setup() {
        templateRepository = TemplateRepositoryImpl(mockTemplateDao, mockSettings)
    }

    @Test
    fun `ensureBuiltInTemplates creates both templates if missing`() = runTest {
        coEvery { mockTemplateDao.getTemplateById(any()) } returns null

        templateRepository.ensureBuiltInTemplates()

        val templates = mutableListOf<PageTemplate>()
        coVerify(exactly = 2) { mockTemplateDao.insertTemplate(capture(templates)) }

        val frequentTemplate = templates.find { it.id == "builtin_frequent" }!!
        val yesNoTemplate = templates.find { it.id == "builtin_yesno" }!!

        assertEquals("Vorlage: Häufigste Aktionen", frequentTemplate.name)
        assertTrue(frequentTemplate.isBuiltIn)
        assertEquals(16, frequentTemplate.buttonConfigs.size)
        assertTrue(frequentTemplate.buttonConfigs.all { it != null })

        assertEquals("Vorlage: Ja / Nein", yesNoTemplate.name)
        assertTrue(yesNoTemplate.isBuiltIn)
        assertEquals(16, yesNoTemplate.buttonConfigs.size)
        assertTrue(yesNoTemplate.buttonConfigs[0] != null) // Ja
        assertTrue(yesNoTemplate.buttonConfigs[1] != null) // Nein
        assertTrue(yesNoTemplate.buttonConfigs[15] != null) // Zurück
        assertTrue(yesNoTemplate.buttonConfigs.filterNotNull().size == 3)
    }

    @Test
    fun `ensureBuiltInTemplates skips creation if already exists`() = runTest {
        coEvery { mockTemplateDao.getTemplateById("builtin_frequent") } returns mockk()
        coEvery { mockTemplateDao.getTemplateById("builtin_yesno") } returns mockk()

        templateRepository.ensureBuiltInTemplates()

        coVerify(exactly = 0) { mockTemplateDao.insertTemplate(any()) }
    }

    @Test
    fun `delete ignores builtin templates`() = runTest {
        val builtIn = PageTemplate(id = "builtin_1", name = "Test", rows = 1, columns = 1, buttonConfigs = emptyList(), isBuiltIn = true)
        val custom = PageTemplate(id = "custom_1", name = "Test", rows = 1, columns = 1, buttonConfigs = emptyList(), isBuiltIn = false)

        templateRepository.delete(builtIn)
        coVerify(exactly = 0) { mockTemplateDao.deleteTemplate(builtIn) }

        templateRepository.delete(custom)
        coVerify(exactly = 1) { mockTemplateDao.deleteTemplate(custom) }
    }

    @Test
    fun `get functions delegate to dao`() = runTest {
        every { mockTemplateDao.getAllTemplatesFlow() } returns flowOf(emptyList())
        
        templateRepository.getAllTemplates()
        verify(exactly = 1) { mockTemplateDao.getAllTemplatesFlow() }

        templateRepository.getById("test")
        coVerify(exactly = 1) { mockTemplateDao.getTemplateById("test") }
    }
}

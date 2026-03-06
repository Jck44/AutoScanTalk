package com.andreas_kratzer.ghosttalk.domain.pages

import com.andreas_kratzer.ghosttalk.data.BookRepository
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.data.TemplateRepository
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.model.PageTemplate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreatePageUseCaseTest {

    private lateinit var pageRepository: PageRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var templateRepository: TemplateRepository
    private lateinit var bookRepository: BookRepository
    private lateinit var createPageUseCase: CreatePageUseCase

    @Before
    fun setup() {
        pageRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        templateRepository = mockk(relaxed = true)
        bookRepository = mockk(relaxed = true)
        createPageUseCase = CreatePageUseCase(pageRepository, settingsRepository, templateRepository, bookRepository)
    }

    @Test
    fun `execute with template creates page with template configuration`() = runTest {
        // Given
        // Template with 4 configs but it must have 36 for validation if we strict check PageTemplate as well
        // However, CreatePageUseCase calls GridUtils.adjustButtonConfigs which pads it
        val template = PageTemplate(
            id = "template1",
            name = "Test Template",
            rows = 2,
            columns = 2,
            buttonConfigs = listOf(null, null, null, null)
        )
        coEvery { templateRepository.getById("template1") } returns template

        // When
        createPageUseCase.execute("New Page", 4, 4, "book1", emptyList(), "template1")

        // Then
        coVerify {
            pageRepository.insertPage(match {
                it.name == "New Page" &&
                it.rows == 2 &&
                it.columns == 2 &&
                it.buttonConfigs.size == 36
            })
        }
        coVerify { bookRepository.updateLastModified("book1") }
    }

    @Test
    fun `execute without template creates page with default home button`() = runTest {
        // Given
        every { settingsRepository.defaultStartPageId } returns "home"

        // When
        createPageUseCase.execute("New Page", 2, 2, "book1", emptyList(), null)

        // Then
        coVerify {
            pageRepository.insertPage(match {
                it.name == "New Page" &&
                it.rows == 2 &&
                it.columns == 2 &&
                it.buttonConfigs.size == 36 &&
                it.buttonConfigs[7] != null &&
                it.buttonConfigs[7]!!.label == "zurück zum Start"
            })
        }
        coVerify { bookRepository.updateLastModified("book1") }
    }

    @Test
    fun `execute throws error for grid larger than 6x6`() {
        // Manual 7x6 
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { createPageUseCase.execute("Big", 7, 6, "b1", emptyList()) }
        }

        // Template 7x1
        // Note: PageTemplate constructor also validates now, so we must be careful
        assertThrows(IllegalArgumentException::class.java) {
             PageTemplate(
                 id = "t1",
                 name = "Big",
                 rows = 7,
                 columns = 1,
                 buttonConfigs = emptyList()
             )
        }
    }

    @Test
    fun `execute patches empty navigation actions in template with homePageId`() = runTest {
        // Given
        val navAction = NavigateToPageButtonAction(pageId = "")
        val btnConfig = ButtonConfig(id = "b1", label = "Back", buttonAction = navAction, auditoryCue = null)
        val template = PageTemplate(
            id = "t1",
            name = "T",
            rows = 1,
            columns = 1,
            buttonConfigs = listOf(btnConfig)
        )
        
        coEvery { templateRepository.getById("t1") } returns template
        every { settingsRepository.defaultStartPageId } returns "home-id"

        // When
        createPageUseCase.execute("Page", 1, 1, "book1", emptyList(), "t1")

        // Then
        coVerify {
            pageRepository.insertPage(match {
                val action = it.buttonConfigs[0]?.buttonAction as? NavigateToPageButtonAction
                action?.pageId == "home-id"
            })
        }
    }

    @Test
    fun `execute does not patch navigation actions that already have a pageId`() = runTest {
        // Given
        val navAction = NavigateToPageButtonAction(pageId = "existing-id")
        val btnConfig = ButtonConfig(id = "b1", label = "Back", buttonAction = navAction, auditoryCue = null)
        val template = PageTemplate(
            id = "t1",
            name = "T",
            rows = 1,
            columns = 1,
            buttonConfigs = listOf(btnConfig)
        )
        
        coEvery { templateRepository.getById("t1") } returns template
        every { settingsRepository.defaultStartPageId } returns "home-id"

        // When
        createPageUseCase.execute("Page", 1, 1, "book1", emptyList(), "t1")

        // Then
        coVerify {
            pageRepository.insertPage(match {
                val action = it.buttonConfigs[0]?.buttonAction as? NavigateToPageButtonAction
                action?.pageId == "existing-id"
            })
        }
    }
}

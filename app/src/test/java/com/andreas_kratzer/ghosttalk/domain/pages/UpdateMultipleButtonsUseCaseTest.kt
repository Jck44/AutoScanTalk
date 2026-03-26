package com.andreas_kratzer.ghosttalk.domain.pages

import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.data.TemplateRepository
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class UpdateMultipleButtonsUseCaseTest {

    private lateinit var pageRepository: PageRepository
    private lateinit var templateRepository: TemplateRepository
    private lateinit var bookRepository: BookRepository
    private lateinit var useCase: UpdateMultipleButtonsUseCase

    @Before
    fun setup() {
        pageRepository = mockk(relaxed = true)
        templateRepository = mockk(relaxed = true)
        bookRepository = mockk(relaxed = true)
        useCase = UpdateMultipleButtonsUseCase(pageRepository, templateRepository, bookRepository)
    }

    @Test
    fun `execute updates isActive for pages`() = runTest {
        val pageId = "page1"
        val initialPage = Page(
            id = pageId,
            name = "Test Page",
            bookId = "book1",
            buttonConfigs = listOf(
                ButtonConfig(label = "Btn 1", isActive = true),
                ButtonConfig(label = "Btn 2", isActive = true)
            )
        )
        coEvery { pageRepository.getPageById(pageId) } returns initialPage

        val usages = listOf(
            UsageLocation.PageUsage(pageId, "Test Page", "Btn 1", 0),
            UsageLocation.PageUsage(pageId, "Test Page", "Btn 2", 1)
        )

        val capturedPage = io.mockk.slot<Page>()
        coEvery { pageRepository.updatePage(capture(capturedPage)) } returns Unit

        useCase.execute(usages, false)

        coVerify {
            pageRepository.updatePage(any())
            bookRepository.updateLastModified("book1", any())
        }
        
        assertEquals(false, capturedPage.captured.buttonConfigs[0]?.isActive)
        assertEquals(false, capturedPage.captured.buttonConfigs[1]?.isActive)
    }

    @Test
    fun `execute updates isActive for templates`() = runTest {
        val templateId = "tmpl1"
        val initialTemplate = PageTemplate(
            id = templateId,
            name = "Test Template",
            buttonConfigs = listOf(
                ButtonConfig(label = "Btn T", isActive = false)
            )
        )
        coEvery { templateRepository.getById(templateId) } returns initialTemplate

        val usages = listOf(
            UsageLocation.TemplateUsage(templateId, "Test Template", "Btn T", 0)
        )

        useCase.execute(usages, true)

        coVerify {
            templateRepository.update(match { 
                it.id == templateId && 
                it.buttonConfigs[0]?.isActive == true 
            })
        }
    }
}

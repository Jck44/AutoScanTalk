package com.andreas_kratzer.ghosttalk.domain.pages

import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.data.TemplateRepository
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.PageTemplate
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetPageUsagesUseCaseTest {

    private val pageRepository = mockk<PageRepository>()
    private val templateRepository = mockk<TemplateRepository>()
    private val useCase = GetPageUsagesUseCase(pageRepository, templateRepository)

    @Test
    fun `execute returns correct page usages`() = runTest {
        val targetPageId = "target"
        
        val page1 = createPage("p1", "Page 1", listOf(
            ButtonConfig(buttonAction = NavigateToPageButtonAction(targetPageId))
        ))
        val page2 = createPage("p2", "Page 2", emptyList())
        val targetPage = createPage(targetPageId, "Target", emptyList())

        coEvery { pageRepository.getAllPagesFlow() } returns flowOf(listOf(page1, page2, targetPage))
        coEvery { templateRepository.getAllTemplates() } returns flowOf(emptyList())

        val result = useCase.execute(targetPageId)

        assertEquals(1, result.size)
        assertTrue(result[0] is UsageLocation.PageUsage)
        assertEquals("p1", result[0].id)
    }

    @Test
    fun `execute returns correct template usages`() = runTest {
        val targetPageId = "target"
        
        val template1 = PageTemplate(id = "t1", name = "Template 1", buttonConfigs = listOf(
            ButtonConfig(buttonAction = NavigateToPageButtonAction(targetPageId))
        ))

        coEvery { pageRepository.getAllPagesFlow() } returns flowOf(emptyList())
        coEvery { templateRepository.getAllTemplates() } returns flowOf(listOf(template1))

        val result = useCase.execute(targetPageId)

        assertEquals(1, result.size)
        assertTrue(result[0] is UsageLocation.TemplateUsage)
        assertEquals("t1", result[0].id)
    }

    private fun createPage(id: String, name: String, buttonConfigs: List<ButtonConfig?>) = Page(
        id = id,
        bookId = "book1",
        name = name,
        templateId = null,
        rows = 4,
        columns = 4,
        buttonConfigs = buttonConfigs,
        createdAt = 0
    )
}

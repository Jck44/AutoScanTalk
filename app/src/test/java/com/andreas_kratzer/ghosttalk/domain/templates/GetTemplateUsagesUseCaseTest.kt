package com.andreas_kratzer.ghosttalk.domain.templates

import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.domain.pages.UsageLocation
import com.andreas_kratzer.ghosttalk.model.Page
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetTemplateUsagesUseCaseTest {

    private val pageRepository = mockk<PageRepository>()
    private val useCase = GetTemplateUsagesUseCase(pageRepository)

    @Test
    fun `execute returns pages using the template`() = runTest {
        val templateId = "t1"
        val page1 = createPage("p1", "Page 1", templateId)
        val page2 = createPage("p2", "Page 2", "other")

        coEvery { pageRepository.getAllPages() } returns listOf(page1, page2)

        val result = useCase.execute(templateId)

        assertEquals(1, result.size)
        assertEquals("p1", result[0].id)
        assert(result[0] is UsageLocation.PageUsage)
    }

    private fun createPage(id: String, name: String, templateId: String?) = Page(
        id = id,
        bookId = "book1",
        name = name,
        templateId = templateId,
        createdAt = 0
    )
}

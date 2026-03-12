package com.andreas_kratzer.ghosttalk.domain.templates

import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import com.andreas_kratzer.ghosttalk.core.database.TemplateRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteTemplateUseCaseTest {

    private lateinit var templateRepository: TemplateRepository
    private lateinit var pageRepository: com.andreas_kratzer.ghosttalk.core.database.PageRepository
    private lateinit var deleteTemplateUseCase: DeleteTemplateUseCase

    @Before
    fun setup() {
        templateRepository = mockk(relaxed = true)
        pageRepository = mockk(relaxed = true)
        deleteTemplateUseCase = DeleteTemplateUseCase(templateRepository, pageRepository)
    }

    @Test
    fun `execute calls repository delete`() = runTest {
        // Given
        val template = PageTemplate(
            id = "id",
            name = "Name",
            rows = 1,
            columns = 1,
            buttonConfigs = emptyList()
        )

        // When
        deleteTemplateUseCase.execute(template)

        // Then
        coVerify { templateRepository.delete(template) }
    }

    @Test
    fun `execute with clearUsages true nullifies templateId in pages`() = runTest {
        val template = PageTemplate(id = "t1", name = "Template", rows = 1, columns = 1, buttonConfigs = emptyList())
        val page = com.andreas_kratzer.ghosttalk.core.model.Page(
            id = "p1", 
            bookId = "b1", 
            name = "Page", 
            templateId = "t1",
            rows = 1,
            columns = 1,
            buttonConfigs = emptyList()
        )

        io.mockk.coEvery { pageRepository.getAllPages() } returns listOf(page)

        deleteTemplateUseCase.execute(template, clearUsages = true)

        coVerify { pageRepository.updatePageSettingsOnly(match { it.id == "p1" && it.templateId == null }) }
        coVerify { templateRepository.delete(template) }
    }
}

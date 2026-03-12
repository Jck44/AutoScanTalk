package com.andreas_kratzer.ghosttalk.domain.pages

import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.database.BookRepository
import com.andreas_kratzer.ghosttalk.core.database.PageRepository
import com.andreas_kratzer.ghosttalk.core.database.TemplateRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class DeletePageUseCaseTest {

    private lateinit var pageRepository: PageRepository
    private lateinit var templateRepository: TemplateRepository
    private lateinit var bookRepository: BookRepository
    private lateinit var useCase: DeletePageUseCase

    @Before
    fun setup() {
        pageRepository = mockk(relaxed = true)
        bookRepository = mockk(relaxed = true)
        templateRepository = mockk(relaxed = true)
        useCase = DeletePageUseCase(pageRepository, templateRepository, bookRepository)
    }

    @Test
    fun `execute deletes page and updates book`() = runTest {
        val page = Page(id = "p1", bookId = "b1", name = "Test", rows = 1, columns = 1, buttonConfigs = emptyList())

        useCase.execute(page)

        coVerify { pageRepository.deletePage(page) }
        coVerify { bookRepository.updateLastModified("b1") }
    }

    @Test
    fun `execute with deleteUsages true clears navigation buttons`() = runTest {
        val pageToDelete = Page(id = "p1", bookId = "b1", name = "To Delete", rows = 1, columns = 1, buttonConfigs = emptyList())
        val otherPage = Page(
            id = "p2", 
            bookId = "b1", 
            name = "Other", 
            rows = 1,
            columns = 1,
            buttonConfigs = listOf(
                com.andreas_kratzer.ghosttalk.core.model.ButtonConfig(
                    id = "b1",
                    label = "Navigate",
                    spokenText = "",
                    buttonAction = com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction("p1"),
                    auditoryCue = null
                )
            )
        )

        io.mockk.coEvery { pageRepository.getAllPages() } returns listOf(pageToDelete, otherPage)
        io.mockk.every { templateRepository.getAllTemplates() } returns kotlinx.coroutines.flow.flowOf(emptyList())

        useCase.execute(pageToDelete, deleteUsages = true)

        coVerify { pageRepository.updatePage(match { it.id == "p2" && it.buttonConfigs[0] == null }) }
        coVerify { pageRepository.deletePage(pageToDelete) }
    }
}

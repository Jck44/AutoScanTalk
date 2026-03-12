package com.andreas_kratzer.ghosttalk.domain.pages

import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.data.BookRepository
import com.andreas_kratzer.ghosttalk.data.PageRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MoveButtonUseCaseTest {

    private val pageRepository = mockk<PageRepository>(relaxed = true)
    private val bookRepository = mockk<BookRepository>(relaxed = true)
    private val useCase = MoveButtonUseCase(pageRepository, bookRepository)

    @Test
    fun `execute swaps buttons correctly`() = runTest {
        // Arrange
        val button1 = mockk<ButtonConfig>()
        val button2 = mockk<ButtonConfig>()
        val initialButtons = mutableListOf<ButtonConfig?>(button1, button2)
        while (initialButtons.size < 49) initialButtons.add(null)
        
        val page = createPage("page1", initialButtons)
        
        coEvery { pageRepository.getPageById("page1") } returns page

        // Act
        val updatedPage = useCase.execute("page1", 0, 1)

        // Assert
        assertEquals(button2, updatedPage?.buttonConfigs?.get(0))
        assertEquals(button1, updatedPage?.buttonConfigs?.get(1))
        
        coVerify { pageRepository.updatePage(match { it.buttonConfigs[0] == button2 && it.buttonConfigs[1] == button1 }) }
        coVerify { bookRepository.updateLastModified("book1") }
    }

    @Test
    fun `execute does nothing if indices are same`() = runTest {
        // Arrange
        val page = createPage("page1", emptyList())
        coEvery { pageRepository.getPageById("page1") } returns page

        // Act
        val result = useCase.execute("page1", 5, 5)

        // Assert
        assertEquals(page, result)
        coVerify(exactly = 0) { pageRepository.updatePage(any()) }
    }

    private fun createPage(id: String, buttonConfigs: List<ButtonConfig?>) = Page(
        id = id,
        bookId = "book1",
        name = "Test Page",
        templateId = null,
        rows = 4,
        columns = 4,
        scanPattern = null,
        rowNames = emptyList(),
        buttonConfigs = buttonConfigs,
        orderIndex = 0,
        createdAt = System.currentTimeMillis()
    )
}

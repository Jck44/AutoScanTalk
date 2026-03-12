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

class MoveRowUseCaseTest {

    private val pageRepository = mockk<PageRepository>(relaxed = true)
    private val bookRepository = mockk<BookRepository>(relaxed = true)
    private val useCase = MoveRowUseCase(pageRepository, bookRepository)

    @Test
    fun `execute moves row correctly`() = runTest {
        // Arrange
        val buttons = MutableList<ButtonConfig?>(49) { null }
        val row0Button = mockk<ButtonConfig>()
        val row1Button = mockk<ButtonConfig>()
        buttons[0] = row0Button // Start of row 0
        buttons[7] = row1Button // Start of row 1
        
        val rowNames = listOf("Row 0", "Row 1")
        val page = createPage("page1", buttons, rowNames)
        
        coEvery { pageRepository.getPageById("page1") } returns page

        // Act
        // Move Row 0 to position 1
        val result = useCase.execute("page1", 0, 1)

        // Assert
        assertEquals(row1Button, result?.buttonConfigs?.get(0))
        assertEquals(row0Button, result?.buttonConfigs?.get(7))
        
        assertEquals("Row 1", result?.rowNames?.get(0))
        assertEquals("Row 0", result?.rowNames?.get(1))
        
        coVerify { pageRepository.updatePage(any()) }
        coVerify { bookRepository.updateLastModified("book1") }
    }

    @Test
    fun `execute handles missing row names`() = runTest {
        // Arrange
        val buttons = MutableList<ButtonConfig?>(49) { null }
        val page = createPage("page1", buttons, emptyList())
        coEvery { pageRepository.getPageById("page1") } returns page

        // Act
        val result = useCase.execute("page1", 0, 1)

        // Assert
        assertEquals(0, result?.rowNames?.size ?: 0)
        coVerify { pageRepository.updatePage(any()) }
    }

    private fun createPage(id: String, buttonConfigs: List<ButtonConfig?>, rowNames: List<String>) = Page(
        id = id,
        bookId = "book1",
        name = "Test Page",
        templateId = null,
        rows = 4,
        columns = 4,
        scanPattern = null,
        rowNames = rowNames,
        buttonConfigs = buttonConfigs,
        orderIndex = 0,
        createdAt = System.currentTimeMillis()
    )
}

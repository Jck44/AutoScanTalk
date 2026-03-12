package com.andreas_kratzer.ghosttalk.domain.pages

import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.data.BookRepository
import com.andreas_kratzer.ghosttalk.data.PageRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class UpdateRowNameUseCaseTest {

    private lateinit var useCase: UpdateRowNameUseCase
    private lateinit var pageRepository: PageRepository
    private lateinit var bookRepository: BookRepository

    @Before
    fun setup() {
        pageRepository = mockk(relaxed = true)
        bookRepository = mockk(relaxed = true)
        useCase = UpdateRowNameUseCase(pageRepository, bookRepository)
    }

    @Test
    fun `execute updates specific row name`() = runTest {
        val page = Page(
            id = "p1",
            bookId = "book1",
            name = "Page",
            rows = 3,
            columns = 1,
            buttonConfigs = emptyList(),
            rowNames = listOf("R1", "R2", "R3")
        )
        
        coEvery { pageRepository.getPageById("p1") } returns page

        // Update Row 1 (index 1) to "NewR2"
        val result = useCase.execute("p1", 1, "NewR2")

        assertEquals(listOf("R1", "NewR2", "R3"), result?.rowNames)
        
        coVerify {
            pageRepository.updatePageSettingsOnly(match { 
                it.id == "p1" && it.rowNames == listOf("R1", "NewR2", "R3")
            })
            bookRepository.updateLastModified("book1")
        }
    }

    @Test
    fun `execute pads rowNames if index is larger than current list size`() = runTest {
        val page = Page(
            id = "p1",
            bookId = "book1",
            name = "Page",
            rows = 3,
            columns = 1,
            buttonConfigs = emptyList(),
            rowNames = listOf("R1")
        )
        
        coEvery { pageRepository.getPageById("p1") } returns page

        // Update Row 2 (index 2) to "NewR3"
        val result = useCase.execute("p1", 2, "NewR3")

        // Expected padding: ["R1", "Zeile 2", "NewR3"]
        assertEquals(listOf("R1", "Zeile 2", "NewR3"), result?.rowNames)
    }

    @Test
    fun `execute returns null if page not found`() = runTest {
        coEvery { pageRepository.getPageById("any") } returns null

        val result = useCase.execute("any", 0, "New")

        assertNull(result)
    }

    @Test
    fun `execute returns null if rowIndex out of bounds for page rows`() = runTest {
        val page = Page(
            id = "p1",
            bookId = "book1",
            name = "Page",
            rows = 2,
            columns = 1,
            buttonConfigs = emptyList(),
            rowNames = listOf("R1", "R2")
        )
        coEvery { pageRepository.getPageById("p1") } returns page

        // Index 2 is out of bounds for 2 rows
        val result = useCase.execute("p1", 2, "New")

        assertNull(result)
    }
}

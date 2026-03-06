package com.andreas_kratzer.ghosttalk.domain.pages

import com.andreas_kratzer.ghosttalk.data.BookRepository
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.model.Page
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class UpdateRowNameUseCaseTest {

    private lateinit var pageRepository: PageRepository
    private lateinit var bookRepository: BookRepository
    private lateinit var useCase: UpdateRowNameUseCase

    @Before
    fun setup() {
        pageRepository = mockk(relaxed = true)
        bookRepository = mockk(relaxed = true)
        useCase = UpdateRowNameUseCase(pageRepository, bookRepository)
    }

    @Test
    fun `execute updates row name and returns updated page`() = runTest {
        val initialPage = Page(id = "p1", bookId = "b1", name = "Test", rowNames = listOf("Row 1"), buttonConfigs = emptyList())
        coEvery { pageRepository.getPageById("p1") } returns initialPage

        val result = useCase.execute("p1", 0, "New Row Name")

        assertNotNull(result)
        assertEquals("New Row Name", result?.rowNames?.get(0))
        coVerify { pageRepository.updatePage(match { it.rowNames[0] == "New Row Name" }) }
        coVerify { bookRepository.updateLastModified("b1") }
    }

    @Test
    fun `execute pads row names if index is out of bounds`() = runTest {
        val initialPage = Page(id = "p1", bookId = "b1", name = "Test", rowNames = emptyList(), buttonConfigs = emptyList())
        coEvery { pageRepository.getPageById("p1") } returns initialPage

        val result = useCase.execute("p1", 1, "Row 2 Name")

        assertNotNull(result)
        assertEquals(2, result?.rowNames?.size)
        assertEquals("Zeile 1", result?.rowNames?.get(0))
        assertEquals("Row 2 Name", result?.rowNames?.get(1))
    }
}

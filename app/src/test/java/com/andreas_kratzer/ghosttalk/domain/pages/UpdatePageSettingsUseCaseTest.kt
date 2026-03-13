package com.andreas_kratzer.ghosttalk.domain.pages

import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class UpdatePageSettingsUseCaseTest {

    private lateinit var useCase: UpdatePageSettingsUseCase
    private lateinit var pageRepository: PageRepository
    private lateinit var bookRepository: BookRepository

    @Before
    fun setup() {
        pageRepository = mockk(relaxed = true)
        bookRepository = mockk(relaxed = true)
        useCase = UpdatePageSettingsUseCase(pageRepository, bookRepository)
    }

    @Test
    fun `execute updates name scanPattern and rowNames`() = runTest {
        val page = Page(
            id = "p1",
            bookId = "book1",
            name = "Old Name",
            rows = 2,
            columns = 1,
            buttonConfigs = emptyList(),
            scanPattern = "linear",
            rowNames = listOf("OldR1", "OldR2")
        )
        
        coEvery { pageRepository.getPageById("p1") } returns page

        val newRowNames = listOf("NewR1", "NewR2")
        val result = useCase.execute("p1", "New Name", "row_by_row", newRowNames)

        assertEquals("New Name", result?.name)
        assertEquals("row_by_row", result?.scanPattern)
        assertEquals(newRowNames, result?.rowNames)
        
        coVerify {
            pageRepository.updatePageSettingsOnly(match { 
                it.id == "p1" && 
                it.name == "New Name" && 
                it.scanPattern == "row_by_row" && 
                it.rowNames == newRowNames 
            })
            bookRepository.updateLastModified("book1")
        }
    }

    @Test
    fun `execute returns null if page not found`() = runTest {
        coEvery { pageRepository.getPageById("any") } returns null

        val result = useCase.execute("any", "New", null, emptyList())

        assertNull(result)
        coVerify(exactly = 0) {
            pageRepository.updatePageSettingsOnly(any())
        }
    }
}

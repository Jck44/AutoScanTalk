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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class UpdatePageSettingsUseCaseTest {

    private lateinit var pageRepository: PageRepository
    private lateinit var bookRepository: BookRepository
    private lateinit var useCase: UpdatePageSettingsUseCase

    @Before
    fun setup() {
        pageRepository = mockk(relaxed = true)
        bookRepository = mockk(relaxed = true)
        useCase = UpdatePageSettingsUseCase(pageRepository, bookRepository)
    }

    @Test
    fun `execute updates settings and returns updated page`() = runTest {
        val initialPage = Page(id = "p1", bookId = "b1", name = "Old Name", buttonConfigs = emptyList())
        coEvery { pageRepository.getPageById("p1") } returns initialPage

        val result = useCase.execute("p1", "New Name", "row_column", listOf("Row A"))

        assertNotNull(result)
        assertEquals("New Name", result?.name)
        assertEquals("row_column", result?.scanPattern)
        assertEquals(listOf("Row A"), result?.rowNames)
        
        coVerify { 
            pageRepository.updatePage(match { 
                it.name == "New Name" && it.scanPattern == "row_column" && it.rowNames == listOf("Row A")
            }) 
        }
        coVerify { bookRepository.updateLastModified("b1") }
    }

    @Test
    fun `execute returns null if page does not exist`() = runTest {
        coEvery { pageRepository.getPageById("invalid") } returns null

        val result = useCase.execute("invalid", "Name", null, emptyList())

        assertNull(result)
    }
}

package com.andreas_kratzer.ghosttalk.domain.pages

import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class UpdateButtonConfigUseCaseTest {

    private lateinit var useCase: UpdateButtonConfigUseCase
    private lateinit var pageRepository: PageRepository
    private lateinit var bookRepository: BookRepository

    @Before
    fun setup() {
        pageRepository = mockk(relaxed = true)
        bookRepository = mockk(relaxed = true)
        useCase = UpdateButtonConfigUseCase(pageRepository, bookRepository)
    }

    @Test
    fun `execute updates button config and persists page`() = runTest {
        val oldConfig = ButtonConfig(id = "b1", label = "Old", auditoryCue = null, buttonAction = SpeakTextButtonAction())
        val page = Page(
            id = "p1",
            bookId = "book1",
            name = "Page",
            rows = 1,
            columns = 1,
            buttonConfigs = listOf(oldConfig)
        )
        
        coEvery { pageRepository.getPageById("p1") } returns page

        val newConfig = oldConfig.copy(label = "New")
        val result = useCase.execute("p1", 0, newConfig)

        assertEquals("New", result?.buttonConfigs?.get(0)?.label)
        coVerify {
            pageRepository.updatePage(match { it.id == "p1" && it.buttonConfigs[0]?.label == "New" })
            bookRepository.updateLastModified("book1")
        }
    }

    @Test
    fun `execute returns null if page not found`() = runTest {
        coEvery { pageRepository.getPageById("any") } returns null

        val result = useCase.execute("any", 0, null)

        assertNull(result)
        coVerify(exactly = 0) {
            pageRepository.updatePage(any())
        }
    }

    @Test
    fun `execute returns null if index out of bounds`() = runTest {
        val page = Page(
            id = "p1",
            bookId = "book1",
            name = "Page",
            rows = 1,
            columns = 1,
            buttonConfigs = emptyList()
        )
        coEvery { pageRepository.getPageById("p1") } returns page

        val result = useCase.execute("p1", 0, null)

        assertNull(result)
    }
}

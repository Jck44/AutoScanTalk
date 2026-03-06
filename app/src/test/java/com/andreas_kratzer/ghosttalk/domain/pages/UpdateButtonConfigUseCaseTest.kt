package com.andreas_kratzer.ghosttalk.domain.pages

import com.andreas_kratzer.ghosttalk.data.BookRepository
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.SpeakTextButtonAction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class UpdateButtonConfigUseCaseTest {

    private lateinit var pageRepository: PageRepository
    private lateinit var bookRepository: BookRepository
    private lateinit var useCase: UpdateButtonConfigUseCase

    @Before
    fun setup() {
        pageRepository = mockk(relaxed = true)
        bookRepository = mockk(relaxed = true)
        useCase = UpdateButtonConfigUseCase(pageRepository, bookRepository)
    }

    @Test
    fun `execute updates button config and returns updated page`() = runTest {
        val initialConfigs = listOf(null, null)
        val initialPage = Page(id = "p1", bookId = "b1", name = "Test", buttonConfigs = initialConfigs)
        coEvery { pageRepository.getPageById("p1") } returns initialPage
        
        val newConfig = ButtonConfig(id = "btn1", label = "New", auditoryCue = null, buttonAction = SpeakTextButtonAction())

        val result = useCase.execute("p1", 0, newConfig)

        assertNotNull(result)
        assertEquals("New", result?.buttonConfigs?.get(0)?.label)
        coVerify { pageRepository.updatePage(match { it.buttonConfigs[0]?.label == "New" }) }
        coVerify { bookRepository.updateLastModified("b1") }
    }

    @Test
    fun `execute returns null if pageId is invalid`() = runTest {
        coEvery { pageRepository.getPageById("invalid") } returns null

        val result = useCase.execute("invalid", 0, null)

        assertNull(result)
    }

    @Test
    fun `execute returns null if index is out of bounds`() = runTest {
        val initialPage = Page(id = "p1", bookId = "b1", name = "Test", buttonConfigs = listOf(null))
        coEvery { pageRepository.getPageById("p1") } returns initialPage

        val result = useCase.execute("p1", 5, null)

        assertNull(result)
    }
}

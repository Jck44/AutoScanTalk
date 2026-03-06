package com.andreas_kratzer.ghosttalk.domain.pages

import com.andreas_kratzer.ghosttalk.data.BookRepository
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.model.Page
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class DeletePageUseCaseTest {

    private lateinit var pageRepository: PageRepository
    private lateinit var bookRepository: BookRepository
    private lateinit var useCase: DeletePageUseCase

    @Before
    fun setup() {
        pageRepository = mockk(relaxed = true)
        bookRepository = mockk(relaxed = true)
        useCase = DeletePageUseCase(pageRepository, bookRepository)
    }

    @Test
    fun `execute deletes page and updates book`() = runTest {
        val page = Page(id = "p1", bookId = "b1", name = "Test", buttonConfigs = emptyList())

        useCase.execute(page)

        coVerify { pageRepository.deletePage(page) }
        coVerify { bookRepository.updateLastModified("b1") }
    }
}

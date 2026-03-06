package com.andreas_kratzer.ghosttalk.domain.pages

import com.andreas_kratzer.ghosttalk.data.BookRepository
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.SortOrder
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ReorderPagesUseCaseTest {

    private lateinit var pageRepository: PageRepository
    private lateinit var bookRepository: BookRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var useCase: ReorderPagesUseCase

    @Before
    fun setup() {
        pageRepository = mockk(relaxed = true)
        bookRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        useCase = ReorderPagesUseCase(pageRepository, bookRepository, settingsRepository)
    }

    @Test
    fun `execute reorders pages and updates indices`() = runTest {
        val p1 = Page(id = "1", bookId = "b1", name = "P1", buttonConfigs = emptyList(), orderIndex = 0)
        val p2 = Page(id = "2", bookId = "b1", name = "P2", buttonConfigs = emptyList(), orderIndex = 1)
        val currentList = listOf(p1, p2)

        useCase.execute(currentList, 0, 1, "b1")

        coVerify { pageRepository.updatePage(match { it.id == "1" && it.orderIndex == 1 }) }
        coVerify { pageRepository.updatePage(match { it.id == "2" && it.orderIndex == 0 }) }
        coVerify { bookRepository.updateLastModified("b1") }
        verify { settingsRepository.pageSortOrder = SortOrder.MANUAL.name }
    }

    @Test
    fun `execute with invalid indices does nothing`() = runTest {
        val p1 = Page(id = "1", bookId = "b1", name = "P1", buttonConfigs = emptyList())
        val currentList = listOf(p1)

        useCase.execute(currentList, 0, 5, "b1")

        coVerify(exactly = 0) { pageRepository.updatePage(any()) }
    }
}

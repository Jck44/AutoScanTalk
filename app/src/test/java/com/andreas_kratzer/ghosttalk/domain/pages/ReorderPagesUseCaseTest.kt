package com.andreas_kratzer.ghosttalk.domain.pages

import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.SortOrder
import com.andreas_kratzer.ghosttalk.data.BookRepository
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ReorderPagesUseCaseTest {

    private lateinit var useCase: ReorderPagesUseCase
    private lateinit var pageRepository: PageRepository
    private lateinit var bookRepository: BookRepository
    private lateinit var settingsRepository: SettingsRepository

    @Before
    fun setup() {
        pageRepository = mockk(relaxed = true)
        bookRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        useCase = ReorderPagesUseCase(pageRepository, bookRepository, settingsRepository)
    }

    private fun createPage(id: String, order: Int): Page {
        return Page(
            id = id,
            bookId = "b1",
            name = "Page $id",
            rows = 1,
            columns = 1,
            buttonConfigs = emptyList(),
            orderIndex = order
        )
    }

    @Test
    fun `execute reorders pages and updates indices in DB`() = runTest {
        val p1 = createPage("1", 0)
        val p2 = createPage("2", 1)
        val p3 = createPage("3", 2)
        val currentList = listOf(p1, p2, p3)

        // Move p2 to the top (index 1 to 0)
        useCase.execute(currentList, fromIndex = 1, toIndex = 0, activeBookId = "b1")

        // Expected order: p2 (0), p1 (1), p3 (2)
        coVerify {
            pageRepository.updatePageSettingsOnly(match { it.id == "2" && it.orderIndex == 0 })
            pageRepository.updatePageSettingsOnly(match { it.id == "1" && it.orderIndex == 1 })
        }
        // p3's orderIndex did not change, so it shouldn't be updated
        coVerify(exactly = 0) {
            pageRepository.updatePageSettingsOnly(match { it.id == "3" })
        }
        
        coVerify { bookRepository.updateLastModified("b1") }
        verify { settingsRepository.pageSortOrder = SortOrder.MANUAL.name }
    }

    @Test
    fun `execute does nothing if indices are out of bounds`() = runTest {
        val p1 = createPage("1", 0)
        val currentList = listOf(p1)

        useCase.execute(currentList, fromIndex = 5, toIndex = 0, activeBookId = "b1")

        coVerify(exactly = 0) {
            pageRepository.updatePageSettingsOnly(any())
            bookRepository.updateLastModified(any())
        }
    }
}

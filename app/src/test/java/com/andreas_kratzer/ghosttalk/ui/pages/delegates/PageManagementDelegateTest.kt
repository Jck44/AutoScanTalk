package com.andreas_kratzer.ghosttalk.ui.pages.delegates

import com.andreas_kratzer.ghosttalk.data.BookRepository
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.data.TemplateRepository
import com.andreas_kratzer.ghosttalk.domain.pages.CreatePageUseCase
import com.andreas_kratzer.ghosttalk.domain.pages.DeletePageUseCase
import com.andreas_kratzer.ghosttalk.domain.pages.ExportPageUseCase
import com.andreas_kratzer.ghosttalk.domain.pages.GetFilteredPagesUseCase
import com.andreas_kratzer.ghosttalk.domain.pages.GetPageUsagesUseCase
import com.andreas_kratzer.ghosttalk.domain.pages.GetPagesUseCase
import com.andreas_kratzer.ghosttalk.domain.pages.ImportPageUseCase
import com.andreas_kratzer.ghosttalk.domain.pages.MoveButtonToPageUseCase
import com.andreas_kratzer.ghosttalk.domain.pages.MoveButtonUseCase
import com.andreas_kratzer.ghosttalk.domain.pages.MoveRowUseCase
import com.andreas_kratzer.ghosttalk.domain.pages.UpdateButtonConfigUseCase
import com.andreas_kratzer.ghosttalk.domain.pages.UpdatePageSettingsUseCase
import com.andreas_kratzer.ghosttalk.domain.pages.UpdateRowNameUseCase
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.SortOrder
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PageManagementDelegateTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var pageRepository: PageRepository
    private lateinit var bookRepository: BookRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var templateRepository: TemplateRepository
    private lateinit var getPagesUseCase: GetPagesUseCase
    private lateinit var createPageUseCaseMock: CreatePageUseCase
    private lateinit var deletePageUseCase: DeletePageUseCase
    private lateinit var updateButtonConfigUseCase: UpdateButtonConfigUseCase
    private lateinit var updatePageSettingsUseCase: UpdatePageSettingsUseCase
    private lateinit var updateRowNameUseCase: UpdateRowNameUseCase
    private lateinit var moveRowUseCase: MoveRowUseCase
    private lateinit var moveButtonUseCase: MoveButtonUseCase
    private lateinit var moveButtonToPageUseCase: MoveButtonToPageUseCase
    private lateinit var importPageUseCase: ImportPageUseCase
    private lateinit var exportPageUseCase: ExportPageUseCase
    private lateinit var getFilteredPagesUseCase: GetFilteredPagesUseCase
    private lateinit var getPageUsagesUseCase: GetPageUsagesUseCase
    private lateinit var appStateRepository: com.andreas_kratzer.ghosttalk.data.AppStateRepository

    private lateinit var delegate: PageManagementDelegate

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        pageRepository = mockk(relaxed = true)
        bookRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        templateRepository = mockk(relaxed = true)
        getPagesUseCase = mockk(relaxed = true)
        createPageUseCaseMock = mockk(relaxed = true)
        deletePageUseCase = mockk(relaxed = true)
        updateButtonConfigUseCase = mockk(relaxed = true)
        updatePageSettingsUseCase = mockk(relaxed = true)
        updateRowNameUseCase = mockk(relaxed = true)
        moveRowUseCase = mockk(relaxed = true)
        moveButtonUseCase = mockk(relaxed = true)
        moveButtonToPageUseCase = mockk(relaxed = true)
        importPageUseCase = mockk(relaxed = true)
        exportPageUseCase = mockk(relaxed = true)
        getPageUsagesUseCase = mockk(relaxed = true)
        getFilteredPagesUseCase = GetFilteredPagesUseCase(settingsRepository)
        appStateRepository = com.andreas_kratzer.ghosttalk.data.AppStateRepository()

        every { settingsRepository.pageSortOrderFlow } returns MutableStateFlow(SortOrder.A_Z.name)
        every { getPagesUseCase.execute(any()) } returns MutableStateFlow(emptyList())
        every { templateRepository.getAllTemplates() } returns MutableStateFlow(emptyList())

        delegate = PageManagementDelegate(
            pageRepository,
            bookRepository,
            templateRepository,
            getPagesUseCase,
            createPageUseCaseMock,
            deletePageUseCase,
            updateButtonConfigUseCase,
            updatePageSettingsUseCase,
            updateRowNameUseCase,
            moveRowUseCase,
            moveButtonUseCase,
            moveButtonToPageUseCase,
            importPageUseCase,
            exportPageUseCase,
            getFilteredPagesUseCase,
            getPageUsagesUseCase,
            appStateRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `moveRow delegates to use case`() = runTest(testDispatcher) {
        delegate.init(backgroundScope)
        delegate.moveRow("page1", 0, 1)
        coVerify { moveRowUseCase.execute("page1", 0, 1) }
    }

    @Test
    fun `moveButton delegates to use case`() = runTest(testDispatcher) {
        delegate.init(backgroundScope)
        delegate.moveButton("page1", 0, 5)
        coVerify { moveButtonUseCase.execute("page1", 0, 5) }
    }

    @Test
    fun `init sets up flows and collects pages`() = runTest(testDispatcher) {
        val pages = listOf(Page(id = "1", bookId = "book1", name = "Page 1", rows = 1, columns = 1, buttonConfigs = emptyList()))
        val pagesFlow = MutableStateFlow(pages)
        every { getPagesUseCase.execute(any()) } returns pagesFlow

        delegate.init(backgroundScope)

        assertEquals(pages, delegate.allPagesFlow.value)
        assertEquals(pages, delegate.unfilteredPages.value)
    }

    @Test
    fun `createNewPage delegates to use case`() = runTest(testDispatcher) {
        delegate.init(backgroundScope)
        
        delegate.createNewPage("New Page", 2, 2, "book1", null) {}
        
        coVerify { createPageUseCaseMock.execute("New Page", 2, 2, "book1", any(), any()) }
        coVerify { bookRepository.updateLastModified("book1") }
    }

    @Test
    fun `filteredPages respects searchQuery`() = runTest(testDispatcher) {
        val page1 = Page(id = "1", name = "Apple", bookId = "book1", rows = 1, columns = 1, buttonConfigs = emptyList())
        val page2 = Page(id = "2", name = "Banana", bookId = "book1", rows = 1, columns = 1, buttonConfigs = emptyList())
        val allPagesFlow = MutableStateFlow(listOf(page1, page2))
        every { getPagesUseCase.execute(any()) } returns allPagesFlow

        delegate.init(backgroundScope)

        assertEquals(2, delegate.filteredPages.value.size)

        delegate.updateSearchQuery("Apple")

        val filtered = delegate.filteredPages.value
        assertEquals(1, filtered.size)
        assertEquals("Apple", filtered.first().name)
    }

    @Test
    fun `deletePage delegates to use case`() = runTest(testDispatcher) {
        delegate.init(backgroundScope)
        
        val page = Page(id = "1", name = "Test", bookId = "book1", rows = 1, columns = 1, buttonConfigs = emptyList())
        delegate.deletePage(page)
        
        coVerify { deletePageUseCase.execute(page) }
    }
}

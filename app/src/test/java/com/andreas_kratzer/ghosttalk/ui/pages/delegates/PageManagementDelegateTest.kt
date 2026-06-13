package com.andreas_kratzer.ghosttalk.ui.pages.delegates

import com.andreas_kratzer.ghosttalk.core.data.AppStateRepository
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.GetPagesUseCase
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.TemplateRepository
import com.andreas_kratzer.ghosttalk.core.domain.pages.CreatePageUseCase
import com.andreas_kratzer.ghosttalk.core.domain.pages.DeletePageUseCase
import com.andreas_kratzer.ghosttalk.core.domain.pages.DuplicateButtonToPageUseCase
import com.andreas_kratzer.ghosttalk.core.domain.pages.ExportPageUseCase
import com.andreas_kratzer.ghosttalk.core.domain.pages.GetFilteredPagesUseCase
import com.andreas_kratzer.ghosttalk.core.domain.pages.GetPageUsagesUseCase
import com.andreas_kratzer.ghosttalk.core.domain.pages.IdentifyActivePageLinksUseCase
import com.andreas_kratzer.ghosttalk.core.domain.pages.ImportPageUseCase
import com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonToPageUseCase
import com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonUseCase
import com.andreas_kratzer.ghosttalk.core.domain.pages.MoveRowUseCase
import com.andreas_kratzer.ghosttalk.core.domain.pages.UpdateButtonConfigUseCase
import com.andreas_kratzer.ghosttalk.core.domain.pages.UpdateMultipleButtonsUseCase
import com.andreas_kratzer.ghosttalk.core.domain.pages.UpdatePageSettingsUseCase
import com.andreas_kratzer.ghosttalk.core.domain.pages.UpdateRowNameUseCase
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.SortOrder
import io.mockk.coEvery
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
    private lateinit var duplicateButtonToPageUseCase: DuplicateButtonToPageUseCase
    private lateinit var importPageUseCase: ImportPageUseCase
    private lateinit var exportPageUseCase: ExportPageUseCase
    private lateinit var getFilteredPagesUseCase: GetFilteredPagesUseCase
    private val getPageUsagesUseCase: GetPageUsagesUseCase = mockk(relaxed = true)
    private val updateMultipleButtonsUseCase: UpdateMultipleButtonsUseCase = mockk(relaxed = true)
    private val identifyActivePageLinksUseCase: IdentifyActivePageLinksUseCase = mockk(relaxed = true)
    private val appStateRepository: AppStateRepository = mockk(relaxed = true)

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
        duplicateButtonToPageUseCase = mockk(relaxed = true)
        importPageUseCase = mockk(relaxed = true)
        exportPageUseCase = mockk(relaxed = true)
        getFilteredPagesUseCase = GetFilteredPagesUseCase(settingsRepository)

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
            duplicateButtonToPageUseCase,
            importPageUseCase,
            exportPageUseCase,
            getFilteredPagesUseCase,
            getPageUsagesUseCase,
            updateMultipleButtonsUseCase,
            identifyActivePageLinksUseCase,
            appStateRepository,
            settingsRepository
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
        val generatedId = "p-new"
        coEvery { createPageUseCaseMock.execute(any(), any(), any(), any(), any(), any()) } returns generatedId
        val page = Page(id = generatedId, bookId = "book1", name = "New Page", rows = 2, columns = 2, buttonConfigs = emptyList())
        coEvery { pageRepository.getPageById(generatedId) } returns page

        delegate.init(backgroundScope)
        
        delegate.createNewPage("New Page", 2, 2, "book1", null) {}
        
        testScheduler.advanceUntilIdle()
        
        coVerify { createPageUseCaseMock.execute("New Page", 2, 2, "book1", any(), null) }
        coVerify { bookRepository.updateLastModified("book1", any(), any()) }
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
        
        coVerify { deletePageUseCase.execute(page, false) }
    }
    
    @Test
    fun `moveButtonToPage delegates to use case and invokes callback`() = runTest(testDispatcher) {
        delegate.init(backgroundScope)
        val result = MoveButtonToPageUseCase.MoveResult.Success(mockk(), mockk())
        
        coEvery { 
            moveButtonToPageUseCase.execute("p1", 0, "p2", false) 
        } returns result
        
        var receivedResult: MoveButtonToPageUseCase.MoveResult? = null
        delegate.moveButtonToPage("p1", 0, "p2", false) {
            receivedResult = it
        }
        
        coVerify { moveButtonToPageUseCase.execute("p1", 0, "p2", false) }
        assertEquals(result, receivedResult)
    }

    @Test
    fun `duplicateButtonToPage delegates to use case and invokes callback`() = runTest(testDispatcher) {
        delegate.init(backgroundScope)
        val result = MoveButtonToPageUseCase.MoveResult.Success(mockk(), mockk())
        
        coEvery { 
            duplicateButtonToPageUseCase.execute("p1", 0, "p2", false) 
        } returns result
        
        var receivedResult: MoveButtonToPageUseCase.MoveResult? = null
        delegate.duplicateButtonToPage("p1", 0, "p2", false) {
            receivedResult = it
        }
        
        coVerify { duplicateButtonToPageUseCase.execute("p1", 0, "p2", false) }
        assertEquals(result, receivedResult)
    }


    @Test
    fun `moveButtonWithInsert moves button and shifts elements`() = runTest(testDispatcher) {
        val originalConfigs = MutableList<ButtonConfig?>(49) { null }
        val b1 = ButtonConfig(id = "b1", label = "L1")
        val b2 = ButtonConfig(id = "b2", label = "L2")
        val b3 = ButtonConfig(id = "b3", label = "L3")
        originalConfigs[0] = b1
        originalConfigs[1] = b2
        originalConfigs[2] = b3
        
        val page = Page(id = "page1", bookId = "book1", name = "Page 1", rows = 2, columns = 3, buttonConfigs = originalConfigs)
        coEvery { pageRepository.getPageById("page1") } returns page
        
        val updatedPageSlot = io.mockk.slot<Page>()
        coEvery { pageRepository.updatePage(capture(updatedPageSlot)) } returns Unit
        
        delegate.init(backgroundScope)
        
        // Move b1 (0) to index 2 (between 1 and 2, target drop pos 2).
        // Since fromIndex < toIndex, we expect:
        // b2 shifts from 1 to 0.
        // b1 is placed at toIndex - 1 (1).
        // b3 remains at 2.
        delegate.moveButtonWithInsert("page1", 0, 2)
        
        // Wait for coroutine to complete
        testScheduler.advanceUntilIdle()
        
        // Assert by id rather than object equality: the shift logic bumps updatedAt on the
        // moved/shifted buttons, so the copies are no longer structurally equal to the originals.
        val updated = updatedPageSlot.captured
        assertEquals(b2.id, updated.buttonConfigs[0]?.id)
        assertEquals(b1.id, updated.buttonConfigs[1]?.id)
        assertEquals(b3.id, updated.buttonConfigs[2]?.id)
    }

    @Test
    fun `undo restores previous page state`() = runTest(testDispatcher) {
        val originalConfigs = MutableList<ButtonConfig?>(49) { null }
        val b1 = ButtonConfig(id = "b1", label = "L1")
        originalConfigs[0] = b1
        
        val page = Page(id = "page1", bookId = "book1", name = "Page 1", rows = 2, columns = 3, buttonConfigs = originalConfigs)
        coEvery { pageRepository.getPageById("page1") } returns page
        
        val updatedPageSlot = io.mockk.slot<Page>()
        coEvery { pageRepository.updatePage(capture(updatedPageSlot)) } returns Unit
        
        delegate.init(backgroundScope)
        
        // Change button configuration at index 0
        val newConfig = ButtonConfig(id = "b1_new", label = "L1_new")
        delegate.insertButtonConfig("page1", 0, newConfig, forceShift = false) { }
        
        testScheduler.advanceUntilIdle()
        
        // Verify it was updated
        assertEquals(newConfig, updatedPageSlot.captured.buttonConfigs[0])
        
        // Verify we can undo
        assertEquals(true, delegate.history.state.value.canUndo)
        
        // Trigger undo
        delegate.history.undo()
        
        testScheduler.advanceUntilIdle()
        
        // After undo, the repository should have been updated back to the original page state
        assertEquals(b1, updatedPageSlot.captured.buttonConfigs[0])
        assertEquals(false, delegate.history.state.value.canUndo)
    }

    @Test
    fun `createNewPage can be undone`() = runTest(testDispatcher) {
        val generatedId = "p-new"
        coEvery { createPageUseCaseMock.execute(any(), any(), any(), any(), any(), any()) } returns generatedId
        val page = Page(id = generatedId, bookId = "book1", name = "New Page", rows = 2, columns = 3, buttonConfigs = emptyList())
        coEvery { pageRepository.getPageById(generatedId) } returns page

        delegate.init(backgroundScope)

        var createdId: String? = null
        delegate.createNewPage("New Page", 2, 3, "book1") { createdId = it }
        testScheduler.advanceUntilIdle()

        assertEquals(generatedId, createdId)
        assertEquals(true, delegate.history.state.value.canUndo)

        // Undo
        delegate.history.undo()
        testScheduler.advanceUntilIdle()

        // Verify deletePageUseCase was executed for the created page
        coVerify { deletePageUseCase.execute(page, false) }
    }

    @Test
    fun `deletePage can be undone and restores references`() = runTest(testDispatcher) {
        val pageToDelete = Page(id = "del-page", bookId = "book1", name = "To Delete", rows = 2, columns = 3, buttonConfigs = emptyList())
        
        // Mock a referencing page
        val refPage = Page(
            id = "ref-page", 
            bookId = "book1", 
            name = "Ref Page", 
            rows = 2, 
            columns = 3, 
            buttonConfigs = listOf(
                ButtonConfig(id = "b1", label = "Link", buttonAction = com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction("del-page"))
            )
        )
        coEvery { pageRepository.getAllPages() } returns listOf(pageToDelete, refPage)
        coEvery { templateRepository.getAllTemplates() } returns kotlinx.coroutines.flow.MutableStateFlow(emptyList())

        delegate.init(backgroundScope)

        delegate.deletePage(pageToDelete, deleteUsages = true)
        testScheduler.advanceUntilIdle()

        coVerify { deletePageUseCase.execute(pageToDelete, true) }
        assertEquals(true, delegate.history.state.value.canUndo)

        // Undo
        delegate.history.undo()
        testScheduler.advanceUntilIdle()

        // Verify page to delete is re-inserted
        coVerify { pageRepository.insertPage(pageToDelete) }
        // Verify referencing page is updated back to its original state
        coVerify { pageRepository.updatePage(refPage) }
    }
}

package com.andreas_kratzer.ghosttalk.ui.pages.delegates

import com.andreas_kratzer.ghosttalk.core.data.AppStateRepository
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.GetPagesUseCase
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToStartPageButtonAction
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
import com.andreas_kratzer.ghosttalk.core.domain.pages.UsageLocation
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.GridSettingsUpdate
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class PageManagementDelegate @Inject constructor(
    val pageRepository: PageRepository,
    private val bookRepository: BookRepository,
    private val templateRepository: TemplateRepository,
    private val getPagesUseCase: GetPagesUseCase,
    private val createPageUseCase: CreatePageUseCase,
    private val deletePageUseCase: DeletePageUseCase,
    private val updateButtonConfigUseCase: UpdateButtonConfigUseCase,
    private val updatePageSettingsUseCase: UpdatePageSettingsUseCase,
    private val updateRowNameUseCase: UpdateRowNameUseCase,
    private val moveRowUseCase: MoveRowUseCase,
    private val moveButtonUseCase: MoveButtonUseCase,
    private val moveButtonToPageUseCase: MoveButtonToPageUseCase,
    private val duplicateButtonToPageUseCase: DuplicateButtonToPageUseCase,
    private val importPageUseCase: ImportPageUseCase,
    private val exportPageUseCase: ExportPageUseCase,
    private val getFilteredPagesUseCase: GetFilteredPagesUseCase,
    private val getPageUsagesUseCase: GetPageUsagesUseCase,
    private val updateMultipleButtonsUseCase: UpdateMultipleButtonsUseCase,
    private val identifyActivePageLinksUseCase: IdentifyActivePageLinksUseCase,
    private val appStateRepository: AppStateRepository,
    private val settingsRepository: SettingsRepository
) {
    private lateinit var scope: CoroutineScope

    val activeBookId: StateFlow<String?> = appStateRepository.activeBookId
    val currentPageId: StateFlow<String?> = appStateRepository.currentPageId

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _allPages = MutableStateFlow<List<Page>>(emptyList())
    val allPagesFlow: StateFlow<List<Page>> = _allPages.asStateFlow()
    
    // We keep a separate flow here for the full Page object,
    // while appStateRepository only holds the ID for basic logic.
    // This could also be refined later.
    private val _currentPage = MutableStateFlow<Page?>(null)
    val currentPage: StateFlow<Page?> = _currentPage.asStateFlow()

    private val _filteredPages = MutableStateFlow<List<Page>>(emptyList())
    val filteredPages: StateFlow<List<Page>> = _filteredPages.asStateFlow()

    private val _unfilteredPages = MutableStateFlow<List<Page>>(emptyList())
    val unfilteredPages: StateFlow<List<Page>> = _unfilteredPages.asStateFlow()

    private val _templates = MutableStateFlow<List<PageTemplate>>(emptyList())
    val templates: StateFlow<List<PageTemplate>> = _templates.asStateFlow()

    private val _activeTargetPageIds = MutableStateFlow<Set<String>>(emptySet())
    val activeTargetPageIds: StateFlow<Set<String>> = _activeTargetPageIds.asStateFlow()

    private val undoStack = mutableListOf<Page>()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    fun saveUndoState(page: Page) {
        if (undoStack.size >= 10) {
            undoStack.removeAt(0)
        }
        // Save a deep copy of the buttonConfigs since they are mutable/nullable list
        undoStack.add(page.copy(buttonConfigs = page.buttonConfigs.toList()))
        _canUndo.value = true
    }

    private fun saveUndoStateForPage(pageId: String) {
        scope.launch {
            pageRepository.getPageById(pageId)?.let { page ->
                saveUndoState(page)
            }
        }
    }

    fun undo(onSuccess: (String) -> Unit = {}) {
        if (undoStack.isNotEmpty()) {
            val previousState = undoStack.removeLast()
            if (undoStack.isEmpty()) {
                _canUndo.value = false
            }
            scope.launch {
                pageRepository.updatePage(previousState)
                bookRepository.updateLastModified(previousState.bookId)
                if (_currentPage.value?.id == previousState.id) {
                    setCurrentPage(previousState)
                }
                onSuccess("Aktion rückgängig gemacht")
            }
        }
    }

    fun init(scope: CoroutineScope) {
        this.scope = scope

        scope.launch {
            migrateStartPageButtons()
        }

        scope.launch {
            getFilteredPagesUseCase.execute(_allPages, _searchQuery, _activeTargetPageIds)
                .collect { _filteredPages.value = it }
        }

        scope.launch {
            getFilteredPagesUseCase.execute(_allPages, MutableStateFlow(""), _activeTargetPageIds)
                .collect { _unfilteredPages.value = it }
        }

        scope.launch {
            templateRepository.getAllTemplates().collect { _templates.value = it }
        }

        scope.launch {
            getPagesUseCase.execute(appStateRepository.activeBookId).collect { pages ->
                _allPages.value = pages
            }
        }

        scope.launch {
            kotlinx.coroutines.flow.combine(
                _allPages,
                _templates,
                settingsRepository.defaultStartPageIdFlow
            ) { pages, templates, defaultStartPageId ->
                val ids = identifyActivePageLinksUseCase.execute(pages, templates).toMutableSet()
                // The default start page is always considered active/reachable
                defaultStartPageId?.let { ids.add(it) }
                ids
            }.collect { ids ->
                _activeTargetPageIds.value = ids
            }
        }
    }

    fun setActiveBookId(bookId: String?) {
        appStateRepository.setActiveBookId(bookId)
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCurrentPage(page: Page?) {
        _currentPage.value = page
        appStateRepository.setCurrentPageId(page?.id)
        // No need to save to SavedStateHandle here if appStateRepository does it, 
        // but since we want to be sure for PageViewModel, we'll do it in VM or here.
    }

    fun createNewPage(name: String, rows: Int, columns: Int, bookId: String, templateId: String? = null, onCreated: (String) -> Unit) {
        scope.launch {
            val generatedId = createPageUseCase.execute(name, rows, columns, bookId, _allPages.value, templateId)
            bookRepository.updateLastModified(bookId)
            onCreated(generatedId)
        }
    }

    fun updateButtonConfig(pageId: String, index: Int, newConfig: ButtonConfig?) {
        saveUndoStateForPage(pageId)
        scope.launch {
            val updatedPage = updateButtonConfigUseCase.execute(pageId, index, newConfig)
            if (updatedPage != null && _currentPage.value?.id == pageId) {
                setCurrentPage(updatedPage)
            }
        }
    }

    fun insertButtonConfig(pageId: String, index: Int, newConfig: ButtonConfig, forceShift: Boolean = false, onResult: (Boolean) -> Unit = {}) {
        saveUndoStateForPage(pageId)
        scope.launch {
            val page = pageRepository.getPageById(pageId) ?: return@launch
            val newButtonConfigs = page.buttonConfigs.toMutableList()
            
            // Ensure 49 slots
            while (newButtonConfigs.size < com.andreas_kratzer.ghosttalk.core.util.GridUtils.TOTAL_SLOTS) {
                newButtonConfigs.add(null)
            }
            
            if (index in newButtonConfigs.indices) {
                // Check if the entire 49 slots are completely full
                if ((newButtonConfigs[index] != null || forceShift) && newButtonConfigs.none { it == null }) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onResult(false)
                    }
                    return@launch
                }
                
                if (newButtonConfigs[index] == null && !forceShift) {
                    // Target is empty, just replace
                    newButtonConfigs[index] = newConfig
                } else {
                    // Target is not empty or we force shift, shift items down following the visible layout flow
                    val visibleIndices = mutableListOf<Int>()
                    for (r in 0 until page.rows) {
                        for (c in 0 until page.columns) {
                            visibleIndices.add(r * com.andreas_kratzer.ghosttalk.core.util.GridUtils.MAX_GRID_SIZE + c)
                        }
                    }
                    
                    val dropVisiblePos = visibleIndices.indexOf(index)
                    if (dropVisiblePos != -1) {
                        val lastVisibleGlobal = visibleIndices.last()
                        val lastItem = newButtonConfigs[lastVisibleGlobal]
                        
                        // Shift visible items down by 1
                        for (i in visibleIndices.size - 1 downTo dropVisiblePos + 1) {
                            val currentGlobal = visibleIndices[i]
                            val prevGlobal = visibleIndices[i - 1]
                            newButtonConfigs[currentGlobal] = newButtonConfigs[prevGlobal]
                        }
                        
                        // Rescue the last item by placing it in the first available invisible slot
                        if (lastItem != null) {
                            for (i in 0 until com.andreas_kratzer.ghosttalk.core.util.GridUtils.TOTAL_SLOTS) {
                                if (i !in visibleIndices && newButtonConfigs[i] == null) {
                                    newButtonConfigs[i] = lastItem
                                    break
                                }
                            }
                        }
                    }
                    
                    // Insert new config
                    newButtonConfigs[index] = newConfig
                }
                
                val updatedPage = page.copy(buttonConfigs = newButtonConfigs)
                pageRepository.updatePage(updatedPage)
                bookRepository.updateLastModified(page.bookId)
                
                if (_currentPage.value?.id == pageId) {
                    setCurrentPage(updatedPage)
                }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(true)
                }
            }
        }
    }

    fun updatePageSettings(
        pageId: String, 
        update: GridSettingsUpdate
    ) {
        saveUndoStateForPage(pageId)
        scope.launch {
            val updatedPage = updatePageSettingsUseCase.execute(
                pageId, 
                update.name, 
                update.scanPattern, 
                update.rowNames, 
                update.rows, 
                update.columns
            )
            if (updatedPage != null && _currentPage.value?.id == pageId) {
                setCurrentPage(updatedPage)
            }
        }
    }

    fun updateRowName(pageId: String, rowIndex: Int, newName: String) {
        saveUndoStateForPage(pageId)
        scope.launch {
            val updatedPage = updateRowNameUseCase.execute(pageId, rowIndex, newName)
            if (updatedPage != null && _currentPage.value?.id == pageId) {
                setCurrentPage(updatedPage)
            }
        }
    }

    fun moveRow(pageId: String, fromRow: Int, toRow: Int) {
        saveUndoStateForPage(pageId)
        scope.launch {
            val updatedPage = moveRowUseCase.execute(pageId, fromRow, toRow)
            if (updatedPage != null && _currentPage.value?.id == pageId) {
                setCurrentPage(updatedPage)
            }
        }
    }

    fun moveButton(pageId: String, fromIndex: Int, toIndex: Int) {
        saveUndoStateForPage(pageId)
        scope.launch {
            val updatedPage = moveButtonUseCase.execute(pageId, fromIndex, toIndex)
            if (updatedPage != null && _currentPage.value?.id == pageId) {
                setCurrentPage(updatedPage)
            }
        }
    }

    fun moveButtonWithInsert(pageId: String, fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex || fromIndex == toIndex - 1) {
            return
        }
        saveUndoStateForPage(pageId)
        scope.launch {
            val page = pageRepository.getPageById(pageId) ?: return@launch
            val newButtonConfigs = page.buttonConfigs.toMutableList()
            
            // Ensure 49 slots
            while (newButtonConfigs.size < com.andreas_kratzer.ghosttalk.core.util.GridUtils.TOTAL_SLOTS) {
                newButtonConfigs.add(null)
            }
            
            val visibleIndices = mutableListOf<Int>()
            for (r in 0 until page.rows) {
                for (c in 0 until page.columns) {
                    visibleIndices.add(r * com.andreas_kratzer.ghosttalk.core.util.GridUtils.MAX_GRID_SIZE + c)
                }
            }
            
            if (fromIndex in visibleIndices && toIndex <= visibleIndices.size) {
                val movedItem = newButtonConfigs[fromIndex] ?: return@launch
                
                // Clear the source
                newButtonConfigs[fromIndex] = null
                
                if (fromIndex < toIndex) {
                    // Shift items between fromIndex + 1 and toIndex - 1 to the left
                    for (i in fromIndex until toIndex - 1) {
                        if (i < visibleIndices.size - 1) {
                            val currentGlobal = visibleIndices[i]
                            val nextGlobal = visibleIndices[i + 1]
                            newButtonConfigs[currentGlobal] = newButtonConfigs[nextGlobal]
                        }
                    }
                    // Insert the moved item at toIndex - 1
                    val targetGlobal = visibleIndices[toIndex - 1]
                    newButtonConfigs[targetGlobal] = movedItem
                } else if (fromIndex > toIndex) {
                    // Shift items between toIndex and fromIndex - 1 to the right
                    for (i in fromIndex downTo toIndex + 1) {
                        val currentGlobal = visibleIndices[i]
                        val prevGlobal = visibleIndices[i - 1]
                        newButtonConfigs[currentGlobal] = newButtonConfigs[prevGlobal]
                    }
                    // Insert the moved item at toIndex
                    val targetGlobal = visibleIndices[toIndex]
                    newButtonConfigs[targetGlobal] = movedItem
                }
                
                val updatedPage = page.copy(buttonConfigs = newButtonConfigs)
                pageRepository.updatePage(updatedPage)
                bookRepository.updateLastModified(page.bookId)
                
                if (_currentPage.value?.id == pageId) {
                    setCurrentPage(updatedPage)
                }
            }
        }
    }

    fun moveButtonToPage(
        fromPageId: String,
        fromIndex: Int,
        toPageId: String,
        forceMove: Boolean = false,
        onResult: (MoveButtonToPageUseCase.MoveResult) -> Unit
    ) {
        scope.launch {
            val result = moveButtonToPageUseCase.execute(fromPageId, fromIndex, toPageId, forceMove)
            if (result is MoveButtonToPageUseCase.MoveResult.Success) {
                if (_currentPage.value?.id == fromPageId) {
                    setCurrentPage(result.fromPage)
                } else if (_currentPage.value?.id == toPageId) {
                    setCurrentPage(result.toPage)
                }
            }
            onResult(result)
        }
    }

    fun duplicateButtonToPage(
        fromPageId: String,
        fromIndex: Int,
        toPageId: String,
        forceMove: Boolean = false,
        onResult: (MoveButtonToPageUseCase.MoveResult) -> Unit
    ) {
        scope.launch {
            val result = duplicateButtonToPageUseCase.execute(fromPageId, fromIndex, toPageId, forceMove)
            if (result is MoveButtonToPageUseCase.MoveResult.Success) {
                if (_currentPage.value?.id == toPageId) {
                    setCurrentPage(result.toPage)
                }
            }
            onResult(result)
        }
    }

    fun deletePage(page: Page, deleteUsages: Boolean = false) {
        scope.launch {
            deletePageUseCase.execute(page, deleteUsages)
        }
    }


    fun importFromJson(
        jsonString: String, 
        bookId: String, 
        regenerateIds: Boolean? = true, 
        restoreSyncSettings: Boolean = true,
        onSuccess: () -> Unit, 
        onError: (String) -> Unit
    ) {
        scope.launch {
            val result = importPageUseCase.execute(jsonString, bookId, regenerateIds, restoreSyncSettings)
            result.onSuccess { onSuccess() }.onFailure { e -> onError("Fehler beim Import: ${e.message}") }
        }
    }

    suspend fun exportToJson(): String {
        return exportPageUseCase.execute(_allPages.value)
    }

    suspend fun getPageUsages(pageId: String): List<UsageLocation> {
        return getPageUsagesUseCase.execute(pageId)
    }

    fun activateButtons(usages: List<UsageLocation>, isActive: Boolean) {
        scope.launch {
            updateMultipleButtonsUseCase.execute(usages, isActive)
        }
    }

    suspend fun getPageById(id: String): Page? = pageRepository.getPageById(id)

    private fun migrateStartPageButtons() {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val books = bookRepository.getAllBooksList()
                books.forEach { book ->
                    val startPageId = settingsRepository.getDefaultStartPageIdForBook(book.id)
                    if (!startPageId.isNullOrEmpty()) {
                        val pages = pageRepository.getPagesForBook(book.id)
                        pages.forEach { page ->
                            var updated = false
                            val updatedConfigs = page.buttonConfigs.map { config ->
                                if (config != null) {
                                    val action = config.buttonAction
                                    if (action is NavigateToPageButtonAction && (action.pageId.isEmpty() || action.pageId == startPageId)) {
                                        updated = true
                                        config.copy(buttonAction = NavigateToStartPageButtonAction())
                                    } else {
                                        config
                                    }
                                } else {
                                    null
                                }
                            }
                            if (updated) {
                                pageRepository.updatePage(page.copy(buttonConfigs = updatedConfigs))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PageManagementDelegate", "Fehler bei der Startseiten-Button Migration", e)
            }
        }
    }
}

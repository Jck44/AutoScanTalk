package com.andreas_kratzer.ghosttalk.ui.pages.delegates

import com.andreas_kratzer.ghosttalk.data.BookRepository
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.data.TemplateRepository
import com.andreas_kratzer.ghosttalk.domain.pages.CreatePageUseCase
import com.andreas_kratzer.ghosttalk.domain.pages.DeletePageUseCase
import com.andreas_kratzer.ghosttalk.domain.pages.ExportPageUseCase
import com.andreas_kratzer.ghosttalk.domain.pages.GetFilteredPagesUseCase
import com.andreas_kratzer.ghosttalk.domain.pages.GetPagesUseCase
import com.andreas_kratzer.ghosttalk.domain.pages.ImportPageUseCase
import com.andreas_kratzer.ghosttalk.domain.pages.ReorderPagesUseCase
import com.andreas_kratzer.ghosttalk.domain.pages.UpdateButtonConfigUseCase
import com.andreas_kratzer.ghosttalk.domain.pages.UpdatePageSettingsUseCase
import com.andreas_kratzer.ghosttalk.domain.pages.UpdateRowNameUseCase
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.PageTemplate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class PageManagementDelegate @Inject constructor(
    private val pageRepository: PageRepository,
    private val bookRepository: BookRepository,
    private val settingsRepository: SettingsRepository,
    private val templateRepository: TemplateRepository,
    private val getPagesUseCase: GetPagesUseCase,
    private val createPageUseCase: CreatePageUseCase,
    private val deletePageUseCase: DeletePageUseCase,
    private val reorderPagesUseCase: ReorderPagesUseCase,
    private val updateButtonConfigUseCase: UpdateButtonConfigUseCase,
    private val updatePageSettingsUseCase: UpdatePageSettingsUseCase,
    private val updateRowNameUseCase: UpdateRowNameUseCase,
    private val importPageUseCase: ImportPageUseCase,
    private val exportPageUseCase: ExportPageUseCase,
    private val getFilteredPagesUseCase: GetFilteredPagesUseCase
) {
    private lateinit var scope: CoroutineScope

    private val _activeBookId = MutableStateFlow<String?>(null)
    val activeBookId: StateFlow<String?> = _activeBookId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _allPages = MutableStateFlow<List<Page>>(emptyList())
    val allPagesFlow: StateFlow<List<Page>> = _allPages.asStateFlow()
    
    private val _currentPage = MutableStateFlow<Page?>(null)
    val currentPage: StateFlow<Page?> = _currentPage.asStateFlow()

    private val _filteredPages = MutableStateFlow<List<Page>>(emptyList())
    val filteredPages: StateFlow<List<Page>> = _filteredPages.asStateFlow()

    private val _unfilteredPages = MutableStateFlow<List<Page>>(emptyList())
    val unfilteredPages: StateFlow<List<Page>> = _unfilteredPages.asStateFlow()

    private val _templates = MutableStateFlow<List<PageTemplate>>(emptyList())
    val templates: StateFlow<List<PageTemplate>> = _templates.asStateFlow()

    fun init(scope: CoroutineScope) {
        this.scope = scope

        scope.launch {
            getFilteredPagesUseCase.execute(_allPages, _searchQuery)
                .collect { _filteredPages.value = it }
        }

        scope.launch {
            getFilteredPagesUseCase.execute(_allPages, MutableStateFlow(""))
                .collect { _unfilteredPages.value = it }
        }

        scope.launch {
            templateRepository.getAllTemplates().collect { _templates.value = it }
        }

        scope.launch {
            getPagesUseCase.execute(_activeBookId).collect { pages ->
                _allPages.value = pages
            }
        }
    }

    fun setActiveBookId(bookId: String?) {
        _activeBookId.value = bookId
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCurrentPage(page: Page?) {
        _currentPage.value = page
    }

    fun createNewPage(name: String, rows: Int, columns: Int, bookId: String, templateId: String? = null, onCreated: (String) -> Unit) {
        scope.launch {
            val generatedId = createPageUseCase.execute(name, rows, columns, bookId, _allPages.value, templateId)
            bookRepository.updateLastModified(bookId)
            onCreated(generatedId)
        }
    }

    fun updateButtonConfig(pageId: String, index: Int, newConfig: ButtonConfig?) {
        scope.launch {
            val updatedPage = updateButtonConfigUseCase.execute(pageId, index, newConfig)
            if (updatedPage != null && _currentPage.value?.id == pageId) {
                _currentPage.value = updatedPage
            }
        }
    }

    fun updatePageSettings(pageId: String, newName: String, newScanPattern: String?, newRowNames: List<String>) {
        scope.launch {
            val updatedPage = updatePageSettingsUseCase.execute(pageId, newName, newScanPattern, newRowNames)
            if (updatedPage != null && _currentPage.value?.id == pageId) {
                _currentPage.value = updatedPage
            }
        }
    }

    fun updateRowName(pageId: String, rowIndex: Int, newName: String) {
        scope.launch {
            val updatedPage = updateRowNameUseCase.execute(pageId, rowIndex, newName)
            if (updatedPage != null && _currentPage.value?.id == pageId) {
                _currentPage.value = updatedPage
            }
        }
    }

    fun deletePage(page: Page) {
        scope.launch {
            deletePageUseCase.execute(page)
        }
    }

    fun reorderPages(fromIndex: Int, toIndex: Int) {
        scope.launch {
            reorderPagesUseCase.execute(_allPages.value, fromIndex, toIndex, _activeBookId.value)
        }
    }

    fun importFromJson(jsonString: String, bookId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        scope.launch {
            val result = importPageUseCase.execute(jsonString, bookId)
            result.onSuccess { onSuccess() }.onFailure { e -> onError("Fehler beim Import: ${e.message}") }
        }
    }

    suspend fun exportToJson(): String {
        return exportPageUseCase.execute(_allPages.value)
    }

    suspend fun getPageById(id: String): Page? = pageRepository.getPageById(id)
}

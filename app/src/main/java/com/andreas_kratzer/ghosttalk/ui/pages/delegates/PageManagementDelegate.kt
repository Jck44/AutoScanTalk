package com.andreas_kratzer.ghosttalk.ui.pages.delegates

import com.andreas_kratzer.ghosttalk.R
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
import com.andreas_kratzer.ghosttalk.core.domain.pages.InsertButtonConfigUseCase
import com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonWithInsertUseCase
import com.andreas_kratzer.ghosttalk.core.domain.pages.MoveRowUseCase
import com.andreas_kratzer.ghosttalk.core.domain.pages.UpdateButtonConfigUseCase
import com.andreas_kratzer.ghosttalk.core.domain.pages.UpdateMultipleButtonsUseCase
import com.andreas_kratzer.ghosttalk.core.domain.pages.UpdatePageSettingsUseCase
import com.andreas_kratzer.ghosttalk.core.domain.pages.UpdateRowNameUseCase
import com.andreas_kratzer.ghosttalk.core.domain.pages.UsageLocation
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.GridSettingsUpdate
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToStartPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.OptionalProperty
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import com.andreas_kratzer.ghosttalk.ui.pages.history.CreatePageCommand
import com.andreas_kratzer.ghosttalk.ui.pages.history.DeletePageCommand
import com.andreas_kratzer.ghosttalk.ui.pages.history.DuplicateButtonToPageCommand
import com.andreas_kratzer.ghosttalk.ui.pages.history.EditCommand
import com.andreas_kratzer.ghosttalk.ui.pages.history.EditHistory
import com.andreas_kratzer.ghosttalk.ui.pages.history.EditIcon
import com.andreas_kratzer.ghosttalk.ui.pages.history.EditLabel
import com.andreas_kratzer.ghosttalk.ui.pages.history.MoveButtonCommand
import com.andreas_kratzer.ghosttalk.ui.pages.history.MoveButtonToPageCommand
import com.andreas_kratzer.ghosttalk.ui.pages.history.PageSnapshotCommand
import com.andreas_kratzer.ghosttalk.ui.pages.history.UpdateButtonConfigCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PageManagementDelegate @Inject constructor(
    val pageRepository: PageRepository,
    val bookRepository: BookRepository,
    val templateRepository: TemplateRepository,
    private val getPagesUseCase: GetPagesUseCase,
    val createPageUseCase: CreatePageUseCase,
    val deletePageUseCase: DeletePageUseCase,
    val updateButtonConfigUseCase: UpdateButtonConfigUseCase,
    private val updatePageSettingsUseCase: UpdatePageSettingsUseCase,
    private val updateRowNameUseCase: UpdateRowNameUseCase,
    private val moveRowUseCase: MoveRowUseCase,
    val moveButtonUseCase: MoveButtonUseCase,
    val moveButtonToPageUseCase: MoveButtonToPageUseCase,
    val duplicateButtonToPageUseCase: DuplicateButtonToPageUseCase,
    private val importPageUseCase: ImportPageUseCase,
    private val exportPageUseCase: ExportPageUseCase,
    private val getFilteredPagesUseCase: GetFilteredPagesUseCase,
    private val getPageUsagesUseCase: GetPageUsagesUseCase,
    private val updateMultipleButtonsUseCase: UpdateMultipleButtonsUseCase,
    private val identifyActivePageLinksUseCase: IdentifyActivePageLinksUseCase,
    private val appStateRepository: AppStateRepository,
    private val settingsRepository: SettingsRepository,
    val insertButtonConfigUseCase: InsertButtonConfigUseCase,
    val moveButtonWithInsertUseCase: MoveButtonWithInsertUseCase
) {
    private lateinit var scope: CoroutineScope
    lateinit var history: EditHistory
    private val mutex get() = history.mutex

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

    fun init(scope: CoroutineScope) {
        this.scope = scope
        this.history = EditHistory(
            limit = 50,
            onPageNavigate = { targetPageId ->
                if (_currentPage.value?.id != targetPageId) {
                    pageRepository.getPageById(targetPageId)?.let { page ->
                        setCurrentPage(page)
                    }
                }
            }
        )

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
        setCurrentPage(null)
        if (::history.isInitialized) {
            history.reset()
        }
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
            mutex.withLock {
                val command = CreatePageCommand(
                    delegate = this@PageManagementDelegate,
                    name = name,
                    rows = rows,
                    columns = columns,
                    bookId = bookId,
                    templateId = templateId,
                    onCreated = onCreated
                )
                history.execute(command)
            }
        }
    }

    fun updateButtonConfig(pageId: String, index: Int, newConfig: ButtonConfig?) {
        scope.launch {
            mutex.withLock {
                val page = pageRepository.getPageById(pageId) ?: return@withLock
                val oldConfig = page.buttonConfigs.getOrNull(index)
                val labelRes = if (newConfig == null) {
                    R.string.history_delete_button
                } else if (oldConfig == null) {
                    R.string.history_add_button
                } else {
                    R.string.history_edit_button
                }
                val labelArg = newConfig?.label ?: oldConfig?.label ?: ""
                val command = UpdateButtonConfigCommand(
                    delegate = this@PageManagementDelegate,
                    pageId = pageId,
                    index = index,
                    oldConfig = oldConfig,
                    newConfig = newConfig,
                    label = EditLabel(labelRes, listOf(labelArg)),
                    icon = if (newConfig == null) EditIcon.DELETE else EditIcon.EDIT
                )
                history.execute(command)
            }
        }
    }

    fun bulkDeleteButtons(pageId: String, indices: List<Int>) {
        scope.launch {
            mutex.withLock {
                val page = pageRepository.getPageById(pageId) ?: return@withLock
                val oldConfigs = page.buttonConfigs.toList()
                val newConfigs = oldConfigs.toMutableList()
                
                indices.forEach { idx ->
                    if (idx in newConfigs.indices) {
                        newConfigs[idx] = null
                    }
                }
                
                val command = PageSnapshotCommand(
                    delegate = this@PageManagementDelegate,
                    pageId = pageId,
                    oldConfigs = oldConfigs,
                    newConfigs = newConfigs,
                    label = EditLabel(R.string.bulk_action_delete),
                    icon = EditIcon.DELETE
                )
                history.execute(command)
            }
        }
    }

    fun insertButtonConfig(pageId: String, index: Int, newConfig: ButtonConfig, forceShift: Boolean = false, onResult: (Boolean) -> Unit = {}) {
        scope.launch {
            mutex.withLock {
                val page = pageRepository.getPageById(pageId) ?: return@withLock
                val oldConfigs = page.buttonConfigs.toList()
                
                when (val result = insertButtonConfigUseCase.execute(page, index, newConfig, forceShift)) {
                    is InsertButtonConfigUseCase.Result.Success -> {
                        val labelVal = newConfig.label
                        val command = PageSnapshotCommand(
                            delegate = this@PageManagementDelegate,
                            pageId = pageId,
                            oldConfigs = oldConfigs,
                            newConfigs = result.newConfigs,
                            label = EditLabel(R.string.history_insert_button, listOf(labelVal)),
                            icon = EditIcon.EDIT
                        )
                        history.execute(command)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            onResult(true)
                        }
                    }
                    else -> {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            onResult(false)
                        }
                    }
                }
            }
        }
    }

    fun updatePageSettings(
        pageId: String, 
        update: GridSettingsUpdate
    ) {
        scope.launch {
            mutex.withLock {
                val page = pageRepository.getPageById(pageId) ?: return@withLock
                val oldName = page.name
                val oldScanPattern = page.scanPattern
                val oldRowNames = page.rowNames
                val oldRows = page.rows
                val oldColumns = page.columns

                class UpdateSettingsCommand : EditCommand {
                    override val pageId: String get() = pageId
                    override val label = EditLabel(R.string.history_update_settings, listOf(update.name ?: page.name))
                    override val icon = EditIcon.PAGE

                    override suspend fun apply() {
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

                    override suspend fun revert() {
                        val updatedPage = updatePageSettingsUseCase.execute(
                            pageId, 
                            oldName, 
                            OptionalProperty(oldScanPattern), 
                            oldRowNames, 
                            oldRows, 
                            oldColumns
                        )
                        if (updatedPage != null && _currentPage.value?.id == pageId) {
                            setCurrentPage(updatedPage)
                        }
                    }
                }

                history.execute(UpdateSettingsCommand())
            }
        }
    }

    fun updateRowName(pageId: String, rowIndex: Int, newName: String) {
        scope.launch {
            mutex.withLock {
                val page = pageRepository.getPageById(pageId) ?: return@withLock
                val oldName = page.rowNames.getOrNull(rowIndex) ?: ""

                class UpdateRowNameCommand : EditCommand {
                    override val pageId: String get() = pageId
                    override val label = EditLabel(R.string.history_update_row_name, listOf(rowIndex + 1, newName))
                    override val icon = EditIcon.EDIT

                    override suspend fun apply() {
                        val updatedPage = updateRowNameUseCase.execute(pageId, rowIndex, newName)
                        if (updatedPage != null && _currentPage.value?.id == pageId) {
                            setCurrentPage(updatedPage)
                        }
                    }

                    override suspend fun revert() {
                        val updatedPage = updateRowNameUseCase.execute(pageId, rowIndex, oldName)
                        if (updatedPage != null && _currentPage.value?.id == pageId) {
                            setCurrentPage(updatedPage)
                        }
                    }
                }

                history.execute(UpdateRowNameCommand())
            }
        }
    }

    fun moveRow(pageId: String, fromRow: Int, toRow: Int) {
        scope.launch {
            mutex.withLock {
                class MoveRowCommand : EditCommand {
                    override val pageId: String get() = pageId
                    override val label = EditLabel(R.string.history_move_row, listOf(fromRow + 1, toRow + 1))
                    override val icon = EditIcon.REORDER

                    override suspend fun apply() {
                        val updatedPage = moveRowUseCase.execute(pageId, fromRow, toRow)
                        if (updatedPage != null && _currentPage.value?.id == pageId) {
                            setCurrentPage(updatedPage)
                        }
                    }

                    override suspend fun revert() {
                        val updatedPage = moveRowUseCase.execute(pageId, toRow, fromRow)
                        if (updatedPage != null && _currentPage.value?.id == pageId) {
                            setCurrentPage(updatedPage)
                        }
                    }
                }

                history.execute(MoveRowCommand())
            }
        }
    }

    fun moveButton(pageId: String, fromIndex: Int, toIndex: Int) {
        scope.launch {
            mutex.withLock {
                val page = pageRepository.getPageById(pageId) ?: return@withLock
                val button = page.buttonConfigs.getOrNull(fromIndex)
                val labelVal = button?.label ?: ""
                val command = MoveButtonCommand(
                    delegate = this@PageManagementDelegate,
                    pageId = pageId,
                    fromIndex = fromIndex,
                    toIndex = toIndex,
                    label = EditLabel(R.string.history_move_button, listOf(labelVal, fromIndex + 1, toIndex + 1))
                )
                history.execute(command)
            }
        }
    }

    fun moveButtonWithInsert(pageId: String, fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex || fromIndex == toIndex - 1) {
            return
        }
        scope.launch {
            mutex.withLock {
                val page = pageRepository.getPageById(pageId) ?: return@withLock
                val oldConfigs = page.buttonConfigs.toList()
                
                when (val result = moveButtonWithInsertUseCase.execute(page, fromIndex, toIndex)) {
                    is MoveButtonWithInsertUseCase.Result.Success -> {
                        val command = PageSnapshotCommand(
                            delegate = this@PageManagementDelegate,
                            pageId = pageId,
                            oldConfigs = oldConfigs,
                            newConfigs = result.newConfigs,
                            label = EditLabel(R.string.history_move_button_with_insert, listOf(result.movedButtonLabel)),
                            icon = EditIcon.MOVE
                        )
                        history.execute(command)
                    }
                    MoveButtonWithInsertUseCase.Result.Error -> {
                        // ignore or handle error
                    }
                }
            }
        }
    }

    fun moveButtonToPage(
        fromPageId: String,
        fromIndices: List<Int>,
        toPageId: String,
        forceMove: Boolean = false,
        onResult: (MoveButtonToPageUseCase.MoveResult) -> Unit
    ) {
        scope.launch {
            mutex.withLock {
                val fromPage = pageRepository.getPageById(fromPageId) ?: return@withLock
                val buttonLabelArg = if (fromIndices.size == 1) {
                    fromPage.buttonConfigs.getOrNull(fromIndices.first())?.label ?: ""
                } else {
                    EditLabel(
                        resId = R.plurals.bulk_action_move_buttons,
                        args = listOf(fromIndices.size),
                        isPlural = true,
                        quantity = fromIndices.size
                    )
                }
                val toPage = pageRepository.getPageById(toPageId)
                val toPageName = toPage?.name ?: ""

                val command = MoveButtonToPageCommand(
                    delegate = this@PageManagementDelegate,
                    fromPageId = fromPageId,
                    fromIndices = fromIndices,
                    toPageId = toPageId,
                    forceMove = forceMove,
                    label = EditLabel(R.string.history_move_button_to_page, listOf(buttonLabelArg, toPageName)),
                    onResult = onResult
                )
                history.execute(command)
            }
        }
    }

    fun duplicateButtonToPage(
        fromPageId: String,
        fromIndices: List<Int>,
        toPageId: String,
        forceMove: Boolean = false,
        onResult: (MoveButtonToPageUseCase.MoveResult) -> Unit
    ) {
        scope.launch {
            mutex.withLock {
                val fromPage = pageRepository.getPageById(fromPageId) ?: return@withLock
                val buttonLabelArg = if (fromIndices.size == 1) {
                    fromPage.buttonConfigs.getOrNull(fromIndices.first())?.label ?: ""
                } else {
                    EditLabel(
                        resId = R.plurals.bulk_action_move_buttons,
                        args = listOf(fromIndices.size),
                        isPlural = true,
                        quantity = fromIndices.size
                    )
                }
                val toPage = pageRepository.getPageById(toPageId)
                val toPageName = toPage?.name ?: ""

                val command = DuplicateButtonToPageCommand(
                    delegate = this@PageManagementDelegate,
                    fromPageId = fromPageId,
                    fromIndices = fromIndices,
                    toPageId = toPageId,
                    forceMove = forceMove,
                    label = EditLabel(R.string.history_duplicate_button_to_page, listOf(buttonLabelArg, toPageName)),
                    onResult = onResult
                )
                history.execute(command)
            }
        }
    }

    fun deletePage(page: Page, deleteUsages: Boolean = false) {
        scope.launch {
            mutex.withLock {
                val command = DeletePageCommand(
                    delegate = this@PageManagementDelegate,
                    page = page,
                    deleteUsages = deleteUsages
                )
                history.execute(command)
            }
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

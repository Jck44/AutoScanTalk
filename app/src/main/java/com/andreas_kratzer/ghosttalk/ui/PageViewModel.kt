package com.andreas_kratzer.ghosttalk.ui

import android.app.Application
import android.content.Intent
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andreas_kratzer.ghosttalk.core.ActionExecutor
import com.andreas_kratzer.ghosttalk.core.FrequentActionResolver
import com.andreas_kratzer.ghosttalk.core.PageImportExportManager
import com.andreas_kratzer.ghosttalk.core.ScannerEngine
import com.andreas_kratzer.ghosttalk.core.cloud.DriveAuthManager
import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.andreas_kratzer.ghosttalk.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.data.TemplateRepository
import com.andreas_kratzer.ghosttalk.domain.ActionLogUseCase
import com.andreas_kratzer.ghosttalk.domain.CreatePageUseCase
import com.andreas_kratzer.ghosttalk.domain.GeminiUseCase
import com.andreas_kratzer.ghosttalk.domain.GeminiUseCaseFactory
import com.andreas_kratzer.ghosttalk.domain.GetPagesUseCase
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.PageTemplate
import com.andreas_kratzer.ghosttalk.model.SortOrder
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PageViewModel @Inject constructor(
    application: Application,
    private val pageRepository: PageRepository,
    private val settingsRepository: SettingsRepository,
    private val importExportManager: PageImportExportManager,
    private val getPagesUseCase: GetPagesUseCase,
    private val actionLogUseCase: ActionLogUseCase,
    private val createPageUseCase: CreatePageUseCase,
    private val frequentActionResolver: FrequentActionResolver,
    private val buttonUsageRepository: ButtonUsageRepository,
    private val templateRepository: TemplateRepository,
    private val driveAuthManager: DriveAuthManager,
    private val logger: Logger,
    private val geminiUseCaseFactory: GeminiUseCaseFactory,
    private val ttsHelper: TextToSpeechHelper
) : AndroidViewModel(application) {

    private var geminiUseCase: GeminiUseCase? = null

    private val _activeBookId = MutableStateFlow<String?>(null)
    val activeBookId: StateFlow<String?> = _activeBookId.asStateFlow()

    private val _allPages = MutableStateFlow<List<Page>>(emptyList())
    val allPages: StateFlow<List<Page>> = kotlinx.coroutines.flow.combine(
        _allPages,
        settingsRepository.pageSortOrderFlow
    ) { pages, sortOrderStr ->
        val sortOrder = try { SortOrder.valueOf(sortOrderStr) } catch (e: Exception) { SortOrder.MANUAL }
        when (sortOrder) {
            SortOrder.MANUAL -> pages.sortedBy { it.orderIndex }
            SortOrder.NEWEST -> pages.sortedByDescending { it.createdAt }
            SortOrder.OLDEST -> pages.sortedBy { it.createdAt }
            SortOrder.A_Z -> pages.sortedBy { it.name.lowercase() }
            SortOrder.Z_A -> pages.sortedByDescending { it.name.lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _currentPage = MutableStateFlow<Page?>(null)
    val currentPage: StateFlow<Page?> = _currentPage.asStateFlow()

    private val _lastActions = MutableStateFlow<List<String>>(emptyList())
    val lastActions: StateFlow<List<String>> = _lastActions.asStateFlow()

    val templates: StateFlow<List<PageTemplate>> = templateRepository.getAllTemplates()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    private val _authRecoverIntent = MutableSharedFlow<Intent>()
    val authRecoverIntent: SharedFlow<Intent> = _authRecoverIntent.asSharedFlow()

    val scannerEngine = ScannerEngine(viewModelScope, settingsRepository, ttsHelper, logger)
    val focusedButtonIndex: StateFlow<Int?> = scannerEngine.focusedButtonIndex
    val focusedRowIndex: StateFlow<Int?> = scannerEngine.focusedRowIndex
    val defaultScanPattern: StateFlow<String> = settingsRepository.defaultScanPatternFlow
    val showTestButtons: StateFlow<Boolean> = settingsRepository.showTestButtonsFlow

    val actionExecutor = ActionExecutor(
        scope = viewModelScope,
        settingsRepository = settingsRepository,
        ttsHelper = ttsHelper,
        geminiUseCase = null, // Will be set in init
        buttonUsageRepository = buttonUsageRepository
    )

    fun setActiveBookId(bookId: String?) {
        _activeBookId.value = bookId
    }

    init {
        geminiUseCase = geminiUseCaseFactory.create {
            driveAuthManager.getDriveCredential()?.getToken()
        }
        actionExecutor.geminiUseCase = geminiUseCase
        
        scannerEngine.ttsHelper = ttsHelper
        actionExecutor.ttsHelper = ttsHelper

        // Observe ActionExecutor status
        viewModelScope.launch {
            actionExecutor.isExecuting.collect { isExecuting ->
                if (isExecuting) {
                    stopScanningTemporarily()
                } else {
                    resumeScanningIfEnabled()
                }
            }
        }

        // Observe ActionExecutor events
        viewModelScope.launch {
            actionExecutor.events.collect { event ->
                when (event) {
                    is ActionExecutor.ExecutionEvent.NavigateToPage -> {
                        viewModelScope.launch {
                            val page = pageRepository.getPageById(event.pageId)
                            if (page != null) {
                                loadPage(page)
                                logAction("Navigiert zu Seite: ${page.name} (ID: ${event.pageId})")
                            } else {
                                logAction("Fehler: Seite mit ID '${event.pageId}' nicht gefunden.")
                                ttsHelper.speak(application.getString(com.andreas_kratzer.ghosttalk.R.string.error_page_not_found)) {}
                            }
                        }
                    }
                    is ActionExecutor.ExecutionEvent.Log -> {
                        logAction(event.message)
                    }
                    is ActionExecutor.ExecutionEvent.Error -> {
                        logAction("Fehler: ${event.message}")
                    }
                    is ActionExecutor.ExecutionEvent.RecoverableAuthError -> {
                        _authRecoverIntent.emit(event.intent)
                    }
                }
            }
        }

        // Monitor settings
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                settingsRepository.ttsLanguageFlow,
                settingsRepository.ttsVoiceNameFlow
            ) { lang, voice -> lang to voice }
                .collect { (newLanguage, newVoice) ->
                    ttsHelper.setLanguageAndVoice(newLanguage, newVoice)
                }
        }

        viewModelScope.launch {
            settingsRepository.scanDelayFlow.collect { delay -> setScanDelay(delay) }
        }
        
        // Reactive page loading via UseCase
        viewModelScope.launch {
            getPagesUseCase.execute(_activeBookId).collect { pages ->
                _allPages.value = pages
            }
        }

        // Action logs via UseCase
        _lastActions.value = actionLogUseCase.loadSavedLogs()

        // Set up Gemini command handlers
        geminiUseCase?.setAppCommandHandler { command, args ->
            when (command) {
                "SPOTIFY_PLAY" -> {
                    val query = args["query"] ?: return@setAppCommandHandler
                    viewModelScope.launch {
                        try {
                            val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                                putExtra("android.intent.extra.focus", "vnd.android.cursor.item/*")
                                putExtra("query", query)
                                putExtra(android.app.SearchManager.QUERY, query)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            application.startActivity(intent)
                        } catch (e: Exception) {
                            Log.e("PageViewModel", "Failed to launch Spotify", e)
                        }
                    }
                }
            }
        }

        ttsHelper.fallbackListener = object : TextToSpeechHelper.OnVoiceFallbackListener {
            override fun onVoiceFallback(originalVoice: String, fallbackVoice: String?, reason: String) {
                viewModelScope.launch {
                    val message = if (fallbackVoice != null) {
                        "Stimme $originalVoice nicht verfügbar (Offline). Fallback auf $fallbackVoice."
                    } else {
                        "Stimme $originalVoice nicht verfügbar (Offline). Fallback auf System-Standard."
                    }
                    android.widget.Toast.makeText(getApplication<Application>(), message, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun loadPage(page: Page) {
        viewModelScope.launch {
            val bookId = _activeBookId.value ?: page.bookId
            val resolvedPage = frequentActionResolver.resolve(page, bookId)
            _currentPage.value = resolvedPage
            scannerEngine.stopScanning()
        }
    }

    fun resumeScanningIfEnabled() {
        if (settingsRepository.autoStartScanning) {
            val startIndex = if (settingsRepository.resumeScanningFromStart) {
                0
            } else {
                focusedButtonIndex.value ?: 0
            }
            startScanning(startIndex)
        }
    }

    fun setScanDelay(delayMillis: Long) {
        scannerEngine.scanDelayMillis = delayMillis
        if (settingsRepository.autoStartScanning) {
            startScanning()
        }
    }

    fun startScanning(startIndex: Int = 0) {
        val page = _currentPage.value ?: return
        val defaultPattern = settingsRepository.defaultScanPattern
        val patternToUse = page.scanPattern ?: defaultPattern
        scannerEngine.startScanning(
            buttonConfigs = page.buttonConfigs,
            startIndex = startIndex,
            pattern = patternToUse,
            columns = page.columns,
            rowNames = page.rowNames
        )
    }

    fun stopScanningTemporarily() {
        scannerEngine.pauseScanning()
    }

    fun stopScanning() {
        scannerEngine.stopScanning()
    }

    fun activateButtonAtIndex(index: Int) {
        val page = _currentPage.value ?: return
        val buttonConfig = page.buttonConfigs.getOrNull(index) ?: return
        
        scannerEngine.setFocusedIndex(index)
        actionExecutor.executeButtonAction(buttonConfig, bookId = _activeBookId.value)
    }

    fun activateFocusedButton() {
        val focusedIdx = focusedButtonIndex.value
        val focusedRow = focusedRowIndex.value
        if (focusedIdx != null) {
            activateButtonAtIndex(focusedIdx)
        } else if (focusedRow != null) {
            scannerEngine.selectCurrentRow()
        }
    }

    private fun logAction(actionText: String) {
        _lastActions.update { current ->
            actionLogUseCase.formatAndAddEntry(actionText, current)
        }
    }

    fun clearActionLogs() {
        _lastActions.value = emptyList()
        actionLogUseCase.clearLogs()
    }

    fun createNewPage(name: String, rows: Int, columns: Int, bookId: String, templateId: String? = null, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val generatedId = createPageUseCase.execute(name, rows, columns, bookId, _allPages.value, templateId)
            onCreated(generatedId)
        }
    }

    fun updateButtonConfig(pageId: String, index: Int, newConfig: ButtonConfig?) {
        viewModelScope.launch {
            val page = pageRepository.getPageById(pageId)
            if (page != null && index in page.buttonConfigs.indices) {
                val updatedConfigs = page.buttonConfigs.toMutableList()
                updatedConfigs[index] = newConfig
                val updatedPage = page.copy(buttonConfigs = updatedConfigs)
                pageRepository.updatePage(updatedPage)
                
                if (_currentPage.value?.id == pageId) {
                    _currentPage.value = updatedPage
                }
            }
        }
    }

    fun updatePageSettings(pageId: String, newName: String, newScanPattern: String?, newRowNames: List<String>) {
        viewModelScope.launch {
            val page = pageRepository.getPageById(pageId)
            if (page != null) {
                val updatedPage = page.copy(
                    name = newName,
                    scanPattern = newScanPattern,
                    rowNames = newRowNames
                )
                pageRepository.updatePage(updatedPage)
                
                if (_currentPage.value?.id == pageId) {
                    _currentPage.value = updatedPage
                }
            }
        }
    }

    fun updateRowName(pageId: String, rowIndex: Int, newName: String) {
        viewModelScope.launch {
            val page = pageRepository.getPageById(pageId)
            if (page != null) {
                val updatedNames = page.rowNames.toMutableList()
                while (updatedNames.size <= rowIndex) {
                    updatedNames.add("Zeile ${updatedNames.size + 1}")
                }
                updatedNames[rowIndex] = newName
                
                val updatedPage = page.copy(rowNames = updatedNames)
                pageRepository.updatePage(updatedPage)
                
                if (_currentPage.value?.id == pageId) {
                    _currentPage.value = updatedPage
                }
            }
        }
    }

    fun deletePage(page: Page) {
        viewModelScope.launch {
            pageRepository.deletePage(page)
        }
    }

    fun reorderPages(fromIndex: Int, toIndex: Int) {
        val currentList = allPages.value.toMutableList()
        if (fromIndex !in currentList.indices || toIndex !in currentList.indices) return
        
        val item = currentList.removeAt(fromIndex)
        currentList.add(toIndex, item)
        
        viewModelScope.launch {
            // Update indices in DB
            currentList.forEachIndexed { index, page ->
                if (page.orderIndex != index) {
                    pageRepository.updatePage(page.copy(orderIndex = index))
                }
            }
            // Ensure we are in MANUAL mode if user reorders
            settingsRepository.pageSortOrder = SortOrder.MANUAL.name
        }
    }

    fun importFromJson(jsonString: String, bookId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val result = importExportManager.importFromJson(jsonString, bookId)
            result.onSuccess { onSuccess() }.onFailure { e -> onError("Fehler beim Import: ${e.message}") }
        }
    }

    suspend fun exportToJson(): String {
        return importExportManager.exportToJson(_allPages.value)
    }

    override fun onCleared() {
        super.onCleared()
        ttsHelper.shutdown()
        scannerEngine.clear()
    }
}

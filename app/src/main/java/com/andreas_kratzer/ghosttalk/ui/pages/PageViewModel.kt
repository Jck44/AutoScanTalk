package com.andreas_kratzer.ghosttalk.ui.pages

import android.app.Application
import android.content.Intent
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andreas_kratzer.ghosttalk.core.actions.ActionExecutor
import com.andreas_kratzer.ghosttalk.core.actions.FrequentActionResolver
import com.andreas_kratzer.ghosttalk.core.pages.PageImportExportManager
import com.andreas_kratzer.ghosttalk.core.scanning.ScannerEngine
import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.andreas_kratzer.ghosttalk.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.data.TemplateRepository
import com.andreas_kratzer.ghosttalk.domain.ActionLogUseCase
import com.andreas_kratzer.ghosttalk.domain.CreatePageUseCase
import com.andreas_kratzer.ghosttalk.domain.GeminiUseCase
import com.andreas_kratzer.ghosttalk.domain.GeminiUseCaseFactory
import com.andreas_kratzer.ghosttalk.domain.FeatureGuard
import com.andreas_kratzer.ghosttalk.domain.GetPagesUseCase
import com.andreas_kratzer.ghosttalk.domain.PredictNextActionUseCase
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.PageTemplate
import com.andreas_kratzer.ghosttalk.model.SortOrder
import com.andreas_kratzer.ghosttalk.model.SmartPredictionButtonAction
import com.andreas_kratzer.ghosttalk.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.model.SpeakTextButtonAction
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
    val settingsRepository: SettingsRepository,
    private val importExportManager: PageImportExportManager,
    private val getPagesUseCase: GetPagesUseCase,
    private val actionLogUseCase: ActionLogUseCase,
    private val createPageUseCase: CreatePageUseCase,
    private val frequentActionResolver: FrequentActionResolver,
    private val buttonUsageRepository: ButtonUsageRepository,
    private val scannerEngine: ScannerEngine,
    templateRepository: TemplateRepository,
    private val googleAuthManager: GoogleAuthManager,
    geminiUseCaseFactory: GeminiUseCaseFactory,
    private val ttsHelper: TextToSpeechHelper,
    private val predictNextActionUseCase: PredictNextActionUseCase,
    private val bookRepository: com.andreas_kratzer.ghosttalk.data.BookRepository,
    private val deletePageUseCase: com.andreas_kratzer.ghosttalk.domain.DeletePageUseCase,
    private val reorderPagesUseCase: com.andreas_kratzer.ghosttalk.domain.ReorderPagesUseCase,
    private val updateButtonConfigUseCase: com.andreas_kratzer.ghosttalk.domain.UpdateButtonConfigUseCase,
    private val updatePageSettingsUseCase: com.andreas_kratzer.ghosttalk.domain.UpdatePageSettingsUseCase,
    private val updateRowNameUseCase: com.andreas_kratzer.ghosttalk.domain.UpdateRowNameUseCase,
    private val importPageUseCase: com.andreas_kratzer.ghosttalk.domain.ImportPageUseCase,
    private val exportPageUseCase: com.andreas_kratzer.ghosttalk.domain.ExportPageUseCase,
    private val logger: Logger,
    val featureGuard: FeatureGuard
) : AndroidViewModel(application) {

    private var geminiUseCase: GeminiUseCase? = null

    private val _activeBookId = MutableStateFlow<String?>(null)
    val activeBookId: StateFlow<String?> = _activeBookId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private val _allPages = MutableStateFlow<List<Page>>(emptyList())
    val allPages: StateFlow<List<Page>> = kotlinx.coroutines.flow.combine(
        _allPages,
        settingsRepository.pageSortOrderFlow,
        _searchQuery
    ) { pages, sortOrderStr, query ->
        val sortOrder = try { SortOrder.valueOf(sortOrderStr) } catch (_: Exception) { SortOrder.MANUAL }
        val trimmedQuery = query.trim()
        val filtered = if (trimmedQuery.isBlank()) {
            pages
        } else {
            pages.filter { it.name.contains(trimmedQuery, ignoreCase = true) }
        }
        when (sortOrder) {
            SortOrder.MANUAL -> filtered.sortedBy { it.orderIndex }
            SortOrder.NEWEST -> filtered.sortedByDescending { it.createdAt }
            SortOrder.OLDEST -> filtered.sortedBy { it.createdAt }
            SortOrder.A_Z -> filtered.sortedBy { it.name.lowercase() }
            SortOrder.Z_A -> filtered.sortedByDescending { it.name.lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val unfilteredPages: StateFlow<List<Page>> = kotlinx.coroutines.flow.combine(
        _allPages,
        settingsRepository.pageSortOrderFlow
    ) { pages, sortOrderStr ->
        val sortOrder = try { SortOrder.valueOf(sortOrderStr) } catch (_: Exception) { SortOrder.MANUAL }
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

    private val _smartPredictions = MutableStateFlow<List<String>>(emptyList())
    val smartPredictions: StateFlow<List<String>> = _smartPredictions.asStateFlow()

    val templates: StateFlow<List<PageTemplate>> = templateRepository.getAllTemplates()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    private val _authRecoverIntent = MutableSharedFlow<Intent>()
    val authRecoverIntent: SharedFlow<Intent> = _authRecoverIntent.asSharedFlow()

    val focusedButtonIndex: StateFlow<Int?> = scannerEngine.focusedButtonIndex
    val focusedRowIndex: StateFlow<Int?> = scannerEngine.focusedRowIndex
    val defaultScanPattern: StateFlow<String> = settingsRepository.defaultScanPatternFlow
    val showTestButtons: StateFlow<Boolean> = settingsRepository.showTestButtonsFlow
    val experimentalManualSorting: StateFlow<Boolean> = settingsRepository.experimentalManualSortingFlow

    val actionExecutor = ActionExecutor(
        scope = viewModelScope,
        settingsRepository = settingsRepository,
        logger = logger,
        ttsHelper = ttsHelper,
        geminiUseCase = null, // Will be set in init
        buttonUsageRepository = buttonUsageRepository
    )

    private val _isUserModeActive = MutableStateFlow(false)
    val isUserModeActive: StateFlow<Boolean> = _isUserModeActive.asStateFlow()

    fun setUserModeActive(isActive: Boolean) {
        _isUserModeActive.value = isActive
        if (!isActive) {
            stopScanningTemporarily()
            ttsHelper.stopNotificationTTS()
        }
    }

    fun setActiveBookId(bookId: String?) {
        _activeBookId.value = bookId
    }

    init {
        geminiUseCase = geminiUseCaseFactory.create {
            googleAuthManager.getGoogleCredential()?.getToken()
        }
        actionExecutor.geminiUseCase = geminiUseCase
        
        actionExecutor.ttsHelper = ttsHelper

        // Consolidated scanner trigger: observe both ActionExecutor and current Page
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                actionExecutor.isExecuting,
                _currentPage,
                _isUserModeActive
            ) { isExecuting, page, isActive -> Triple(isExecuting, page, isActive) }
                .collect { (isExecuting, page, isActive) ->
                    if (!isActive) {
                        Log.d("PageViewModel", "Scanner Trigger: User mode inactive, ignoring state change.")
                        return@collect
                    }
                    if (isExecuting) {
                        Log.d("PageViewModel", "Scanner Trigger: ActionExecutor is executing. Pausing scan.")
                        stopScanningTemporarily()
                    } else if (page != null) {
                        Log.d("PageViewModel", "Scanner Trigger: Ready to resume on page ${page.id}")
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
                                val idSuffix = if (settingsRepository.showPageIdInLog) " (ID: ${event.pageId})" else ""
                                logAction("Navigiert zu Seite: ${page.name}$idSuffix")
                                loadPage(page)
                            } else {
                                val idSuffix = if (settingsRepository.showPageIdInLog) " mit ID '${event.pageId}'" else ""
                                logAction("Fehler: Seite$idSuffix nicht gefunden.")
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

        // Gemini Prediction Triggers
        viewModelScope.launch {
            // Trigger prediction when page changes OR when action log changes
            kotlinx.coroutines.flow.combine(
                _currentPage,
                _lastActions,
                settingsRepository.smartPredictionDelayMillisFlow,
                settingsRepository.isSmartPredictionEnabledFlow
            ) { page, _, delay, enabled -> Triple(page, delay, enabled) }
                .collect { (page, delay, enabled) ->
                    if (page != null && enabled) {
                        // Check if at least one SMART_PREDICTION button exists
                        val hasPredictor = page.buttonConfigs.any { 
                            it != null && featureGuard.isActionEnabled(it.buttonAction) && it.buttonAction is SmartPredictionButtonAction
                        }
                        
                        if (hasPredictor) {
                            val bookId = _activeBookId.value
                            if (bookId != null) {
                                // Clear current while waiting? User didn't specify, but let's keep old for less Flicker
                                kotlinx.coroutines.delay(delay) // Debounce using the configurable delay
                                try {
                                    _smartPredictions.value = predictNextActionUseCase.predict(page, bookId)
                                } catch (e: Exception) {
                                    Log.e("PageViewModel", "Smart Prediction failed", e)
                                }
                            }
                        } else {
                            _smartPredictions.value = emptyList()
                        }
                    }
                }
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
            val isSamePage = _currentPage.value?.id == page.id
            
            if (isSamePage) {
                // Keep focus index if it's just a dynamic update (Stats)
                scannerEngine.pauseScanning()
            } else {
                scannerEngine.stopScanning()
            }
            
            val bookId = _activeBookId.value ?: page.bookId
            val resolvedPage = frequentActionResolver.resolve(page, bookId)
            _currentPage.value = resolvedPage
        }
    }

    fun resumeScanningIfEnabled() {
        if (actionExecutor.isExecuting.value) {
            Log.d("PageViewModel", "resumeScanningIfEnabled: ActionExecutor is busy, skipping scan resume.")
            return
        }
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
        page.scanPattern ?: defaultPattern
        scannerEngine.startScanning(
            buttonConfigs = page.buttonConfigs,
            startIndex = startIndex,
            pattern = page.scanPattern ?: settingsRepository.defaultScanPattern,
            columns = page.columns,
            rowNames = page.rowNames,
            pageId = page.id
        )
    }

    fun stopScanningTemporarily() {
        scannerEngine.pauseScanning()
    }

    fun stopScanning() {
        scannerEngine.stopScanning()
    }

    fun activateButtonAtIndex(index: Int) {
        // Prevent interaction during execution (non-interruptible audio policy)
        if (actionExecutor.isExecuting.value) {
            Log.d("PageViewModel", "Ignoring button click at index $index as ActionExecutor is currently executing.")
            return
        }

        // Jeder gültige Tasterdruck unterbricht ein eventuell laufendes Vorlesen von Benachrichtigungen
        ttsHelper.stopNotificationTTS()
        
        val page = _currentPage.value ?: return
        val buttonConfig = page.buttonConfigs.getOrNull(index) ?: return
        
        scannerEngine.setFocusedIndex(index)
        
        val smartAction = buttonConfig.buttonAction as? SmartPredictionButtonAction
        if (smartAction != null) {
            val prediction = _smartPredictions.value.getOrNull(smartAction.rank - 1)
            if (prediction != null) {
                resolveSmartPrediction(prediction)
                return
            }
        }
        
        actionExecutor.executeButtonAction(buttonConfig, bookId = _activeBookId.value)
    }

    private fun resolveSmartPrediction(prediction: String) {
        viewModelScope.launch {
            // Check if it's a page name (Navigation)
            val allPages = _allPages.value
            val targetPage = allPages.find { it.name.equals(prediction, ignoreCase = true) }
            
            if (targetPage != null) {
                actionExecutor.executeButtonAction(
                    ButtonConfig(
                        label = targetPage.name,
                        auditoryCue = null,
                        buttonAction = NavigateToPageButtonAction(targetPage.id)
                    ),
                    bookId = _activeBookId.value
                )
            } else {
                // Otherwise treat as SpeakText
                actionExecutor.executeButtonAction(
                    ButtonConfig(
                        label = prediction,
                        auditoryCue = null,
                        buttonAction = SpeakTextButtonAction(prediction)
                    ),
                    bookId = _activeBookId.value
                )
            }
        }
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
            bookRepository.updateLastModified(bookId)
            onCreated(generatedId)
        }
    }

    fun updateButtonConfig(pageId: String, index: Int, newConfig: ButtonConfig?) {
        viewModelScope.launch {
            val updatedPage = updateButtonConfigUseCase.execute(pageId, index, newConfig)
            if (updatedPage != null && _currentPage.value?.id == pageId) {
                _currentPage.value = updatedPage
            }
        }
    }

    fun updatePageSettings(pageId: String, newName: String, newScanPattern: String?, newRowNames: List<String>) {
        viewModelScope.launch {
            val updatedPage = updatePageSettingsUseCase.execute(pageId, newName, newScanPattern, newRowNames)
            if (updatedPage != null && _currentPage.value?.id == pageId) {
                _currentPage.value = updatedPage
            }
        }
    }

    fun updateRowName(pageId: String, rowIndex: Int, newName: String) {
        viewModelScope.launch {
            val updatedPage = updateRowNameUseCase.execute(pageId, rowIndex, newName)
            if (updatedPage != null && _currentPage.value?.id == pageId) {
                _currentPage.value = updatedPage
            }
        }
    }

    fun deletePage(page: Page) {
        viewModelScope.launch {
            deletePageUseCase.execute(page)
        }
    }

    fun reorderPages(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            reorderPagesUseCase.execute(_allPages.value, fromIndex, toIndex, _activeBookId.value)
        }
    }

    fun importFromJson(jsonString: String, bookId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val result = importPageUseCase.execute(jsonString, bookId)
            result.onSuccess { onSuccess() }.onFailure { e -> onError("Fehler beim Import: ${e.message}") }
        }
    }

    suspend fun exportToJson(): String {
        return exportPageUseCase.execute(_allPages.value)
    }

    override fun onCleared() {
        super.onCleared()
        ttsHelper.shutdown()
        scannerEngine.clear()
    }
}

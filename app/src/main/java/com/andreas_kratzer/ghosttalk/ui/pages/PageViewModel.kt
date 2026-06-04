package com.andreas_kratzer.ghosttalk.ui.pages

import android.app.Application
import android.content.Intent
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.andreas_kratzer.ghosttalk.core.actions.ActionExecutor
import com.andreas_kratzer.ghosttalk.core.ai.domain.GeminiUseCase
import com.andreas_kratzer.ghosttalk.core.ai.domain.UpdateSmartPredictionsUseCase
import com.andreas_kratzer.ghosttalk.core.cloud.PhilipsHueManager
import com.andreas_kratzer.ghosttalk.core.cloud.SpotifyManager
import com.andreas_kratzer.ghosttalk.core.cloud.SpotifyPlaylist
import com.andreas_kratzer.ghosttalk.core.data.ButtonTemplateRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.domain.pages.UsageLocation
import com.andreas_kratzer.ghosttalk.core.model.Book
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ButtonTemplate
import com.andreas_kratzer.ghosttalk.core.model.GridSettingsUpdate
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.scanning.ScanCoordinator
import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
import com.andreas_kratzer.ghosttalk.domain.actions.ResolveDynamicButtonsUseCase
import com.andreas_kratzer.ghosttalk.feature.settings.domain.FeatureGuard
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.InteractionDelegate
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.PageManagementDelegate
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.ScreenManagementDelegate
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.SmartPredictionDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.andreas_kratzer.ghosttalk.core.ai.domain.SplitPageUseCase
import com.andreas_kratzer.ghosttalk.core.domain.pages.CreatePageUseCase
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.core.model.OptionalProperty
import kotlinx.coroutines.withContext
import com.andreas_kratzer.ghosttalk.core.model.BookRestructureProposal
import android.widget.Toast
import androidx.core.net.toUri

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PageViewModel @Inject constructor(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
    val settingsRepository: SettingsRepository,
    private val bookRepository: com.andreas_kratzer.ghosttalk.core.data.BookRepository,
    private val ttsHelper: TextToSpeechHelper,
    val featureGuard: FeatureGuard,
    val pageManagementDelegate: PageManagementDelegate,
    val interactionDelegate: InteractionDelegate,
    val screenManagementDelegate: ScreenManagementDelegate,
    smartPredictionDelegate: SmartPredictionDelegate,
    private val resolveDynamicButtonsUseCase: ResolveDynamicButtonsUseCase,
    updateSmartPredictionsUseCase: UpdateSmartPredictionsUseCase,
    val actionExecutor: ActionExecutor,
    private val scanCoordinator: ScanCoordinator,
    private val geminiUseCase: GeminiUseCase,
    private val buttonTemplateRepository: ButtonTemplateRepository,
    val systemCallManager: com.andreas_kratzer.ghosttalk.core.call.SystemCallManager,
    val philipsHueManager: PhilipsHueManager,
    private val spotifyManager: SpotifyManager,
    private val buttonUsageRepository: com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository,
    private val efficiencyAnalyzer: com.andreas_kratzer.ghosttalk.core.data.impl.analytics.EfficiencyAnalyzer,
    private val pathAnalyzer: com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PathAnalyzer,
    private val userModeSessionRepository: com.andreas_kratzer.ghosttalk.core.data.UserModeSessionRepository,
    private val splitPageUseCase: SplitPageUseCase,
    private val createPageUseCase: CreatePageUseCase,
    private val pageLayoutOptimizer: com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PageLayoutOptimizer,
    private val bookRestructureProposalUseCase: com.andreas_kratzer.ghosttalk.core.ai.domain.BookRestructureProposalUseCase,
    private val cloneBookUseCase: com.andreas_kratzer.ghosttalk.core.data.impl.CloneBookUseCase
) : AndroidViewModel(application), com.andreas_kratzer.ghosttalk.ui.util.GridEditorActions {

    private val pageBackStack = mutableListOf<String>()

    val activeBookId = pageManagementDelegate.activeBookId

    val userModeSessions: StateFlow<List<com.andreas_kratzer.ghosttalk.core.model.UserModeSession>> = activeBookId
        .flatMapLatest { bookId ->
            if (bookId == null) flowOf(emptyList())
            else userModeSessionRepository.getSessionsForBook(bookId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearUserModeSessions() {
        viewModelScope.launch {
            val bookId = activeBookId.value
            if (bookId != null) {
                userModeSessionRepository.clearSessions(bookId)
            }
        }
    }
    val currentPageId = pageManagementDelegate.currentPageId
    val searchQuery = pageManagementDelegate.searchQuery
    val filteredPages = pageManagementDelegate.filteredPages
    val unfilteredPages = pageManagementDelegate.unfilteredPages
    val allPages = pageManagementDelegate.allPagesFlow
    val currentPage = pageManagementDelegate.currentPage
    val templates = pageManagementDelegate.templates
    val activeTargetPageIds = pageManagementDelegate.activeTargetPageIds

    val buttonTemplates: StateFlow<List<ButtonTemplate>> = buttonTemplateRepository.getTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val buttonHistory = buttonUsageRepository.buttonHistory

    private val _isCalculatingRecommendations = MutableStateFlow(false)
    val isCalculatingRecommendations: StateFlow<Boolean> = _isCalculatingRecommendations.asStateFlow()

    val shortcutRecommendations: StateFlow<List<com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PathAnalyzer.ShortcutRecommendation>> = combine(
        buttonHistory,
        unfilteredPages,
        activeBookId
    ) { history, allPages, bookId ->
        _isCalculatingRecommendations.value = true
        try {
            if (bookId != null && history.isNotEmpty() && allPages.isNotEmpty()) {
                val delay = settingsRepository.scanDelayMillis
                val startPageId = settingsRepository.defaultStartPageId
                pathAnalyzer.analyzePaths(history, allPages, delay, startPageId)
            } else {
                emptyList()
            }
        } finally {
            _isCalculatingRecommendations.value = false
        }
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun applyShortcutRecommendation(
        recommendation: com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PathAnalyzer.ShortcutRecommendation,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val page = pageManagementDelegate.getPageById(recommendation.sourcePageId)
                if (page == null) {
                    onResult(false, "Quellseite nicht gefunden.")
                    return@launch
                }
                
                val emptyIndex = page.buttonConfigs.indexOfFirst { it == null }
                if (emptyIndex == -1 || emptyIndex >= com.andreas_kratzer.ghosttalk.core.util.GridUtils.TOTAL_SLOTS) {
                    onResult(false, "Die Quellseite ist bereits voll (alle Kachel-Slots belegt).")
                    return@launch
                }
                
                val newConfig = recommendation.targetButtonConfig.copy(
                    id = java.util.UUID.randomUUID().toString()
                )
                
                pageManagementDelegate.insertButtonConfig(
                    pageId = recommendation.sourcePageId,
                    index = emptyIndex,
                    newConfig = newConfig,
                    forceShift = false
                ) { success ->
                    if (success) {
                        onResult(true, "Abkürzung erfolgreich auf Seite '${recommendation.sourcePageName}' erstellt.")
                    } else {
                        onResult(false, "Fehler beim Erstellen der Kachel.")
                    }
                }
            } catch (e: Exception) {
                Log.e("PageViewModel", "Error applying shortcut", e)
                onResult(false, "Fehler: ${e.localizedMessage}")
            }
        }
    }

    fun saveButtonAsTemplate(name: String, config: ButtonConfig) {
        viewModelScope.launch {
            buttonTemplateRepository.saveTemplate(
                ButtonTemplate(
                    id = java.util.UUID.randomUUID().toString(),
                    name = name,
                    buttonConfig = config.copy(id = java.util.UUID.randomUUID().toString()),
                    isBuiltIn = false
                )
            )
        }
    }

    fun deleteButtonTemplate(template: ButtonTemplate) {
        viewModelScope.launch {
            buttonTemplateRepository.deleteTemplate(template)
        }
    }

    fun updateButtonTemplate(template: ButtonTemplate) {
        viewModelScope.launch {
            buttonTemplateRepository.saveTemplate(template)
        }
    }

    fun updateButtonTemplatesOrder(templates: List<ButtonTemplate>) {
        viewModelScope.launch {
            buttonTemplateRepository.updateTemplateOrder(templates)
        }
    }



    val lastActions = interactionDelegate.lastActions
    val authRecoverIntent = interactionDelegate.authRecoverIntent
    val permissionRequestFlow = interactionDelegate.permissionRequestFlow
    val isUserModeActive = interactionDelegate.isUserModeActive
    val screenState get() = screenManagementDelegate.screenState

    private val _smartPredictions = MutableStateFlow<List<String>?>(null)
    val smartPredictions: StateFlow<List<String>?> = _smartPredictions.asStateFlow()
    
    val isSmartPredictionLoading: StateFlow<Boolean> = updateSmartPredictionsUseCase.isLoading

    val defaultScanPattern = settingsRepository.defaultScanPatternFlow
    val showTestButtons = settingsRepository.showTestButtonsFlow

    val spotifyUserDisplayName = settingsRepository.spotifyUserDisplayNameFlow
    
    private val _spotifyPlaylists = MutableStateFlow<List<SpotifyPlaylist>>(emptyList())
    val spotifyPlaylists: StateFlow<List<SpotifyPlaylist>> = _spotifyPlaylists.asStateFlow()
    
    private val _isLoadingPlaylists = MutableStateFlow(false)
    val isLoadingPlaylists: StateFlow<Boolean> = _isLoadingPlaylists.asStateFlow()

    val staticRowPage: StateFlow<Page?> = combine(
        activeBookId,
        allPages,
        settingsRepository.staticRowEnabledFlow
    ) { bookId, allPages, enabled ->
        if (bookId != null && enabled) {
            val expectedId = "static_row_$bookId"
            val existing = allPages.find { it.id == expectedId }
            if (existing == null) {
                viewModelScope.launch(Dispatchers.IO) {
                    if (pageManagementDelegate.getPageById(expectedId) == null) {
                        val newPage = Page(
                            id = expectedId,
                            bookId = bookId,
                            name = "Statische Zeile",
                            templateId = null,
                            rows = 1,
                            columns = 4,
                            scanPattern = "linear",
                            rowNames = emptyList(),
                            buttonConfigs = emptyList(),
                            orderIndex = -1
                        )
                        pageManagementDelegate.pageRepository.insertPage(newPage)
                    }
                }
                null
            } else {
                resolveDynamicButtonsUseCase.execute(existing, bookId, _smartPredictions.value, allPages)
            }
        } else {
            null
        }
    }
    .flowOn(Dispatchers.Default)
    .distinctUntilChanged()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isEditPreviewActive = MutableStateFlow(false)
    val isEditPreviewActive = _isEditPreviewActive.asStateFlow()

    fun toggleEditPreviewActive() {
        _isEditPreviewActive.value = !_isEditPreviewActive.value
        Log.d("PageViewModel", "toggleEditPreviewActive: active = ${_isEditPreviewActive.value}")
    }

    private val isPreviewOrUserMode = combine(isUserModeActive, isEditPreviewActive) { userMode, editPreview ->
        userMode || editPreview
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), isUserModeActive.value || isEditPreviewActive.value)

    val resolvedPage: StateFlow<Page?> = combine(
        currentPage,
        isPreviewOrUserMode,
        smartPredictions,
        activeBookId,
        unfilteredPages
    ) { page, isPreviewMode, predictions, bookId, allPages ->
        if (page != null && isPreviewMode && bookId != null) {
            resolveDynamicButtonsUseCase.execute(page, bookId, predictions, allPages)
        } else {
            page
        }
    }
    .flowOn(Dispatchers.Default)
    .distinctUntilChanged()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), currentPage.value)

    // --- Caregiver Visual Analytics Overlay States ---
    private val _isAnalyticsOverlayEnabled = MutableStateFlow(false)
    val isAnalyticsOverlayEnabled = _isAnalyticsOverlayEnabled.asStateFlow()

    fun toggleAnalyticsOverlay() {
        _isAnalyticsOverlayEnabled.value = !_isAnalyticsOverlayEnabled.value
        Log.d("PageViewModel", "toggleAnalyticsOverlay: enabled = ${_isAnalyticsOverlayEnabled.value}")
    }

    val pageMetrics: StateFlow<Map<String, com.andreas_kratzer.ghosttalk.core.model.ButtonEffortMetrics>> = combine(
        resolvedPage,
        activeBookId,
        buttonHistory
    ) { page, bookId, history ->
        Log.d("PageViewModel", "pageMetrics combine: page = ${page?.name} (${page?.id}), bookId = $bookId, history = ${history.size}")
        Triple(page, bookId, history)
    }
    .flatMapLatest { (page, bookId, history) ->
        if (page == null || bookId == null) {
            Log.d("PageViewModel", "pageMetrics flatMapLatest: skipping analysis (page=${page?.id}, bookId=$bookId)")
            flowOf(emptyMap())
        } else {
            kotlinx.coroutines.flow.flow {
                try {
                    Log.d("PageViewModel", "pageMetrics: starting analysis for page ${page.name} in book $bookId")
                    val stats = buttonUsageRepository.getGroupedUsageStats(bookId)
                    val clickCounts = stats.flatMap { it.children }
                        .associate { it.buttonConfigId to it.usageCount }
                    Log.d("PageViewModel", "pageMetrics: fetched ${stats.size} stats, clickCounts = $clickCounts")
                    val delay = settingsRepository.scanDelayMillis
                    val pattern = settingsRepository.defaultScanPattern
                    val allPages = pageManagementDelegate.unfilteredPages.value
                    val startPageId = settingsRepository.defaultStartPageId
                    val metrics = efficiencyAnalyzer.calculatePageMetrics(
                        page = page,
                        allPages = allPages,
                        startPageId = startPageId,
                        clickCounts = clickCounts,
                        scanDelayMs = delay,
                        defaultScanPattern = pattern,
                        historyEvents = history
                    )
                    Log.d("PageViewModel", "pageMetrics: calculated metrics for ${metrics.size} buttons: $metrics")
                    emit(metrics)
                } catch (e: Exception) {
                    Log.e("PageViewModel", "Error analyzing page efficiency", e)
                    emit(emptyMap())
                }
            }
        }
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    suspend fun getMarkovSuccessors(buttonId: String): List<Pair<String, Int>> {
        val bookId = activeBookId.value ?: return emptyList()
        return buttonUsageRepository.getMarkovSuccessors(bookId, buttonId)
    }

    val focusedButtonIndex = scanCoordinator.focusedButtonIndex
    val focusedRowIndex = scanCoordinator.focusedRowIndex
    val isScanning = scanCoordinator.isScanning

    // --- Telephony Call States ---
    val callState = systemCallManager.callState
    val callerName = systemCallManager.callerName
    val callerPhone = systemCallManager.callerPhone
    val callDurationSeconds = systemCallManager.callDurationSeconds
    val isOutgoing = systemCallManager.isOutgoing
    val isSimulatedCall = systemCallManager.isSimulatedFlow
    val isHangUpButtonFocused = MutableStateFlow(false)
    val hangUpPressCount = MutableStateFlow(0)
    val focusedCallScreenButton = MutableStateFlow("ANNEHMEN") // "ANNEHMEN" or "ABLEHNEN"
    private var callScanJob: kotlinx.coroutines.Job? = null

    private fun speakCallScreenButton(button: String, isInitial: Boolean) {
        val textRes = if (button == "ANNEHMEN") {
            com.andreas_kratzer.ghosttalk.R.string.call_answer
        } else {
            com.andreas_kratzer.ghosttalk.R.string.call_reject
        }
        val text = getApplication<Application>().getString(textRes)
        val cueDevice = settingsRepository.cuesAudioDeviceAddress
        val queueMode = if (isInitial) {
            android.speech.tts.TextToSpeech.QUEUE_ADD
        } else {
            android.speech.tts.TextToSpeech.QUEUE_FLUSH
        }
        ttsHelper.speakRouted(text, cueDevice, queueMode = queueMode, isForCues = true)
    }

    private fun startCallScanning() {
        callScanJob?.cancel()
        focusedCallScreenButton.value = "ANNEHMEN"
        speakCallScreenButton("ANNEHMEN", isInitial = true)
        val scanDelay = settingsRepository.scanDelayMillis
        callScanJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(scanDelay)
                if (focusedCallScreenButton.value == "ANNEHMEN") {
                    focusedCallScreenButton.value = "ABLEHNEN"
                } else {
                    focusedCallScreenButton.value = "ANNEHMEN"
                    systemCallManager.incrementScanCycle()
                }
                speakCallScreenButton(focusedCallScreenButton.value, isInitial = false)
            }
        }
    }

    private fun stopCallScanning() {
        callScanJob?.cancel()
        callScanJob = null
    }

    fun loadStartPage() {
        pageBackStack.clear()
        val allPages = pageManagementDelegate.allPagesFlow.value
        val startId = settingsRepository.defaultStartPageId ?: allPages.firstOrNull()?.id
        val startPage = allPages.find { it.id == startId }
        if (startPage != null) {
            loadPage(startPage)
        }
    }

    private val _selectedPageIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedPageIds: StateFlow<Set<String>> = _selectedPageIds.asStateFlow()

    private val _aiRestructureProposal = MutableStateFlow<BookRestructureProposal?>(null)
    val aiRestructureProposal: StateFlow<BookRestructureProposal?> = _aiRestructureProposal.asStateFlow()

    private val _aiRestructureScope = MutableStateFlow("detailed")
    val aiRestructureScope: StateFlow<String> = _aiRestructureScope.asStateFlow()

    fun setAiRestructureScope(scope: String) {
        _aiRestructureScope.value = scope
    }

    private val _aiRestructureError = MutableStateFlow<String?>(null)
    val aiRestructureError: StateFlow<String?> = _aiRestructureError.asStateFlow()

    fun clearAiRestructureError() {
        _aiRestructureError.value = null
    }

    val activeBook: StateFlow<Book?> = activeBookId.flatMapLatest { id ->
        if (id != null) bookRepository.getBookByIdFlow(id) else flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        loadSpotifyPlaylists()
        viewModelScope.launch {
            buttonTemplateRepository.ensureBuiltInTemplates()
        }
        pageManagementDelegate.init(viewModelScope)
        interactionDelegate.init(viewModelScope, actionExecutor, ::loadPage, ::navigateBack, _smartPredictions, activeBookId)
        interactionDelegate.scanCoordinator = scanCoordinator
        screenManagementDelegate.init(viewModelScope, isUserModeActive)
        
        smartPredictionDelegate.init(
            scope = viewModelScope,
            currentPage = currentPage,
            allPages = pageManagementDelegate.allPagesFlow,
            lastActions = lastActions,
            activeBookId = activeBookId,
            isUserModeActive = isPreviewOrUserMode,
            onPredictionsUpdated = { _smartPredictions.value = it }
        )

        scanCoordinator.init(
            currentPage = currentPage,
            isUserModeActive = isUserModeActive,
            resolvedPage = resolvedPage,
            isSmartPredictionLoading = isSmartPredictionLoading,
            smartPredictions = smartPredictions,
            staticRowPage = staticRowPage,
            staticRowScanPattern = settingsRepository.staticRowScanPatternFlow
        )

        // Observe book settings for scan limit
        viewModelScope.launch {
            activeBook.collect { book ->
                if (book != null) {
                    scanCoordinator.setScanLimitSettings(book.limitScanCycles, book.scanCycleLimit)
                }
            }
        }

        // Observe Call State for scanning and page reset
        viewModelScope.launch {
            systemCallManager.callState.collect { state ->
                when (state) {
                    com.andreas_kratzer.ghosttalk.core.call.CallState.RINGING -> {
                        scanCoordinator.stopScanning()
                        ttsHelper.stopAll()
                        actionExecutor.stopActions()
                        startCallScanning()
                    }
                    com.andreas_kratzer.ghosttalk.core.call.CallState.DIALING,
                    com.andreas_kratzer.ghosttalk.core.call.CallState.ACTIVE -> {
                        scanCoordinator.stopScanning()
                        ttsHelper.stopAll()
                        actionExecutor.stopActions()
                        stopCallScanning()
                        isHangUpButtonFocused.value = false
                        hangUpPressCount.value = 0
                    }
                    com.andreas_kratzer.ghosttalk.core.call.CallState.NONE -> {
                        stopCallScanning()
                        isHangUpButtonFocused.value = false
                        hangUpPressCount.value = 0
                        if (isUserModeActive.value) {
                            loadStartPage()
                            scanCoordinator.restartScanning()
                        }
                    }
                    else -> {}
                }
            }
        }

        // Observe and persist cycle count
        viewModelScope.launch {
            scanCoordinator.currentCycleCount.collect { count ->
                savedStateHandle["scanCycleCount"] = count
            }
        }

        // RESTORE STATE FROM SavedStateHandle
        savedStateHandle.get<String>("currentPageId")?.let { id ->
            viewModelScope.launch {
                pageManagementDelegate.getPageById(id)?.let { page ->
                    pageManagementDelegate.setCurrentPage(page)
                }
            }
        }
        savedStateHandle.get<Boolean>("isUserModeActive")?.let { active ->
            interactionDelegate.setUserModeActive(active)
        }

        // RESTORE SCAN STATE
        val focusedButtonIndex: Int? = savedStateHandle["focusedButtonIndex"]
        focusedButtonIndex?.let { scanCoordinator.setFocusedIndex(it) }
 
        val focusedRowIndex: Int? = savedStateHandle["focusedRowIndex"]
        focusedRowIndex?.let { scanCoordinator.setFocusedRowIndex(it) }
 
        val scanCycleCount: Int? = savedStateHandle["scanCycleCount"]
        scanCycleCount?.let { scanCoordinator.setCycleCount(it) }

        // Observe book ID changes to load restructure proposal cache
        viewModelScope.launch {
            val flow = activeBookId ?: return@launch
            flow.collect { bookId ->
                if (bookId != null) {
                    _aiRestructureProposal.value = loadProposalFromCache(bookId)
                } else {
                    _aiRestructureProposal.value = null
                }
            }
        }

        // Initialize selectedPageIds to active/reachable pages by default
        viewModelScope.launch {
            val unfilteredFlow = pageManagementDelegate.unfilteredPages ?: return@launch
            val activeTargetFlow = pageManagementDelegate.activeTargetPageIds ?: return@launch
            kotlinx.coroutines.flow.combine(
                unfilteredFlow,
                activeTargetFlow
            ) { pages, activeIds ->
                Pair(pages, activeIds)
            }.collect { (pages, activeIds) ->
                if (pages != null && activeIds != null && pages.isNotEmpty() && _selectedPageIds.value.isEmpty()) {
                    _selectedPageIds.value = pages
                        .filter { activeIds.contains(it.id) }
                        .map { it.id }
                        .toSet()
                }
            }
        }

        // Set up Gemini command handlers
        geminiUseCase.setAppCommandHandler { command, args ->
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

    fun updateSearchQuery(query: String) = pageManagementDelegate.updateSearchQuery(query)
    fun setActiveBookId(bookId: String?) {
        pageBackStack.clear()
        pageManagementDelegate.setActiveBookId(bookId)
    }

    fun loadPage(page: Page) {
        loadPageInternal(page, isBackNavigation = false)
    }

    private fun loadPageInternal(page: Page, isBackNavigation: Boolean = false) {
        viewModelScope.launch {
            val isSamePage = currentPage.value?.id == page.id
            val redoPrediction = settingsRepository.geminiRedoPrediction
            
            // Stoppe laufende Aktionen und Audio der alten Seite
            val skipLog = activeBook.value?.logStopActions == false
            actionExecutor.stopActions(skipLog = skipLog)
            
            scanCoordinator.onPageChanged(isSamePage)
            if (!isSamePage || redoPrediction) {
                // Avoid redundant emission if predictions are already null
                if (_smartPredictions.value != null) {
                    _smartPredictions.value = null // Clear to null to indicate "waiting for results"
                }
            }

            // Track backstack: push current page ID before switching
            if (!isSamePage && !isBackNavigation) {
                currentPage.value?.id?.let { prevId ->
                    if (pageBackStack.lastOrNull() != prevId) {
                        pageBackStack.add(prevId)
                    }
                }
            }

            pageManagementDelegate.setCurrentPage(page)
            savedStateHandle["currentPageId"] = page.id
            savedStateHandle["focusedButtonIndex"] = focusedButtonIndex.value
            savedStateHandle["focusedRowIndex"] = focusedRowIndex.value
        }
    }

    fun hasHistory(): Boolean = pageBackStack.isNotEmpty()

    fun navigateBack() {
        if (pageBackStack.isNotEmpty()) {
            val prevPageId = pageBackStack.removeAt(pageBackStack.lastIndex)
            viewModelScope.launch {
                val prevPage = pageManagementDelegate.getPageById(prevPageId)
                if (prevPage != null) {
                    loadPageInternal(prevPage, isBackNavigation = true)
                }
            }
        } else {
            loadStartPage()
        }
    }

    override val availableGeminiTools = geminiUseCase.getAvailableTools()

    fun setUserModeActive(isActive: Boolean) {
        interactionDelegate.setUserModeActive(isActive)
        savedStateHandle["isUserModeActive"] = isActive
    }
    fun activateButtonAtIndex(index: Int) = interactionDelegate.activateButtonAtIndex(
        index,
        resolvedPage.value,
        activeBookId.value,
        isHardwareTriggered = com.andreas_kratzer.ghosttalk.core.util.InputSourceTracker.isHardwareTriggered,
        staticRowPage = staticRowPage.value
    )
    private var lastCallPressTime = 0L

    fun activateFocusedButton() {
        val state = systemCallManager.callState.value
        if (state == com.andreas_kratzer.ghosttalk.core.call.CallState.RINGING) {
            if (focusedCallScreenButton.value == "ANNEHMEN") {
                systemCallManager.answerCall()
            } else {
                systemCallManager.hangUp()
            }
            return
        }
        
        if (state == com.andreas_kratzer.ghosttalk.core.call.CallState.ACTIVE ||
            state == com.andreas_kratzer.ghosttalk.core.call.CallState.DIALING) {
            val currentTime = System.currentTimeMillis()
            val holdingTime = settingsRepository.holdingTimeMillis
            if (currentTime - lastCallPressTime < holdingTime) {
                // Ignore rapid accidental presses (debounce / Haltezeit)
                return
            }
            lastCallPressTime = currentTime

            val requiredPresses = settingsRepository.hangUpPressesRequired
            val nextPressCount = hangUpPressCount.value + 1
            hangUpPressCount.value = nextPressCount
            
            if (requiredPresses <= 1 || nextPressCount >= requiredPresses) {
                systemCallManager.hangUp()
            } else {
                isHangUpButtonFocused.value = true
                val cueDevice = settingsRepository.cuesAudioDeviceAddress
                val text = getApplication<Application>().getString(com.andreas_kratzer.ghosttalk.R.string.call_hang_up)
                ttsHelper.speakRouted(text, cueDevice, isForCues = true)
            }
            return
        }

        interactionDelegate.activateFocusedButton(resolvedPage.value, activeBookId.value, staticRowPage = staticRowPage.value)
    }
    fun clearActionLogs() = interactionDelegate.clearActionLogs()

    fun resumeScanningIfEnabled() = scanCoordinator.resumeScanningIfEnabled()
    fun startScanning(startIndex: Int = 0) = scanCoordinator.startScanning(startIndex)
    fun stopScanning() = scanCoordinator.stopScanning()
    @Suppress("unused")
    fun restartScanning() = scanCoordinator.restartScanning()

    override fun updateButtonConfig(itemId: String, index: Int, newConfig: ButtonConfig?) {
        pageManagementDelegate.updateButtonConfig(itemId, index, newConfig)
    }

    override fun insertButtonConfig(itemId: String, index: Int, newConfig: ButtonConfig, forceShift: Boolean, onResult: (Boolean) -> Unit) {
        pageManagementDelegate.insertButtonConfig(itemId, index, newConfig, forceShift, onResult)
    }

    override fun moveButtonWithInsert(itemId: String, fromIndex: Int, toIndex: Int) {
        pageManagementDelegate.moveButtonWithInsert(itemId, fromIndex, toIndex)
    }

    override fun undo(onSuccess: (String) -> Unit) {
        pageManagementDelegate.undo(onSuccess)
    }

    override val canUndo: StateFlow<Boolean> = pageManagementDelegate.canUndo

    override fun updateGridSettings(
        itemId: String,
        update: GridSettingsUpdate
    ) {
        updatePageSettings(itemId, update)
    }

    override val isExecuting: StateFlow<Boolean> = actionExecutor.isExecuting

    override fun executeButtonAction(config: ButtonConfig) {
        actionExecutor.executeButtonAction(config)
    }

    override fun isTextCached(text: String): Boolean {
        return ttsHelper.isCached(text)
    }

    override fun prefetchText(text: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            ttsHelper.prefetch(text)
            onComplete()
        }
    }

    override fun isTtsElevenLabs(): Boolean {
        return settingsRepository.ttsEngine == "elevenlabs"
    }

    override fun createNewPage(
        name: String,
        rows: Int,
        columns: Int,
        bookId: String,
        templateId: String?,
        onCreated: (String) -> Unit
    ) {
        pageManagementDelegate.createNewPage(name, rows, columns, bookId, templateId, onCreated)
    }

    fun updatePageSettings(
        pageId: String, 
        update: GridSettingsUpdate
    ) = pageManagementDelegate.updatePageSettings(pageId, update)

    override fun updateRowName(itemId: String, rowIndex: Int, newName: String) {
        pageManagementDelegate.updateRowName(itemId, rowIndex, newName)
    }

    override fun suggestRowName(itemId: String, rowIndex: Int, onResult: (String) -> Unit) {
        val page = pageManagementDelegate.unfilteredPages.value.find { it.id == itemId }
        if (page == null) {
            onResult("")
            return
        }

        if (!settingsRepository.isGeminiEnabled) {
            android.widget.Toast.makeText(getApplication(), "Gemini ist in den Einstellungen deaktiviert.", android.widget.Toast.LENGTH_SHORT).show()
            onResult("")
            return
        }

        val columns = page.columns
        val labels = (0 until columns).mapNotNull { c ->
            val globalIndex = rowIndex * com.andreas_kratzer.ghosttalk.core.util.GridUtils.MAX_GRID_SIZE + c
            val config = page.buttonConfigs.getOrNull(globalIndex)
            if (config != null && config.isActive && config.label.isNotBlank()) {
                config.label
            } else null
        }

        if (labels.isEmpty()) {
            android.widget.Toast.makeText(getApplication(), "Keine aktiven Buttons in dieser Zeile vorhanden.", android.widget.Toast.LENGTH_SHORT).show()
            onResult("")
            return
        }

        viewModelScope.launch {
            try {
                val prompt = "Analysiere diese Liste von Begriffen, die sich in einer Zeile auf einer Kommunikations-Tafel für Unterstützte Kommunikation befinden: ${labels.joinToString(", ")}. Schlage einen einzigen, kurzen Begriff (maximal 2 Wörter) vor, der als Name für diese Zeile dienen kann. Antworte NUR mit diesem Begriff, ohne Satzzeichen, Anführungszeichen oder zusätzliche Erklärungen."
                val response = geminiUseCase.generateResponse(prompt)
                val cleaned = response.trim().removeSurrounding("\"").removeSurrounding("'").trim()
                onResult(cleaned)
            } catch (e: Exception) {
                Log.e("PageViewModel", "Error generating row name suggestion", e)
                android.widget.Toast.makeText(getApplication(), "Fehler bei der Generierung: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                onResult("")
            }
        }
    }

    override fun moveRow(itemId: String, fromRow: Int, toRow: Int) {
        pageManagementDelegate.moveRow(itemId, fromRow, toRow)
    }

    override fun moveButton(itemId: String, fromIndex: Int, toIndex: Int) {
        pageManagementDelegate.moveButton(itemId, fromIndex, toIndex)
    }

    override fun moveButtonToPage(
        fromPageId: String,
        fromIndex: Int,
        toPageId: String,
        forceMove: Boolean,
        onResult: (com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonToPageUseCase.MoveResult) -> Unit
    ) {
        pageManagementDelegate.moveButtonToPage(fromPageId, fromIndex, toPageId, forceMove, onResult)
    }

    override fun duplicateButtonToPage(
        fromPageId: String,
        fromIndex: Int,
        toPageId: String,
        forceMove: Boolean,
        onResult: (com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonToPageUseCase.MoveResult) -> Unit
    ) {
        pageManagementDelegate.duplicateButtonToPage(fromPageId, fromIndex, toPageId, forceMove, onResult)
    }

    fun deletePage(page: Page, deleteUsages: Boolean = false) = pageManagementDelegate.deletePage(page, deleteUsages)
    suspend fun getPageUsages(pageId: String) = pageManagementDelegate.getPageUsages(pageId)
    fun importFromJson(jsonString: String, bookId: String, regenerateIds: Boolean? = true, onSuccess: () -> Unit, onError: (String) -> Unit) =
        pageManagementDelegate.importFromJson(jsonString, bookId, regenerateIds, restoreSyncSettings = true, onSuccess, onError)

    fun activateButtons(usages: List<UsageLocation>, isActive: Boolean) =
        pageManagementDelegate.activateButtons(usages, isActive)

    fun duplicatePage(pageId: String, suffix: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val newId = pageManagementDelegate.pageRepository.duplicatePage(pageId, suffix)
            onResult(newId)
        }
    }

    @Suppress("unused")
    suspend fun exportToJson(): String = pageManagementDelegate.exportToJson()

    override fun speakTtsPreview(text: String, onDone: () -> Unit) {
        ttsHelper.speak(text, queueMode = 0, onDone = onDone, onError = { onDone() })
    }

    override fun stopTtsPreview() {
        ttsHelper.stopAll()
    }

    fun refreshHueDevicesCache(silentOnFailure: Boolean = false, onResult: ((Boolean) -> Unit)? = null) {
        val ip = settingsRepository.hueBridgeIp
        val username = settingsRepository.hueUsername
        if (ip.isBlank() || username.isBlank()) {
            if (!silentOnFailure) {
                android.widget.Toast.makeText(getApplication(), "Bitte zuerst in den Einstellungen koppeln.", android.widget.Toast.LENGTH_LONG).show()
            }
            onResult?.invoke(false)
            return
        }

        viewModelScope.launch {
            val fetchedDevices = philipsHueManager.getLocalLights(ip, username)
            if (fetchedDevices.isNotEmpty()) {
                val array = org.json.JSONArray()
                fetchedDevices.forEach { device ->
                    val obj = org.json.JSONObject().apply {
                        put("id", device.id)
                        put("name", device.name)
                        put("type", device.type)
                    }
                    array.put(obj)
                }
                settingsRepository.hueCachedDevices = array.toString()
                android.widget.Toast.makeText(getApplication(), "${fetchedDevices.size} Lampen geladen und im Cache gespeichert.", android.widget.Toast.LENGTH_LONG).show()
                onResult?.invoke(true)
            } else {
                if (!silentOnFailure) {
                    android.widget.Toast.makeText(getApplication(), "Konnte Bridge nicht erreichen. Alter Cache wird beibehalten.", android.widget.Toast.LENGTH_LONG).show()
                }
                onResult?.invoke(false)
            }
        }
    }

    fun connectSpotify(ctx: android.content.Context) {
        val authUrl = spotifyManager.getAuthorizationUrl()
        val intent = Intent(Intent.ACTION_VIEW, authUrl.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }

    fun disconnectSpotify() {
        spotifyManager.disconnect()
        _spotifyPlaylists.value = emptyList()
    }

    fun loadSpotifyPlaylists() {
        viewModelScope.launch {
            if (settingsRepository.spotifyAccessToken.isNullOrBlank()) {
                _spotifyPlaylists.value = emptyList()
                return@launch
            }
            _isLoadingPlaylists.value = true
            try {
                val playlists = spotifyManager.getPlaylists()
                _spotifyPlaylists.value = playlists
            } catch (e: Exception) {
                Log.e("PageViewModel", "Failed to load Spotify playlists", e)
            } finally {
                _isLoadingPlaylists.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        actionExecutor.stopActions()
        scanCoordinator.clear()
    }

    // --- Page Split Wizard States & Functions ---

    private val _pageSplitProposal = MutableStateFlow<SplitPageUseCase.PageSplitProposal?>(null)
    val pageSplitProposal: StateFlow<SplitPageUseCase.PageSplitProposal?> = _pageSplitProposal.asStateFlow()

    private val _isPageSplitLoading = MutableStateFlow(false)
    val isPageSplitLoading: StateFlow<Boolean> = _isPageSplitLoading.asStateFlow()

    fun generatePageSplitPrompt(buttonLabels: List<String>): String {
        return splitPageUseCase.generatePrompt(buttonLabels)
    }

    fun parsePageSplitProposal(response: String) {
        try {
            val parsed = splitPageUseCase.parseResponse(response)
            _pageSplitProposal.value = parsed
        } catch (e: Exception) {
            Log.e("PageViewModel", "Error parsing page split proposal", e)
            throw e
        }
    }

    fun clearPageSplitProposal() {
        _pageSplitProposal.value = null
    }

    private val _currentProposalFilter = MutableStateFlow(ProposalFilter.ALL)
    val currentProposalFilter: StateFlow<ProposalFilter> = _currentProposalFilter.asStateFlow()

    private val _currentProposalSort = MutableStateFlow(ProposalSort.TIME_SAVED_DESC)
    val currentProposalSort: StateFlow<ProposalSort> = _currentProposalSort.asStateFlow()

    fun setProposalFilter(filter: ProposalFilter) {
        _currentProposalFilter.value = filter
    }

    fun setProposalSort(sort: ProposalSort) {
        _currentProposalSort.value = sort
    }

    val layoutOptimizationProposals: StateFlow<List<com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PageLayoutOptimizer.LayoutOptimizationProposal>> = combine(
        unfilteredPages,
        activeBookId,
        currentProposalFilter,
        currentProposalSort,
        buttonHistory
    ) { allPages, bookId, filter, sort, historyEvents ->
        if (bookId != null && allPages.isNotEmpty()) {
            val delay = settingsRepository.scanDelayMillis
            val pattern = settingsRepository.defaultScanPattern
            val startPageId = settingsRepository.defaultStartPageId
            val raw = pageLayoutOptimizer.analyzePages(allPages, startPageId, delay, pattern, historyEvents)
            raw.filter { proposal ->
                when (filter) {
                    ProposalFilter.ALL -> true
                    ProposalFilter.SPLIT_ONLY -> proposal is com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PageLayoutOptimizer.LayoutOptimizationProposal.SplitPageProposal
                    ProposalFilter.PATTERN_ONLY -> proposal is com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PageLayoutOptimizer.LayoutOptimizationProposal.ChangeScanPatternProposal
                    ProposalFilter.START_PAGE_ONLY -> proposal.pageId == startPageId
                    ProposalFilter.CRITICAL_ONLY -> {
                        val currentScanTime = when (proposal) {
                            is com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PageLayoutOptimizer.LayoutOptimizationProposal.SplitPageProposal -> proposal.currentAverageScanTimeSec
                            is com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PageLayoutOptimizer.LayoutOptimizationProposal.ChangeScanPatternProposal -> proposal.currentAverageScanTimeSec
                            is com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PageLayoutOptimizer.LayoutOptimizationProposal.ChangeScanDelayProposal -> (proposal.currentScanDelayMs / 1000.0) * 4.0 // Baseline estimate
                            is com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PageLayoutOptimizer.LayoutOptimizationProposal.SpacerRelocateProposal -> 6.0
                        }
                        currentScanTime > 8.0
                    }
                }
            }.sortedWith(
                when (sort) {
                    ProposalSort.TIME_SAVED_DESC -> compareByDescending { proposal ->
                        when (proposal) {
                            is com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PageLayoutOptimizer.LayoutOptimizationProposal.SplitPageProposal ->
                                proposal.currentAverageScanTimeSec - proposal.estimatedNewAverageScanTimeSec
                            is com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PageLayoutOptimizer.LayoutOptimizationProposal.ChangeScanPatternProposal ->
                                proposal.currentAverageScanTimeSec - proposal.estimatedNewAverageScanTimeSec
                            is com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PageLayoutOptimizer.LayoutOptimizationProposal.ChangeScanDelayProposal ->
                                1.5 // Static priority score for delay change
                            is com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PageLayoutOptimizer.LayoutOptimizationProposal.SpacerRelocateProposal ->
                                proposal.accidentalClickCount * 0.5 // Priority based on errors
                        }
                    }
                    ProposalSort.PAGE_NAME_ASC -> compareBy { it.pageName.lowercase() }
                    ProposalSort.BUTTONS_COUNT_DESC -> compareByDescending { proposal ->
                        when (proposal) {
                            is com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PageLayoutOptimizer.LayoutOptimizationProposal.SplitPageProposal -> proposal.activeButtonsCount
                            is com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PageLayoutOptimizer.LayoutOptimizationProposal.ChangeScanPatternProposal -> proposal.activeButtonsCount
                            is com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PageLayoutOptimizer.LayoutOptimizationProposal.ChangeScanDelayProposal -> 0
                            is com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PageLayoutOptimizer.LayoutOptimizationProposal.SpacerRelocateProposal -> 0
                        }
                    }
                    ProposalSort.CURRENT_TIME_DESC -> compareByDescending { proposal ->
                        when (proposal) {
                            is com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PageLayoutOptimizer.LayoutOptimizationProposal.SplitPageProposal -> proposal.currentAverageScanTimeSec
                            is com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PageLayoutOptimizer.LayoutOptimizationProposal.ChangeScanPatternProposal -> proposal.currentAverageScanTimeSec
                            is com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PageLayoutOptimizer.LayoutOptimizationProposal.ChangeScanDelayProposal -> proposal.currentScanDelayMs / 1000.0
                            is com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PageLayoutOptimizer.LayoutOptimizationProposal.SpacerRelocateProposal -> 0.0
                        }
                    }
                }
            )
        } else {
            emptyList()
        }
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    fun changePageScanPattern(pageId: String, pattern: String) {
        viewModelScope.launch {
            try {
                pageManagementDelegate.updatePageSettings(
                    pageId = pageId,
                    update = GridSettingsUpdate(
                        scanPattern = OptionalProperty(pattern)
                    )
                )
            } catch (e: Exception) {
                Log.e("PageViewModel", "Failed to update page scan pattern", e)
            }
        }
    }

    fun changeScanDelay(delayMs: Long) {
        viewModelScope.launch {
            try {
                settingsRepository.scanDelayMillis = delayMs
            } catch (e: Exception) {
                Log.e("PageViewModel", "Failed to update scan delay", e)
            }
        }
    }

    fun applySpacerRelocate(pageId: String, buttonId: String, intendedButtonId: String) {
        viewModelScope.launch {
            try {
                val page = pageManagementDelegate.getPageById(pageId) ?: return@launch
                val fromIndex = page.buttonConfigs.indexOfFirst { it?.id == buttonId }
                val toIndex = page.buttonConfigs.indexOfFirst { it?.id == intendedButtonId }
                if (fromIndex != -1 && toIndex != -1) {
                    pageManagementDelegate.moveButton(pageId, fromIndex, toIndex)
                }
            } catch (e: Exception) {
                Log.e("PageViewModel", "Failed to swap buttons for spacer relocate proposal", e)
            }
        }
    }

    fun shouldFilterButtonFromSplit(buttonConfig: ButtonConfig?, defaultStartPageId: String?, currentPageId: String?): Boolean {
        if (buttonConfig == null || !buttonConfig.isActive || buttonConfig.label.isBlank()) return true
        val action = buttonConfig.buttonAction
        if (action is NavigateToPageButtonAction) {
            // Filter out circular back-to-start navigation
            if (action.pageId == defaultStartPageId) return true
            // Filter out circular navigation pointing to this page itself
            if (action.pageId == currentPageId) return true
            // Filter out typical back/exit navigation labels
            val labelLower = buttonConfig.label.lowercase().trim()
            if (labelLower == "zurück zum start" || labelLower == "zurück" || labelLower == "back") return true
        }
        return false
    }

    fun generatePageSplitProposal(pageId: String) {
        viewModelScope.launch {
            _isPageSplitLoading.value = true
            try {
                val page = pageManagementDelegate.getPageById(pageId) ?: return@launch
                val defaultStartPageId = settingsRepository.defaultStartPageId
                val labels = page.buttonConfigs
                    .filter { !shouldFilterButtonFromSplit(it, defaultStartPageId, pageId) }
                    .map { it!!.label }
                
                if (labels.isEmpty()) {
                    _isPageSplitLoading.value = false
                    return@launch
                }
                
                val result = splitPageUseCase.execute(labels)
                _pageSplitProposal.value = result
            } catch (e: Exception) {
                Log.e("PageViewModel", "Error generating page split proposal", e)
                android.widget.Toast.makeText(getApplication(), "Fehler beim Erstellen des Vorschlags: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            } finally {
                _isPageSplitLoading.value = false
            }
        }
    }

    fun applyPageSplit(pageId: String, proposal: SplitPageUseCase.PageSplitProposal) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isPageSplitLoading.value = true
                
                val finalSourcePage = pageManagementDelegate.pageRepository.runInTransaction {
                    val sourcePage = pageManagementDelegate.getPageById(pageId) ?: throw IllegalArgumentException("Source page not found")
                    val bookId = sourcePage.bookId
                    val currentPages = pageManagementDelegate.unfilteredPages.value

                    // 1. Neue Seiten erstellen für jede Kategorie
                    val categoryPageIds = mutableMapOf<String, String>()
                    proposal.categories.forEach { category ->
                        val buttonCount = category.buttonLabels.size
                        val (rows, cols) = when {
                            buttonCount <= 4 -> 2 to 2
                            buttonCount <= 9 -> 3 to 3
                            buttonCount <= 16 -> 4 to 4
                            else -> 5 to 5
                        }
                        val newId = createPageUseCase.execute(
                            name = category.name,
                            rows = rows,
                            columns = cols,
                            bookId = bookId,
                            currentPages = currentPages
                        )
                        categoryPageIds[category.name] = newId
                    }

                    // 2. Buttons von Quellseite auf Zielseiten verschieben
                    val sourcePageUpdated = pageManagementDelegate.getPageById(pageId) ?: throw IllegalArgumentException("Source page not found after creation")
                    val sourceConfigs = sourcePageUpdated.buttonConfigs.toMutableList()

                    proposal.categories.forEach { category ->
                        val targetPageId = categoryPageIds[category.name] ?: return@forEach
                        val targetPage = pageManagementDelegate.getPageById(targetPageId) ?: return@forEach
                        val targetConfigs = targetPage.buttonConfigs.toMutableList()

                        category.buttonLabels.forEach { label ->
                            val sourceIndex = sourceConfigs.indexOfFirst { it?.label == label }
                            if (sourceIndex != -1) {
                                val buttonToMove = sourceConfigs[sourceIndex] ?: return@forEach
                                val targetIndex = targetConfigs.indexOfFirst { it == null }
                                if (targetIndex != -1) {
                                    targetConfigs[targetIndex] = buttonToMove
                                    sourceConfigs[sourceIndex] = null
                                }
                            }
                        }
                        pageManagementDelegate.pageRepository.updatePage(targetPage.copy(buttonConfigs = targetConfigs))
                    }

                    // 3. Quellseite neu sortieren (freie Plätze entfernen) und schrumpfen
                    val remainingConfigs = sourceConfigs.filterNotNull()
                    
                    // Neue Navigation-Buttons vorbereiten
                    val newCategoryButtons = proposal.categories.mapNotNull { category ->
                        val targetPageId = categoryPageIds[category.name] ?: return@mapNotNull null
                        ButtonConfig(
                            id = java.util.UUID.randomUUID().toString(),
                            label = category.name,
                            spokenText = "Öffne ${category.name}",
                            buttonAction = NavigateToPageButtonAction(pageId = targetPageId),
                            auditoryCue = AuditoryCue.TextToSpeechCue("Öffne ${category.name}")
                        )
                    }
                    
                    val finalSourceButtons = remainingConfigs + newCategoryButtons
                    val finalCount = finalSourceButtons.size
                    
                    // Bestimme die optimal geschrumpfte Grid-Größe
                    val (sourceRows, sourceCols) = when {
                        finalCount <= 4 -> 2 to 2
                        finalCount <= 9 -> 3 to 3
                        finalCount <= 16 -> 4 to 4
                        finalCount <= 25 -> 5 to 5
                        finalCount <= 36 -> 6 to 6
                        else -> 7 to 7
                    }
                    
                    // Fülle die Knöpfe lückenlos in die sichtbaren Slots des neuen Grids
                    val finalConfigs = MutableList<ButtonConfig?>(com.andreas_kratzer.ghosttalk.core.util.GridUtils.TOTAL_SLOTS) { null }
                    var buttonIndex = 0
                    for (r in 0 until sourceRows) {
                        for (c in 0 until sourceCols) {
                            if (buttonIndex < finalSourceButtons.size) {
                                val globalPos = r * com.andreas_kratzer.ghosttalk.core.util.GridUtils.MAX_GRID_SIZE + c
                                finalConfigs[globalPos] = finalSourceButtons[buttonIndex]
                                buttonIndex++
                            }
                        }
                    }
                    
                    // Quellseite speichern
                    val updatedPage = sourcePageUpdated.copy(
                        buttonConfigs = finalConfigs,
                        rows = sourceRows,
                        columns = sourceCols
                    )
                    pageManagementDelegate.pageRepository.updatePage(updatedPage)
                    updatedPage
                }

                withContext(Dispatchers.Main) {
                    pageManagementDelegate.setCurrentPage(finalSourcePage)
                    android.widget.Toast.makeText(getApplication(), "Seite erfolgreich aufgeteilt!", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("PageViewModel", "Error applying page split", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(getApplication(), "Fehler beim Anwenden: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            } finally {
                _isPageSplitLoading.value = false
                _pageSplitProposal.value = null
            }
        }
    }

    // --- AI Book Restructuring States & Functions ---

    fun togglePageSelection(pageId: String) {
        val current = _selectedPageIds.value
        _selectedPageIds.value = if (current.contains(pageId)) {
            current - pageId
        } else {
            current + pageId
        }
    }

    fun selectAllPages() {
        _selectedPageIds.value = pageManagementDelegate.unfilteredPages.value.map { it.id }.toSet()
    }

    fun selectActivePagesOnly() {
        val activeIds = pageManagementDelegate.activeTargetPageIds.value
        _selectedPageIds.value = pageManagementDelegate.unfilteredPages.value
            .filter { activeIds.contains(it.id) }
            .map { it.id }
            .toSet()
    }

    private fun saveProposalToCache(bookId: String, proposal: BookRestructureProposal) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cacheDir = getApplication<Application>().cacheDir ?: return@launch
                val file = java.io.File(cacheDir, "ai_restructure_proposal_${bookId}.json")
                val json = kotlinx.serialization.json.Json.encodeToString(BookRestructureProposal.serializer(), proposal)
                file.writeText(json)
            } catch (e: Exception) {
                Log.e("PageViewModel", "Error saving proposal to cache", e)
            }
        }
    }

    fun loadProposalFromCache(bookId: String): BookRestructureProposal? {
        return try {
            val cacheDir = getApplication<Application>().cacheDir ?: return null
            val file = java.io.File(cacheDir, "ai_restructure_proposal_${bookId}.json")
            if (file.exists()) {
                val json = file.readText()
                kotlinx.serialization.json.Json.decodeFromString(BookRestructureProposal.serializer(), json)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("PageViewModel", "Error loading proposal from cache", e)
            null
        }
    }

    private val _isAiRestructureLoading = MutableStateFlow(false)
    val isAiRestructureLoading: StateFlow<Boolean> = _isAiRestructureLoading.asStateFlow()

    fun generateAiRestructureProposal() {
        val bookId = activeBookId.value ?: return
        viewModelScope.launch(Dispatchers.Default) {
            _isAiRestructureLoading.value = true
            _aiRestructureError.value = null
            try {
                // Fetch stats to include click count
                val stats = buttonUsageRepository.getGroupedUsageStats(bookId)
                val clickCounts = stats.flatMap { it.children }
                    .associate { it.buttonConfigId to it.usageCount }

                // Retrieve all pages of the book
                val pages = pageManagementDelegate.unfilteredPages.value

                // Format pages and buttons into JSON representation
                val pagesArray = org.json.JSONArray()
                pages.forEach { page ->
                    if (_selectedPageIds.value.contains(page.id)) {
                        val pageObj = org.json.JSONObject()
                        pageObj.put("pageName", page.name)
                        val buttonsArray = org.json.JSONArray()
                        page.buttonConfigs.forEach { btn ->
                            if (btn != null && btn.isActive && btn.label.isNotBlank()) {
                                val btnObj = org.json.JSONObject()
                                btnObj.put("label", btn.label)
                                btnObj.put("clicks", clickCounts[btn.id] ?: 0)
                                val action = btn.buttonAction
                                if (action is NavigateToPageButtonAction) {
                                    val targetPageName = pages.find { it.id == action.pageId }?.name ?: ""
                                    btnObj.put("destinationPage", targetPageName)
                                }
                                buttonsArray.put(btnObj)
                            }
                        }
                        pageObj.put("buttons", buttonsArray)
                        pagesArray.put(pageObj)
                    }
                }

                val pagesJsonString = pagesArray.toString()
                val proposal = bookRestructureProposalUseCase.execute(pagesJsonString, _aiRestructureScope.value)
                _aiRestructureProposal.value = proposal
                saveProposalToCache(bookId, proposal)
            } catch (e: Exception) {
                Log.e("PageViewModel", "Error generating AI restructure proposal", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Fehler: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            } finally {
                _isAiRestructureLoading.value = false
            }
        }
    }

    fun loadMoreAiRestructureProposals() {
        val bookId = activeBookId.value ?: return
        val currentProposal = _aiRestructureProposal.value ?: return
        viewModelScope.launch(Dispatchers.Default) {
            _isAiRestructureLoading.value = true
            _aiRestructureError.value = null
            try {
                val stats = buttonUsageRepository.getGroupedUsageStats(bookId)
                val clickCounts = stats.flatMap { it.children }
                    .associate { it.buttonConfigId to it.usageCount }

                val pages = pageManagementDelegate.unfilteredPages.value

                val pagesArray = org.json.JSONArray()
                pages.forEach { page ->
                    if (_selectedPageIds.value.contains(page.id)) {
                        val pageObj = org.json.JSONObject()
                        pageObj.put("pageName", page.name)
                        val buttonsArray = org.json.JSONArray()
                        page.buttonConfigs.forEach { btn ->
                            if (btn != null && btn.isActive && btn.label.isNotBlank()) {
                                val btnObj = org.json.JSONObject()
                                btnObj.put("label", btn.label)
                                btnObj.put("clicks", clickCounts[btn.id] ?: 0)
                                val action = btn.buttonAction
                                if (action is NavigateToPageButtonAction) {
                                    val targetPageName = pages.find { it.id == action.pageId }?.name ?: ""
                                    btnObj.put("destinationPage", targetPageName)
                                }
                                buttonsArray.put(btnObj)
                            }
                        }
                        pageObj.put("buttons", buttonsArray)
                        pagesArray.put(pageObj)
                    }
                }

                val pagesJsonString = pagesArray.toString()
                
                val existingActionsArray = org.json.JSONArray()
                currentProposal.actions.forEach { act ->
                    val actObj = org.json.JSONObject()
                    actObj.put("type", act.type)
                    actObj.put("rationale", act.rationale)
                    act.buttonLabel?.let { actObj.put("buttonLabel", it) }
                    act.sourcePageName?.let { actObj.put("sourcePageName", it) }
                    act.targetPageName?.let { actObj.put("targetPageName", it) }
                    existingActionsArray.put(actObj)
                }
                val existingProposalsJson = org.json.JSONObject().apply {
                    put("actions", existingActionsArray)
                }.toString()

                val moreProposal = bookRestructureProposalUseCase.executeLoadMore(pagesJsonString, existingProposalsJson)
                val combinedActions = currentProposal.actions + moreProposal.actions
                val combinedProposal = BookRestructureProposal(combinedActions)
                
                _aiRestructureProposal.value = combinedProposal
                saveProposalToCache(bookId, combinedProposal)
            } catch (e: Exception) {
                Log.e("PageViewModel", "Error loading more AI proposals", e)
                _aiRestructureError.value = e.localizedMessage
            } finally {
                _isAiRestructureLoading.value = false
            }
        }
    }

    fun clearAiRestructureProposal() {
        _aiRestructureProposal.value = null
        val bookId = activeBookId.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cacheDir = getApplication<Application>().cacheDir ?: return@launch
                val file = java.io.File(cacheDir, "ai_restructure_proposal_${bookId}.json")
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                Log.e("PageViewModel", "Error deleting proposal cache", e)
            }
        }
    }

    fun applyAiRestructureProposal(proposal: BookRestructureProposal, onResult: (String) -> Unit) {
        val currentBookId = activeBookId.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isAiRestructureLoading.value = true
            try {
                val newBookId = cloneBookUseCase.execute(currentBookId, proposal)
                
                // Fetch the default start page of the new book from database directly to load it immediately
                val startId = settingsRepository.getDefaultStartPageIdForBook(newBookId)
                val pages = pageManagementDelegate.pageRepository.getPagesForBook(newBookId)
                val startPage = pages.find { it.id == startId } ?: pages.firstOrNull()

                withContext(Dispatchers.Main) {
                    setActiveBookId(newBookId)
                    if (startPage != null) {
                        loadPage(startPage)
                    }
                    onResult(newBookId)
                }
            } catch (e: Exception) {
                Log.e("PageViewModel", "Error applying AI restructure proposal", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Fehler beim Anwenden: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            } finally {
                _isAiRestructureLoading.value = false
            }
        }
    }

    fun applySingleAiRestructureAction(action: com.andreas_kratzer.ghosttalk.core.model.RestructureAction, onResult: (String) -> Unit) {
        val currentBookId = activeBookId.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isAiRestructureLoading.value = true
            try {
                val singleProposal = com.andreas_kratzer.ghosttalk.core.model.BookRestructureProposal(listOf(action))
                val newBookId = cloneBookUseCase.execute(currentBookId, singleProposal)
                
                // Fetch the default start page of the new book from database directly to load it immediately
                val startId = settingsRepository.getDefaultStartPageIdForBook(newBookId)
                val pages = pageManagementDelegate.pageRepository.getPagesForBook(newBookId)
                val startPage = pages.find { it.id == startId } ?: pages.firstOrNull()

                withContext(Dispatchers.Main) {
                    setActiveBookId(newBookId)
                    if (startPage != null) {
                        loadPage(startPage)
                    }
                    onResult(newBookId)
                }
            } catch (e: java.lang.Exception) {
                Log.e("PageViewModel", "Error applying single AI restructure action", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Fehler beim Anwenden: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            } finally {
                _isAiRestructureLoading.value = false
            }
        }
    }
}

enum class ProposalFilter {
    ALL,
    SPLIT_ONLY,
    PATTERN_ONLY,
    START_PAGE_ONLY,
    CRITICAL_ONLY
}

enum class ProposalSort {
    TIME_SAVED_DESC,
    PAGE_NAME_ASC,
    BUTTONS_COUNT_DESC,
    CURRENT_TIME_DESC
}

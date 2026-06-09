package com.andreas_kratzer.ghosttalk.ui.pages

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.andreas_kratzer.ghosttalk.core.actions.ActionExecutor
import com.andreas_kratzer.ghosttalk.core.ai.domain.GeminiUseCase
import com.andreas_kratzer.ghosttalk.core.ai.domain.SplitPageUseCase
import com.andreas_kratzer.ghosttalk.core.ai.domain.UpdateSmartPredictionsUseCase
import com.andreas_kratzer.ghosttalk.core.call.CallState
import com.andreas_kratzer.ghosttalk.core.cloud.PhilipsHueManager
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.domain.pages.CreatePageUseCase
import com.andreas_kratzer.ghosttalk.core.domain.pages.UsageLocation
import com.andreas_kratzer.ghosttalk.core.model.Book
import com.andreas_kratzer.ghosttalk.core.model.BookRestructureProposal
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ButtonTemplate
import com.andreas_kratzer.ghosttalk.core.model.GridSettingsUpdate
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.scanning.ScanCoordinator
import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PageViewModel @Inject constructor(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
    override val settingsRepository: SettingsRepository,
    private val bookRepository: com.andreas_kratzer.ghosttalk.core.data.BookRepository,
    private val ttsHelper: TextToSpeechHelper,
    val featureGuard: FeatureGuard,
    val pageManagementDelegate: PageManagementDelegate,
    val interactionDelegate: InteractionDelegate,
    val screenManagementDelegate: ScreenManagementDelegate,
    smartPredictionDelegate: SmartPredictionDelegate,
    val callManagementDelegate: com.andreas_kratzer.ghosttalk.ui.pages.delegates.CallManagementDelegate,
    val navigationDelegate: com.andreas_kratzer.ghosttalk.ui.pages.delegates.NavigationDelegate,
    val aiRestructureDelegate: com.andreas_kratzer.ghosttalk.ui.pages.delegates.AiRestructureDelegate,
    val analyticsDelegate: com.andreas_kratzer.ghosttalk.ui.pages.delegates.AnalyticsDelegate,
    val smartIntegrationDelegate: com.andreas_kratzer.ghosttalk.ui.pages.delegates.SmartIntegrationDelegate,
    val suggestionsDelegate: com.andreas_kratzer.ghosttalk.ui.pages.delegates.SuggestionsDelegate,
    val ttsPreviewDelegate: com.andreas_kratzer.ghosttalk.ui.pages.delegates.TtsPreviewDelegate,
    val pageSplitDelegate: com.andreas_kratzer.ghosttalk.ui.pages.delegates.PageSplitDelegate,
    val layoutWizardDelegate: com.andreas_kratzer.ghosttalk.ui.pages.delegates.LayoutWizardDelegate,
    val buttonTemplateDelegate: com.andreas_kratzer.ghosttalk.ui.pages.delegates.ButtonTemplateDelegate,
    val pageResolutionDelegate: com.andreas_kratzer.ghosttalk.ui.pages.delegates.PageResolutionDelegate,
    updateSmartPredictionsUseCase: UpdateSmartPredictionsUseCase,
    val actionExecutor: ActionExecutor,
    private val scanCoordinator: ScanCoordinator,
    private val geminiUseCase: GeminiUseCase,
    override val philipsHueManager: PhilipsHueManager,
    private val createPageUseCase: CreatePageUseCase,
    private val buttonUsageRepository: com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository,
    private val splitPageUseCase: SplitPageUseCase
) : AndroidViewModel(application), com.andreas_kratzer.ghosttalk.ui.util.GridEditorActions {



    val activeBookId = pageManagementDelegate.activeBookId

    val userModeSessions = analyticsDelegate.userModeSessions

    fun clearUserModeSessions() = analyticsDelegate.clearUserModeSessions()

    val currentPageId = pageManagementDelegate.currentPageId
    val searchQuery = pageManagementDelegate.searchQuery
    val filteredPages = pageManagementDelegate.filteredPages
    val unfilteredPages = pageManagementDelegate.unfilteredPages
    val allPages = pageManagementDelegate.allPagesFlow
    val currentPage = pageManagementDelegate.currentPage
    val templates = pageManagementDelegate.templates
    val activeTargetPageIds = pageManagementDelegate.activeTargetPageIds

    override val buttonTemplates: StateFlow<List<ButtonTemplate>>
        get() = buttonTemplateDelegate.buttonTemplates

    override val buttonHistory = analyticsDelegate.buttonHistory

    val isCalculatingRecommendations = analyticsDelegate.isCalculatingRecommendations

    override val shortcutRecommendations = analyticsDelegate.shortcutRecommendations

    override fun applyShortcutRecommendation(
        recommendation: com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PathAnalyzer.ShortcutRecommendation,
        onResult: (Boolean, String) -> Unit
    ) {
        analyticsDelegate.applyShortcutRecommendation(recommendation, onResult)
    }

    override fun saveButtonAsTemplate(name: String, config: ButtonConfig) {
        buttonTemplateDelegate.saveButtonAsTemplate(name, config)
    }

    override fun deleteButtonTemplate(template: ButtonTemplate) {
        buttonTemplateDelegate.deleteButtonTemplate(template)
    }

    override fun updateButtonTemplate(template: ButtonTemplate) {
        buttonTemplateDelegate.updateButtonTemplate(template)
    }

    override fun updateButtonTemplatesOrder(templates: List<ButtonTemplate>) {
        buttonTemplateDelegate.updateButtonTemplatesOrder(templates)
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

    override val spotifyUserDisplayName = smartIntegrationDelegate.spotifyUserDisplayName
    override val spotifyPlaylists = smartIntegrationDelegate.spotifyPlaylists
    override val isLoadingPlaylists = smartIntegrationDelegate.isLoadingPlaylists

    val staticRowPage: StateFlow<Page?> = pageResolutionDelegate.getStaticRowPage(
        scope = viewModelScope,
        activeBookId = activeBookId,
        allPages = allPages,
        smartPredictions = smartPredictions
    )

    override val isEditPreviewActive = pageResolutionDelegate.isEditPreviewActive

    fun toggleEditPreviewActive() {
        pageResolutionDelegate.toggleEditPreviewActive()
        Log.d("PageViewModel", "toggleEditPreviewActive: active = ${isEditPreviewActive.value}")
    }

    private val isPreviewOrUserMode = pageResolutionDelegate.getIsPreviewOrUserMode(
        scope = viewModelScope,
        isUserModeActive = isUserModeActive
    )

    override val resolvedPage: StateFlow<Page?> = pageResolutionDelegate.getResolvedPage(
        scope = viewModelScope,
        currentPage = currentPage,
        isPreviewOrUserMode = isPreviewOrUserMode,
        smartPredictions = smartPredictions,
        activeBookId = activeBookId,
        unfilteredPages = unfilteredPages
    )

    // --- Caregiver Visual Analytics Overlay States ---
    override val isAnalyticsOverlayEnabled = analyticsDelegate.isAnalyticsOverlayEnabled

    fun toggleAnalyticsOverlay() {
        analyticsDelegate.toggleAnalyticsOverlay()
    }

    override val pageMetrics = analyticsDelegate.pageMetrics

    override suspend fun getMarkovSuccessors(buttonId: String): List<Pair<String, Int>> {
        val bookId = activeBookId.value ?: return emptyList()
        return buttonUsageRepository.getMarkovSuccessors(bookId, buttonId)
    }

    val focusedButtonIndex = scanCoordinator.focusedButtonIndex
    val focusedRowIndex = scanCoordinator.focusedRowIndex
    val isScanning = scanCoordinator.isScanning

    // --- Telephony Call States ---
    val callState = callManagementDelegate.callState
    val callerName = callManagementDelegate.callerName
    val callerPhone = callManagementDelegate.callerPhone
    val callDurationSeconds = callManagementDelegate.callDurationSeconds
    val isOutgoing = callManagementDelegate.isOutgoing
    val isSimulatedCall = callManagementDelegate.isSimulatedCall
    val isHangUpButtonFocused = callManagementDelegate.isHangUpButtonFocused
    val hangUpPressCount = callManagementDelegate.hangUpPressCount
    val focusedCallScreenButton = callManagementDelegate.focusedCallScreenButton


    fun startCallScanning() {
        callManagementDelegate.startCallScanning(viewModelScope)
    }

    fun stopCallScanning() {
        callManagementDelegate.stopCallScanning()
    }

    fun loadStartPage() {
        navigationDelegate.loadStartPage()
    }

    val selectedPageIds: StateFlow<Set<String>> = aiRestructureDelegate.selectedPageIds

    val aiRestructureProposal: StateFlow<BookRestructureProposal?> = aiRestructureDelegate.aiRestructureProposal
    val aiHierarchyProposal: StateFlow<com.andreas_kratzer.ghosttalk.core.model.BookHierarchyProposal?> = aiRestructureDelegate.aiHierarchyProposal
    val aiPageLayoutProposals: StateFlow<Map<String, com.andreas_kratzer.ghosttalk.core.model.PageLayoutProposal>> = aiRestructureDelegate.aiPageLayoutProposals
    val isAiHierarchyLoading: StateFlow<Boolean> = aiRestructureDelegate.isAiHierarchyLoading
    val isLoadingPageLayout: StateFlow<Map<String, Boolean>> = aiRestructureDelegate.isLoadingPageLayout
    val aiRestructureScope: StateFlow<String> = aiRestructureDelegate.aiRestructureScope
    val aiRestructureError: StateFlow<String?> = aiRestructureDelegate.aiRestructureError

    fun setAiRestructureScope(scope: String) {
        aiRestructureDelegate.setAiRestructureScope(scope)
    }

    fun clearAiRestructureError() {
        aiRestructureDelegate.clearAiRestructureError()
    }

    val activeBook: StateFlow<Book?> = activeBookId.flatMapLatest { id ->
        if (id != null) bookRepository.getBookByIdFlow(id) else flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        analyticsDelegate.init(viewModelScope, resolvedPage)
        smartIntegrationDelegate.init(viewModelScope)
        suggestionsDelegate.init(viewModelScope)
        ttsPreviewDelegate.init(viewModelScope)
        pageSplitDelegate.init(
            coroutineScope = viewModelScope,
            unfilteredPages = pageManagementDelegate.unfilteredPages,
            activeBookId = activeBookId,
            buttonHistory = analyticsDelegate.buttonHistory
        )
        layoutWizardDelegate.init(viewModelScope)
        navigationDelegate.init(
            scope = viewModelScope,
            savedStateHandle = savedStateHandle,
            activeBookState = activeBook,
            onPageChanging = { isSamePage, redoPrediction ->
                if (!isSamePage || redoPrediction) {
                    if (_smartPredictions.value != null) {
                        _smartPredictions.value = null
                    }
                }
            }
        )
        navigationDelegate.restoreState()

        loadSpotifyPlaylists()
        buttonTemplateDelegate.init(viewModelScope)
        pageManagementDelegate.init(viewModelScope)
        interactionDelegate.init(
            scope = viewModelScope,
            actionExecutor = actionExecutor,
            onPageLoadRequested = ::loadPage,
            onGoBackRequested = ::navigateBack,
            smartPredictions = _smartPredictions,
            currentBookIdFlow = activeBookId,
            onVocalSwitchTriggered = { action, label, positiveConfidence, _, threshold ->
                if (action != null) {
                    val config = ButtonConfig(
                        id = java.util.UUID.randomUUID().toString(),
                        label = label ?: "",
                        spokenText = label,
                        buttonAction = action
                    )
                    viewModelScope.launch {
                        actionExecutor.executeButtonAction(config)
                    }
                } else {
                    val focusedIndex = scanCoordinator.focusedButtonIndex.value
                    val focusedButtonConfig = focusedIndex?.let { resolvedPage.value?.buttonConfigs?.getOrNull(it) }
                    
                    val urgentKeywords = listOf("Schmerz", "Hilfe", "Notfall", "Aua", "Ja", "Nein", "Wasser")
                    val isUrgent = focusedButtonConfig?.label?.let { btnLabel ->
                        urgentKeywords.any { keyword -> btnLabel.contains(keyword, ignoreCase = true) }
                    } ?: false
                    
                    val cycleCount = scanCoordinator.currentCycleCount.value
                    
                    val requiredThreshold = if (isUrgent || cycleCount >= 2) {
                        0.70f
                    } else {
                        threshold
                    }
                    
                    if (positiveConfidence >= requiredThreshold) {
                        activateFocusedButton()
                    } else {
                        Log.d("PageViewModel", "Vocal Switch click blocked: positiveConfidence=$positiveConfidence, required=$requiredThreshold (isUrgent=$isUrgent, cycleCount=$cycleCount)")
                    }
                }
            }
        )
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
            staticRowPage = staticRowPage
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
            callManagementDelegate.callState.collect { state ->
                when (state) {
                    CallState.RINGING -> {
                        scanCoordinator.stopScanning()
                        ttsHelper.stopAll()
                        actionExecutor.stopActions()
                        startCallScanning()
                    }
                    CallState.DIALING,
                    CallState.ACTIVE -> {
                        scanCoordinator.stopScanning()
                        ttsHelper.stopAll()
                        actionExecutor.stopActions()
                        stopCallScanning()
                        callManagementDelegate.resetHangUpState()
                    }
                    CallState.NONE -> {
                        stopCallScanning()
                        callManagementDelegate.resetHangUpState()
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
            val flow = activeBookId
            flow.collect { bookId ->
                if (bookId != null) {
                    aiRestructureDelegate.setAiRestructureProposal(loadProposalFromCache(bookId))
                } else {
                    aiRestructureDelegate.setAiRestructureProposal(null)
                }
            }
        }

        // Initialize selectedPageIds to active/reachable pages by default
        viewModelScope.launch {
            val unfilteredFlow = pageManagementDelegate.unfilteredPages
            val activeTargetFlow = pageManagementDelegate.activeTargetPageIds
            combine(
                unfilteredFlow,
                activeTargetFlow
            ) { pages, activeIds ->
                Pair(pages, activeIds)
            }.collect { (pages, activeIds) ->
                if (pages.isNotEmpty() && aiRestructureDelegate.selectedPageIds.value.isEmpty()) {
                    aiRestructureDelegate.selectActivePagesOnly(pages, activeIds)
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
                    Toast.makeText(getApplication<Application>(), message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun updateSearchQuery(query: String) = pageManagementDelegate.updateSearchQuery(query)
    fun setActiveBookId(bookId: String?) {
        navigationDelegate.pageBackStack.clear()
        if (bookId != null) {
            settingsRepository.activeBookId = bookId
        }
        pageManagementDelegate.setActiveBookId(bookId)
    }

    fun loadPage(page: Page) {
        navigationDelegate.loadPage(page)
    }

    fun navigateBack() {
        navigationDelegate.navigateBack()
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
    fun activateFocusedButton() {
        if (callManagementDelegate.handleCallButtonPress()) {
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
        return ttsPreviewDelegate.isTextCached(text)
    }

    override fun prefetchText(text: String, onComplete: () -> Unit) {
        ttsPreviewDelegate.prefetchText(text, onComplete)
    }

    override fun isTtsElevenLabs(): Boolean {
        return ttsPreviewDelegate.isTtsElevenLabs()
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

    override val isGeminiEnabled: Boolean
        get() = settingsRepository.isGeminiEnabled

    override fun suggestButtonLabel(config: ButtonConfig, onResult: (String) -> Unit) {
        suggestionsDelegate.suggestButtonLabel(config, onResult)
    }

    override fun suggestRowName(itemId: String, rowIndex: Int, onResult: (String) -> Unit) {
        suggestionsDelegate.suggestRowName(itemId, rowIndex, onResult)
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
        ttsPreviewDelegate.speakTtsPreview(text, onDone)
    }

    override fun stopTtsPreview() {
        ttsPreviewDelegate.stopTtsPreview()
    }

    override fun refreshHueDevicesCache(silentOnFailure: Boolean, onResult: ((Boolean) -> Unit)?) {
        smartIntegrationDelegate.refreshHueDevicesCache(silentOnFailure, onResult)
    }

    override fun connectSpotify(context: android.content.Context) {
        smartIntegrationDelegate.connectSpotify(context)
    }

    override fun disconnectSpotify() {
        smartIntegrationDelegate.disconnectSpotify()
    }

    override fun loadSpotifyPlaylists() {
        smartIntegrationDelegate.loadSpotifyPlaylists()
    }

    override fun onCleared() {
        super.onCleared()
        actionExecutor.stopActions()
        scanCoordinator.clear()
    }

    // --- Page Split Wizard States & Functions ---

    val pageSplitProposal: StateFlow<SplitPageUseCase.PageSplitProposal?> = pageSplitDelegate.pageSplitProposal
    val isPageSplitLoading: StateFlow<Boolean> = pageSplitDelegate.isPageSplitLoading

    fun generatePageSplitPrompt(buttonLabels: List<String>): String {
        return pageSplitDelegate.generatePageSplitPrompt(buttonLabels)
    }

    fun parsePageSplitProposal(response: String) {
        pageSplitDelegate.parsePageSplitProposal(response)
    }

    fun clearPageSplitProposal() {
        pageSplitDelegate.clearPageSplitProposal()
    }

    val currentProposalFilter: StateFlow<ProposalFilter> = pageSplitDelegate.currentProposalFilter
    val currentProposalSort: StateFlow<ProposalSort> = pageSplitDelegate.currentProposalSort

    fun setProposalFilter(filter: ProposalFilter) {
        pageSplitDelegate.setProposalFilter(filter)
    }

    fun setProposalSort(sort: ProposalSort) {
        pageSplitDelegate.setProposalSort(sort)
    }

    val layoutOptimizationProposals: StateFlow<List<com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PageLayoutOptimizer.LayoutOptimizationProposal>> = pageSplitDelegate.layoutOptimizationProposals

    fun changePageScanPattern(pageId: String, pattern: String) {
        pageSplitDelegate.changePageScanPattern(pageId, pattern)
    }

    fun changeScanDelay(delayMs: Long) {
        pageSplitDelegate.changeScanDelay(delayMs)
    }

    fun applySpacerRelocate(pageId: String, buttonId: String, intendedButtonId: String) {
        pageSplitDelegate.applySpacerRelocate(pageId, buttonId, intendedButtonId)
    }

    fun generatePageSplitProposal(pageId: String) {
        pageSplitDelegate.generatePageSplitProposal(pageId)
    }

    fun applyPageSplit(pageId: String, proposal: SplitPageUseCase.PageSplitProposal) {
        pageSplitDelegate.applyPageSplit(pageId, proposal)
    }

    fun shouldFilterButtonFromSplit(buttonConfig: ButtonConfig?, defaultStartPageId: String?, currentPageId: String?): Boolean {
        return pageSplitDelegate.shouldFilterButtonFromSplit(buttonConfig, defaultStartPageId, currentPageId)
    }

    // --- AI Book Restructuring States & Functions ---

    fun togglePageSelection(pageId: String) {
        aiRestructureDelegate.togglePageSelection(pageId)
    }

    fun selectAllPages() {
        aiRestructureDelegate.selectAllPages(pageManagementDelegate.unfilteredPages.value)
    }

    fun selectActivePagesOnly() {
        aiRestructureDelegate.selectActivePagesOnly(
            pages = pageManagementDelegate.unfilteredPages.value,
            activeTargetPageIds = pageManagementDelegate.activeTargetPageIds.value
        )
    }


    fun loadProposalFromCache(bookId: String): BookRestructureProposal? {
        return aiRestructureDelegate.loadProposalFromCache(bookId)
    }

    val isAiRestructureLoading: StateFlow<Boolean> = aiRestructureDelegate.isAiRestructureLoading



    fun clearAiRestructureProposal() {
        val bookId = activeBookId.value ?: return
        aiRestructureDelegate.deleteRestructureCache(viewModelScope, bookId)
    }



    fun generateAiHierarchyProposal(feedback: String? = null) {
        val bookId = activeBookId.value ?: return
        aiRestructureDelegate.generateAiHierarchyProposal(viewModelScope, bookId, pageManagementDelegate, feedback)
    }

    fun updateHierarchyManualEdit(updatedProposal: com.andreas_kratzer.ghosttalk.core.model.BookHierarchyProposal) {
        aiRestructureDelegate.setAiHierarchyProposal(updatedProposal)
    }

    fun loadPageLayoutProposal(pageName: String) {
        val bookId = activeBookId.value ?: return
        aiRestructureDelegate.loadPageLayoutProposal(viewModelScope, bookId, pageManagementDelegate, pageName)
    }

    fun loadAllPageLayoutProposals(onComplete: () -> Unit) {
        val hierarchy = aiHierarchyProposal.value ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val missingPages = hierarchy.pages.filter { !aiPageLayoutProposals.value.containsKey(it.name) }
            for (node in missingPages) {
                loadPageLayoutProposal(node.name)
            }
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    fun applyHierarchyProposal(onResult: (String) -> Unit) {
        val currentBookId = activeBookId.value ?: return
        aiRestructureDelegate.applyHierarchyProposal(
            scope = viewModelScope,
            currentBookId = currentBookId,
            pageManagementDelegate = pageManagementDelegate,
            setActiveBookId = ::setActiveBookId,
            loadPage = ::loadPage,
            onResult = onResult
        )
    }


    // --- Layout- & Struktur-Assistent Actions ---

    fun reorderByClickStats(pageId: String, onComplete: () -> Unit = {}) {
        layoutWizardDelegate.reorderByClickStats(pageId, onComplete)
    }

    fun insertHomeNavigationEveryX(pageId: String, x: Int, onComplete: () -> Unit = {}) {
        layoutWizardDelegate.insertHomeNavigationEveryX(pageId, x, onComplete)
    }

    fun shrinkGridToMinimum(pageId: String, onComplete: () -> Unit = {}) {
        layoutWizardDelegate.shrinkGridToMinimum(pageId, onComplete)
    }

    fun deleteDeactivatedButtons(pageId: String, onComplete: () -> Unit = {}) {
        layoutWizardDelegate.deleteDeactivatedButtons(pageId, onComplete)
    }

    val magicCleanupProgress: StateFlow<String?> = layoutWizardDelegate.magicCleanupProgress

    fun magicCleanup(pageId: String, onComplete: () -> Unit = {}) {
        layoutWizardDelegate.magicCleanup(pageId, onComplete)
    }
}

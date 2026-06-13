package com.andreas_kratzer.ghosttalk.ui.pages

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.andreas_kratzer.ghosttalk.core.actions.ActionExecutor
import com.andreas_kratzer.ghosttalk.core.ai.domain.GeminiUseCase
import com.andreas_kratzer.ghosttalk.core.ai.domain.UpdateSmartPredictionsUseCase
import com.andreas_kratzer.ghosttalk.core.call.CallState
import com.andreas_kratzer.ghosttalk.core.cloud.PhilipsHueManager
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.domain.pages.CreatePageUseCase
import com.andreas_kratzer.ghosttalk.core.domain.pages.UsageLocation
import com.andreas_kratzer.ghosttalk.core.model.Book
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.scanning.ScanCoordinator
import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
import com.andreas_kratzer.ghosttalk.feature.settings.domain.FeatureGuard
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.InteractionDelegate
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.PageManagementDelegate
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.ScreenManagementDelegate
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.SmartPredictionDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PageViewModel @Inject constructor(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
    private val settingsRepository: SettingsRepository,
    private val bookRepository: com.andreas_kratzer.ghosttalk.core.data.BookRepository,
    private val ttsHelper: TextToSpeechHelper,
    val featureGuard: FeatureGuard,
    private val pageManagementDelegate: PageManagementDelegate,
    internal val interactionDelegate: InteractionDelegate,
    private val screenManagementDelegate: ScreenManagementDelegate,
    smartPredictionDelegate: SmartPredictionDelegate,
    private val callManagementDelegate: com.andreas_kratzer.ghosttalk.ui.pages.delegates.CallManagementDelegate,
    private val navigationDelegate: com.andreas_kratzer.ghosttalk.ui.pages.delegates.NavigationDelegate,
    private val analyticsDelegate: com.andreas_kratzer.ghosttalk.ui.pages.delegates.AnalyticsDelegate,
    private val smartIntegrationDelegate: com.andreas_kratzer.ghosttalk.ui.pages.delegates.SmartIntegrationDelegate,
    private val suggestionsDelegate: com.andreas_kratzer.ghosttalk.ui.pages.delegates.SuggestionsDelegate,
    private val ttsPreviewDelegate: com.andreas_kratzer.ghosttalk.ui.pages.delegates.TtsPreviewDelegate,
    private val buttonTemplateDelegate: com.andreas_kratzer.ghosttalk.ui.pages.delegates.ButtonTemplateDelegate,
    private val pageResolutionDelegate: com.andreas_kratzer.ghosttalk.ui.pages.delegates.PageResolutionDelegate,
    updateSmartPredictionsUseCase: UpdateSmartPredictionsUseCase,
    private val actionExecutor: ActionExecutor,
    private val scanCoordinator: ScanCoordinator,
    private val geminiUseCase: GeminiUseCase,
    private val philipsHueManager: PhilipsHueManager,
    private val createPageUseCase: CreatePageUseCase,
    private val buttonUsageRepository: com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository
) : AndroidViewModel(application) {



    fun isButtonVisible(buttonConfig: ButtonConfig?): Boolean = buttonConfig == null || featureGuard.isButtonVisible(buttonConfig)

    val pageSortOrderFlow: Flow<String> = settingsRepository.pageSortOrderFlow
    var pageSortOrder: String
        get() = settingsRepository.pageSortOrder
        set(value) {
            settingsRepository.pageSortOrder = value
        }

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

    val buttonHistory = analyticsDelegate.buttonHistory

    val isCalculatingRecommendations = analyticsDelegate.isCalculatingRecommendations

    val shortcutRecommendations = analyticsDelegate.shortcutRecommendations

    fun applyShortcutRecommendation(
        recommendation: com.andreas_kratzer.ghosttalk.core.data.impl.analytics.PathAnalyzer.ShortcutRecommendation,
        onResult: (Boolean, String) -> Unit
    ) {
        analyticsDelegate.applyShortcutRecommendation(recommendation, onResult)
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
    val isGeminiEnabled get() = settingsRepository.isGeminiEnabled
    val defaultStartPageIdFlow = settingsRepository.defaultStartPageIdFlow

    val spotifyUserDisplayName = smartIntegrationDelegate.spotifyUserDisplayName
    val spotifyPlaylists = smartIntegrationDelegate.spotifyPlaylists
    val isLoadingPlaylists = smartIntegrationDelegate.isLoadingPlaylists

    val staticRowPage: StateFlow<Page?> = pageResolutionDelegate.getStaticRowPage(
        scope = viewModelScope,
        activeBookId = activeBookId,
        allPages = allPages,
        smartPredictions = smartPredictions
    )

    val isEditPreviewActive = pageResolutionDelegate.isEditPreviewActive

    fun toggleEditPreviewActive() {
        pageResolutionDelegate.toggleEditPreviewActive()
        Log.d("PageViewModel", "toggleEditPreviewActive: active = ${isEditPreviewActive.value}")
    }

    private val isPreviewOrUserMode = pageResolutionDelegate.getIsPreviewOrUserMode(
        scope = viewModelScope,
        isUserModeActive = isUserModeActive
    )

    val resolvedPage: StateFlow<Page?> = pageResolutionDelegate.getResolvedPage(
        scope = viewModelScope,
        currentPage = currentPage,
        isPreviewOrUserMode = isPreviewOrUserMode,
        smartPredictions = smartPredictions,
        activeBookId = activeBookId,
        unfilteredPages = unfilteredPages,
        isPredictionTimedOut = scanCoordinator.isPredictionTimedOut
    )


    // --- Caregiver Visual Analytics Overlay States ---
    val isAnalyticsOverlayEnabled = analyticsDelegate.isAnalyticsOverlayEnabled

    fun toggleAnalyticsOverlay() {
        analyticsDelegate.toggleAnalyticsOverlay()
    }

    val pageMetrics = analyticsDelegate.pageMetrics

    suspend fun getMarkovSuccessors(buttonId: String): List<Pair<String, Int>> {
        val bookId = activeBookId.value ?: return emptyList()
        return buttonUsageRepository.getMarkovSuccessors(bookId, buttonId)
    }

    val focusedButtonIndex = scanCoordinator.focusedButtonIndex
    val focusedRowIndex = scanCoordinator.focusedRowIndex
    val isScanning = scanCoordinator.isScanning



    fun loadStartPage() {
        navigationDelegate.loadStartPage()
    }



    val activeBook: StateFlow<Book?> = activeBookId.flatMapLatest { id ->
        if (id != null) bookRepository.getBookByIdFlow(id) else flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        analyticsDelegate.init(viewModelScope, resolvedPage)
        smartIntegrationDelegate.init(viewModelScope)
        suggestionsDelegate.init(viewModelScope)
        ttsPreviewDelegate.init(viewModelScope)

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

        smartIntegrationDelegate.loadSpotifyPlaylists()
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
                        callManagementDelegate.startCallScanning(this)
                    }
                    CallState.DIALING,
                    CallState.ACTIVE -> {
                        scanCoordinator.stopScanning()
                        ttsHelper.stopAll()
                        actionExecutor.stopActions()
                        callManagementDelegate.stopCallScanning()
                        callManagementDelegate.resetHangUpState()
                    }
                    CallState.NONE -> {
                        callManagementDelegate.stopCallScanning()
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

        // Observe resolvedPage and trigger rollback if it's completely empty in user mode
        viewModelScope.launch {
            combine(
                resolvedPage,
                isUserModeActive,
                scanCoordinator.isPredictionTimedOut,
                isSmartPredictionLoading,
                smartPredictions,
                staticRowPage
            ) { array ->
                val page = array[0] as? Page
                val userMode = array[1] as Boolean
                val timedOut = array[2] as Boolean
                val isPredictionLoading = array[3] as Boolean
                @Suppress("UNCHECKED_CAST")
                val predictions = array[4] as? List<String>
                val staticPage = array[5] as? Page

                if (userMode && page != null) {
                    val hasPredictions = page.buttonConfigs.any { it != null && it.isActive && it.buttonAction is com.andreas_kratzer.ghosttalk.core.model.SmartPredictionButtonAction }
                    val isWaiting = if (hasPredictions) {
                        !timedOut && (isPredictionLoading || predictions == null)
                    } else {
                        false
                    }
                    if (!isWaiting) {
                        val hasMainButtons = page.buttonConfigs.any { config ->
                            config != null && config.isActive
                        }
                        val hasStaticButtons = staticPage?.buttonConfigs?.any { config ->
                            config != null && config.isActive
                        } ?: false
                        
                        !hasMainButtons && !hasStaticButtons
                    } else {
                        false
                    }
                } else {
                    false
                }
            }.collect { shouldRollback ->
                if (shouldRollback) {
                    triggerEmptyPageRollback()
                }
            }
        }
    }


    val actionLogUseCase = interactionDelegate.actionLogUseCase

    fun createNewPage(name: String, rows: Int, columns: Int, bookId: String, templateId: String?, onComplete: (String) -> Unit) {
        pageManagementDelegate.createNewPage(name, rows, columns, bookId, templateId, onComplete)
    }

    suspend fun getPageById(id: String): Page? {
        return pageManagementDelegate.getPageById(id)
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
        interactionDelegate.activateFocusedButton(resolvedPage.value, activeBookId.value, staticRowPage = staticRowPage.value)
    }
    fun clearActionLogs() = interactionDelegate.clearActionLogs()

    fun resumeScanningIfEnabled() = scanCoordinator.resumeScanningIfEnabled()
    fun startScanning(startIndex: Int = 0) = scanCoordinator.startScanning(startIndex)
    fun stopScanning() = scanCoordinator.stopScanning()
    @Suppress("unused")
    fun restartScanning() = scanCoordinator.restartScanning()

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

    override fun onCleared() {
        super.onCleared()
        actionExecutor.stopActions()
        scanCoordinator.clear()
    }



    private var isRollingBack = false

    private fun triggerEmptyPageRollback() {
        if (isRollingBack) return
        isRollingBack = true
        viewModelScope.launch {
            Log.w("PageViewModel", "Empty page detected. Speaking TTS and navigating back.")
            val msg = "Es ist ein Fehler bei der Berechnung der dynamischen Tasten aufgetreten. Es wird zur vorherigen Seite zurückgekehrt."
            ttsHelper.speak(msg)
            kotlinx.coroutines.delay(3000)
            navigateBack()
            isRollingBack = false
        }
    }
}

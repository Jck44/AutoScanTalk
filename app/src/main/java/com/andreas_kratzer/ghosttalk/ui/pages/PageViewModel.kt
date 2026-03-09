package com.andreas_kratzer.ghosttalk.ui.pages

import android.app.Application
import android.content.Intent
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andreas_kratzer.ghosttalk.core.actions.ActionExecutor
import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import com.andreas_kratzer.ghosttalk.core.pages.PageImportExportManager
import com.andreas_kratzer.ghosttalk.core.scanning.ScannerEngine
import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.domain.actions.ResolveDynamicButtonsUseCase
import com.andreas_kratzer.ghosttalk.domain.actions.UpdateSmartPredictionsUseCase
import com.andreas_kratzer.ghosttalk.domain.genai.GeminiUseCase
import com.andreas_kratzer.ghosttalk.domain.genai.GeminiUseCaseFactory
import com.andreas_kratzer.ghosttalk.domain.settings.CheckForPredictorUseCase
import com.andreas_kratzer.ghosttalk.domain.settings.FeatureGuard
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PageViewModel @Inject constructor(
    application: Application,
    val settingsRepository: SettingsRepository,
    internal val importExportManager: PageImportExportManager,
    internal val scannerEngine: ScannerEngine,
    private val googleAuthManager: GoogleAuthManager,
    geminiUseCaseFactory: GeminiUseCaseFactory,
    private val ttsHelper: TextToSpeechHelper,
    private val localIntentRouter: com.andreas_kratzer.ghosttalk.domain.executors.LocalIntentRouter,
    private val weatherExecutor: com.andreas_kratzer.ghosttalk.domain.executors.WeatherExecutor,
    private val logger: Logger,
    val buttonUsageRepository: com.andreas_kratzer.ghosttalk.data.ButtonUsageRepository,
    val featureGuard: FeatureGuard,
    val pageManagementDelegate: PageManagementDelegate,
    val interactionDelegate: InteractionDelegate,
    val screenManagementDelegate: ScreenManagementDelegate,
    smartPredictionDelegate: SmartPredictionDelegate,
    private val resolveDynamicButtonsUseCase: ResolveDynamicButtonsUseCase,
    private val updateSmartPredictionsUseCase: UpdateSmartPredictionsUseCase,
    private val checkForPredictorUseCase: CheckForPredictorUseCase
) : AndroidViewModel(application), com.andreas_kratzer.ghosttalk.ui.util.GridEditorActions {

    private var geminiUseCase: GeminiUseCase? = null

    val activeBookId = pageManagementDelegate.activeBookId
    val searchQuery = pageManagementDelegate.searchQuery
    val filteredPages = pageManagementDelegate.filteredPages
    val unfilteredPages = pageManagementDelegate.unfilteredPages
    val currentPage = pageManagementDelegate.currentPage
    val templates = pageManagementDelegate.templates

    val lastActions = interactionDelegate.lastActions
    val authRecoverIntent = interactionDelegate.authRecoverIntent
    val isUserModeActive = interactionDelegate.isUserModeActive
    val screenState get() = screenManagementDelegate.screenState

    private val _smartPredictions = MutableStateFlow<List<String>?>(null)
    val smartPredictions: StateFlow<List<String>?> = _smartPredictions.asStateFlow()
    
    val isSmartPredictionLoading: StateFlow<Boolean> = updateSmartPredictionsUseCase.isLoading

    val defaultScanPattern = settingsRepository.defaultScanPatternFlow
    val showTestButtons = settingsRepository.showTestButtonsFlow

    val resolvedPage: StateFlow<Page?> = combine(
        currentPage,
        isUserModeActive,
        smartPredictions,
        activeBookId,
        unfilteredPages
    ) { page, isUserMode, predictions, bookId, allPages ->
        if (page != null && isUserMode && bookId != null) {
            resolveDynamicButtonsUseCase.execute(page, bookId, predictions, allPages)
        } else {
            page
        }
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val actionExecutor = ActionExecutor(
        application = application,
        scope = viewModelScope,
        settingsRepository = settingsRepository,
        logger = logger,
        ttsHelper = ttsHelper,
        geminiUseCase = null,
        localIntentRouter = localIntentRouter,
        weatherExecutor = weatherExecutor,
        buttonUsageRepository = buttonUsageRepository
    )

    private val scanCoordinator = ScanCoordinator(
        scope = viewModelScope,
        scannerEngine = scannerEngine,
        settingsRepository = settingsRepository,
        actionExecutor = actionExecutor,
        currentPage = currentPage,
        isUserModeActive = isUserModeActive,
        resolvedPage = resolvedPage,
        isSmartPredictionLoading = isSmartPredictionLoading,
        checkForPredictorUseCase = checkForPredictorUseCase,
        ttsHelper = ttsHelper,
        smartPredictions = smartPredictions
    )

    val focusedButtonIndex = scanCoordinator.focusedButtonIndex
    val focusedRowIndex = scanCoordinator.focusedRowIndex

    init {
        pageManagementDelegate.init(viewModelScope)
        interactionDelegate.init(viewModelScope, actionExecutor, ::loadPage, _smartPredictions)
        interactionDelegate.scanCoordinator = scanCoordinator
        screenManagementDelegate.init(viewModelScope, isUserModeActive)
        
        smartPredictionDelegate.init(
            scope = viewModelScope,
            currentPage = currentPage,
            allPages = pageManagementDelegate.allPagesFlow,
            lastActions = lastActions,
            activeBookId = activeBookId,
            isUserModeActive = isUserModeActive,
            onPredictionsUpdated = { _smartPredictions.value = it }
        )

        geminiUseCase = geminiUseCaseFactory.create {
            googleAuthManager.getGoogleCredential()?.getToken()
        }
        actionExecutor.geminiUseCase = geminiUseCase
        actionExecutor.ttsHelper = ttsHelper

        scanCoordinator.init()

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

    fun updateSearchQuery(query: String) = pageManagementDelegate.updateSearchQuery(query)
    fun setActiveBookId(bookId: String?) = pageManagementDelegate.setActiveBookId(bookId)
    fun loadPage(page: Page) {
        viewModelScope.launch {
            val isSamePage = currentPage.value?.id == page.id
            val redoPrediction = settingsRepository.geminiRedoPrediction
            scanCoordinator.onPageChanged(isSamePage)
            if (!isSamePage || redoPrediction) {
                _smartPredictions.value = null // Clear to null to indicate "waiting for results"
            }
            pageManagementDelegate.setCurrentPage(page)
            
            // Background pre-fetch for weather if current page has a weather button
            val hasWeatherButton = page.buttonConfigs.any { config ->
                val action = config?.buttonAction
                action is com.andreas_kratzer.ghosttalk.model.GeminiNanoButtonAction && 
                        action.intent == "gemini_nano_intent_weather"
            }
            if (hasWeatherButton) {
                viewModelScope.launch {
                    try {
                        weatherExecutor.getWeatherInfo() // This will refresh if expired
                    } catch (e: Exception) {
                        logger.e("PageViewModel", "Weather pre-fetch failed", e)
                    }
                }
            }
        }
    }

    fun setUserModeActive(isActive: Boolean) = interactionDelegate.setUserModeActive(isActive)
    fun activateButtonAtIndex(index: Int) = interactionDelegate.activateButtonAtIndex(index, resolvedPage.value, activeBookId.value)
    fun activateFocusedButton() = interactionDelegate.activateFocusedButton(resolvedPage.value, activeBookId.value)
    fun clearActionLogs() = interactionDelegate.clearActionLogs()

    fun resumeScanningIfEnabled() = scanCoordinator.resumeScanningIfEnabled()
    fun startScanning(startIndex: Int = 0) = scanCoordinator.startScanning(startIndex)
    fun stopScanning() = scanCoordinator.stopScanning()

    override fun updateButtonConfig(itemId: String, index: Int, newConfig: ButtonConfig?) {
        pageManagementDelegate.updateButtonConfig(itemId, index, newConfig)
    }

    override fun updateGridSettings(
        itemId: String,
        newName: String,
        newScanPattern: String?,
        newRowNames: List<String>,
        newRows: Int?,
        newColumns: Int?
    ) {
        updatePageSettings(itemId, newName, newScanPattern, newRowNames, newRows, newColumns)
    }

    override val isExecuting: StateFlow<Boolean> = actionExecutor.isExecuting

    override fun executeButtonAction(config: ButtonConfig) {
        actionExecutor.executeButtonAction(config)
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
        newName: String, 
        newScanPattern: String?, 
        newRowNames: List<String>,
        newRows: Int? = null,
        newColumns: Int? = null
    ) = pageManagementDelegate.updatePageSettings(pageId, newName, newScanPattern, newRowNames, newRows, newColumns)

    override fun updateRowName(itemId: String, rowIndex: Int, newName: String) {
        pageManagementDelegate.updateRowName(itemId, rowIndex, newName)
    }

    override fun moveRow(itemId: String, fromRow: Int, toRow: Int) {
        pageManagementDelegate.moveRow(itemId, fromRow, toRow)
    }

    override fun moveButton(itemId: String, fromIndex: Int, toIndex: Int) {
        pageManagementDelegate.moveButton(itemId, fromIndex, toIndex)
    }

    fun deletePage(page: Page, deleteUsages: Boolean = false) = pageManagementDelegate.deletePage(page, deleteUsages)
    suspend fun getPageUsages(pageId: String) = pageManagementDelegate.getPageUsages(pageId)
    fun importFromJson(jsonString: String, bookId: String, onSuccess: () -> Unit, onError: (String) -> Unit) =
        pageManagementDelegate.importFromJson(jsonString, bookId, onSuccess, onError)

    suspend fun exportToJson(): String = pageManagementDelegate.exportToJson()

    override fun onCleared() {
        super.onCleared()
        scanCoordinator.clear()
    }
}

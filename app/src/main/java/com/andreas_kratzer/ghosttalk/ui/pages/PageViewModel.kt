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
import com.andreas_kratzer.ghosttalk.domain.FeatureGuard
import com.andreas_kratzer.ghosttalk.domain.GeminiUseCase
import com.andreas_kratzer.ghosttalk.domain.GeminiUseCaseFactory
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.InteractionDelegate
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.PageManagementDelegate
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.SmartPredictionDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val logger: Logger,
    val featureGuard: FeatureGuard,
    val pageManagementDelegate: PageManagementDelegate,
    val interactionDelegate: InteractionDelegate,
    val smartPredictionDelegate: SmartPredictionDelegate
) : AndroidViewModel(application) {

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

    private val _smartPredictions = MutableStateFlow<List<String>>(emptyList())
    val smartPredictions: StateFlow<List<String>> = _smartPredictions.asStateFlow()

    val defaultScanPattern = settingsRepository.defaultScanPatternFlow
    val showTestButtons = settingsRepository.showTestButtonsFlow
    val experimentalManualSorting = settingsRepository.experimentalManualSortingFlow

    val actionExecutor = ActionExecutor(
        scope = viewModelScope,
        settingsRepository = settingsRepository,
        logger = logger,
        ttsHelper = ttsHelper,
        geminiUseCase = null,
        localIntentRouter = localIntentRouter,
        buttonUsageRepository = null // Will be handled inside ActionExecutor if needed, or pass it if you have it
    )

    private val scanCoordinator = ScanCoordinator(
        scope = viewModelScope,
        scannerEngine = scannerEngine,
        settingsRepository = settingsRepository,
        actionExecutor = actionExecutor,
        currentPage = currentPage,
        isUserModeActive = isUserModeActive
    )

    val focusedButtonIndex = scanCoordinator.focusedButtonIndex
    val focusedRowIndex = scanCoordinator.focusedRowIndex

    init {
        pageManagementDelegate.init(viewModelScope)
        interactionDelegate.init(viewModelScope, actionExecutor, ::loadPage, _smartPredictions)
        interactionDelegate.scanCoordinator = scanCoordinator
        
        smartPredictionDelegate.init(
            scope = viewModelScope,
            currentPage = currentPage,
            allPages = pageManagementDelegate.allPagesFlow,
            lastActions = lastActions,
            activeBookId = activeBookId,
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
            scanCoordinator.onPageChanged(isSamePage)
            pageManagementDelegate.setCurrentPage(page)
        }
    }

    fun setUserModeActive(isActive: Boolean) = interactionDelegate.setUserModeActive(isActive)
    fun activateButtonAtIndex(index: Int) = interactionDelegate.activateButtonAtIndex(index, currentPage.value, activeBookId.value)
    fun activateFocusedButton() = interactionDelegate.activateFocusedButton(currentPage.value, activeBookId.value)
    fun clearActionLogs() = interactionDelegate.clearActionLogs()

    fun resumeScanningIfEnabled() = scanCoordinator.resumeScanningIfEnabled()
    fun startScanning(startIndex: Int = 0) = scanCoordinator.startScanning(startIndex)
    fun stopScanning() = scanCoordinator.stopScanning()

    fun createNewPage(name: String, rows: Int, columns: Int, bookId: String, templateId: String? = null, onCreated: (String) -> Unit) =
        pageManagementDelegate.createNewPage(name, rows, columns, bookId, templateId, onCreated)

    fun updateButtonConfig(pageId: String, index: Int, newConfig: ButtonConfig?) =
        pageManagementDelegate.updateButtonConfig(pageId, index, newConfig)

    fun updatePageSettings(pageId: String, newName: String, newScanPattern: String?, newRowNames: List<String>) =
        pageManagementDelegate.updatePageSettings(pageId, newName, newScanPattern, newRowNames)

    fun updateRowName(pageId: String, rowIndex: Int, newName: String) =
        pageManagementDelegate.updateRowName(pageId, rowIndex, newName)

    fun deletePage(page: Page) = pageManagementDelegate.deletePage(page)
    fun reorderPages(fromIndex: Int, toIndex: Int) = pageManagementDelegate.reorderPages(fromIndex, toIndex)
    fun importFromJson(jsonString: String, bookId: String, onSuccess: () -> Unit, onError: (String) -> Unit) =
        pageManagementDelegate.importFromJson(jsonString, bookId, onSuccess, onError)

    suspend fun exportToJson(): String = pageManagementDelegate.exportToJson()

    override fun onCleared() {
        super.onCleared()
        ttsHelper.shutdown()
        scanCoordinator.clear()
    }
}

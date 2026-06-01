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
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.impl.PageImportExportManager
import com.andreas_kratzer.ghosttalk.core.model.Book
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.GridSettingsUpdate
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.ButtonTemplate
import com.andreas_kratzer.ghosttalk.core.data.ButtonTemplateRepository
import com.andreas_kratzer.ghosttalk.core.scanning.ScanCoordinator
import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.andreas_kratzer.ghosttalk.domain.actions.ResolveDynamicButtonsUseCase
import com.andreas_kratzer.ghosttalk.core.domain.pages.UsageLocation
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
import com.andreas_kratzer.ghosttalk.core.cloud.PhilipsHueManager
import com.andreas_kratzer.ghosttalk.core.cloud.SpotifyManager
import com.andreas_kratzer.ghosttalk.core.cloud.SpotifyPlaylist
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PageViewModel @Inject constructor(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
    val settingsRepository: SettingsRepository,
    private val bookRepository: com.andreas_kratzer.ghosttalk.core.data.BookRepository,
    internal val importExportManager: PageImportExportManager,
    private val ttsHelper: TextToSpeechHelper,
    private val logger: Logger,
    private val weatherExecutor: com.andreas_kratzer.ghosttalk.domain.executors.WeatherExecutor,
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
    private val spotifyManager: SpotifyManager
) : AndroidViewModel(application), com.andreas_kratzer.ghosttalk.ui.util.GridEditorActions {

    val buttonTemplates: StateFlow<List<ButtonTemplate>> = buttonTemplateRepository.getTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    val activeBookId = pageManagementDelegate.activeBookId
    val currentPageId = pageManagementDelegate.currentPageId
    val searchQuery = pageManagementDelegate.searchQuery
    val filteredPages = pageManagementDelegate.filteredPages
    val unfilteredPages = pageManagementDelegate.unfilteredPages
    val currentPage = pageManagementDelegate.currentPage
    val templates = pageManagementDelegate.templates
    val activeTargetPageIds = pageManagementDelegate.activeTargetPageIds

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
    .distinctUntilChanged()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), currentPage.value)

    val focusedButtonIndex = scanCoordinator.focusedButtonIndex
    val focusedRowIndex = scanCoordinator.focusedRowIndex
    val isStoppedDueToLimit = scanCoordinator.isStoppedDueToLimit
    val isScanning = scanCoordinator.isScanning

    // --- Telephony Call States ---
    val callState = systemCallManager.callState
    val callerName = systemCallManager.callerName
    val callerPhone = systemCallManager.callerPhone
    val callDurationSeconds = systemCallManager.callDurationSeconds
    val isOutgoing = systemCallManager.isOutgoing
    val isSimulatedCall = systemCallManager.isSimulatedFlow
    val isHangUpButtonFocused = MutableStateFlow(false)
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
        val allPages = pageManagementDelegate.allPagesFlow.value
        val startId = settingsRepository.defaultStartPageId ?: allPages.firstOrNull()?.id
        val startPage = allPages.find { it.id == startId }
        if (startPage != null) {
            loadPage(startPage)
        }
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
        interactionDelegate.init(viewModelScope, actionExecutor, ::loadPage, _smartPredictions, activeBookId)
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

        scanCoordinator.init(
            currentPage = currentPage,
            isUserModeActive = isUserModeActive,
            resolvedPage = resolvedPage,
            isSmartPredictionLoading = isSmartPredictionLoading,
            smartPredictions = smartPredictions
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
                    }
                    com.andreas_kratzer.ghosttalk.core.call.CallState.NONE -> {
                        stopCallScanning()
                        isHangUpButtonFocused.value = false
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
    fun setActiveBookId(bookId: String?) = pageManagementDelegate.setActiveBookId(bookId)
    fun loadPage(page: Page) {
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
            pageManagementDelegate.setCurrentPage(page)
            savedStateHandle["currentPageId"] = page.id
            savedStateHandle["focusedButtonIndex"] = focusedButtonIndex.value
            savedStateHandle["focusedRowIndex"] = focusedRowIndex.value
        }
    }

    override val availableGeminiTools = geminiUseCase.getAvailableTools()

    fun setUserModeActive(isActive: Boolean) {
        interactionDelegate.setUserModeActive(isActive)
        savedStateHandle["isUserModeActive"] = isActive
    }
    fun activateButtonAtIndex(index: Int) = interactionDelegate.activateButtonAtIndex(index, resolvedPage.value, activeBookId.value)
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
            if (isHangUpButtonFocused.value) {
                systemCallManager.hangUp()
            } else {
                isHangUpButtonFocused.value = true
                val cueDevice = settingsRepository.cuesAudioDeviceAddress
                val text = getApplication<Application>().getString(com.andreas_kratzer.ghosttalk.R.string.call_hang_up)
                ttsHelper.speakRouted(text, cueDevice, isForCues = true)
            }
            return
        }

        interactionDelegate.activateFocusedButton(resolvedPage.value, activeBookId.value)
    }
    fun clearActionLogs() = interactionDelegate.clearActionLogs()

    fun resumeScanningIfEnabled() = scanCoordinator.resumeScanningIfEnabled()
    fun startScanning(startIndex: Int = 0) = scanCoordinator.startScanning(startIndex)
    fun stopScanning() = scanCoordinator.stopScanning()
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
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(authUrl)).apply {
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
}

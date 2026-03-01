package com.example.gostalk.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gostalk.model.AuditoryCue
import com.example.gostalk.model.NavigateToPageButtonAction
import com.example.gostalk.model.Page
import com.example.gostalk.model.SpeakTextButtonAction
import com.example.gostalk.tts.TextToSpeechHelper
import com.example.gostalk.model.ButtonConfig
import com.google.gson.Gson
import com.example.gostalk.core.ScannerEngine
import com.example.gostalk.core.ActionExecutor
import com.example.gostalk.model.importexport.ImportExportData
import com.example.gostalk.core.PageImportExportManager
import com.example.gostalk.data.SettingsRepository
import com.example.gostalk.data.PageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import com.example.gostalk.core.util.Logger
import com.example.gostalk.core.util.AppLogger
import com.example.gostalk.core.util.TestLogger

import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class PageViewModel(
    application: Application,
    private val pageRepository: PageRepository,
    private val settingsRepository: SettingsRepository,
    private var ttsHelper: TextToSpeechHelper? = null,
    private val logger: Logger = AppLogger,
    private val importExportManager: PageImportExportManager = PageImportExportManager(pageRepository, logger)
) : AndroidViewModel(application) {

    private val _activeBookId = MutableStateFlow<String?>(null)
    val activeBookId: StateFlow<String?> = _activeBookId.asStateFlow()

    private val _allPages = MutableStateFlow<List<Page>>(emptyList())
    val allPages: StateFlow<List<Page>> = _allPages.asStateFlow()

    private val _currentPage = MutableStateFlow<Page?>(null)
    val currentPage: StateFlow<Page?> = _currentPage.asStateFlow()

    private val _lastActions = MutableStateFlow<List<String>>(emptyList())
    val lastActions: StateFlow<List<String>> = _lastActions.asStateFlow()

    val scannerEngine = ScannerEngine(viewModelScope, settingsRepository, ttsHelper, logger)
    val focusedButtonIndex: StateFlow<Int?> = scannerEngine.focusedButtonIndex
    val focusedRowIndex: StateFlow<Int?> = scannerEngine.focusedRowIndex
    val defaultScanPattern: StateFlow<String> = settingsRepository.defaultScanPatternFlow

    val actionExecutor = ActionExecutor(
        scope = viewModelScope,
        pageRepository = pageRepository,
        settingsRepository = settingsRepository,
        ttsHelper = ttsHelper,
        onLoadPage = { page -> loadPage(page) },
        onPauseScanning = { stopScanningTemporarily() },
        onResumeScanning = { resumeScanningIfEnabled() },
        onLogAction = { actionText -> logAction(actionText) }
    )

    fun setActiveBookId(bookId: String?) {
        _activeBookId.value = bookId
    }

    init {
        if (ttsHelper == null) {
            ttsHelper = TextToSpeechHelper(application.applicationContext)
        }
        // Ensure engines get the initialized TTS instance
        scannerEngine.ttsHelper = ttsHelper
        actionExecutor.ttsHelper = ttsHelper

        // Settings live überwachen
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                settingsRepository.ttsLanguageFlow,
                settingsRepository.ttsVoiceNameFlow
            ) { lang, voice ->
                Pair(lang, voice)
            }.collect { (newLanguage, newVoice) ->
                ttsHelper?.setLanguageAndVoice(newLanguage, newVoice)
            }
        }

        viewModelScope.launch {
            settingsRepository.scanDelayFlow.collect { delay ->
                setScanDelay(delay)
            }
        }
        
        viewModelScope.launch {
            _activeBookId.flatMapLatest { bookId ->
                if (bookId != null) {
                    pageRepository.getPagesForBookFlow(bookId)
                } else {
                    flowOf(emptyList()) // No book selected, no pages
                }
            }.collect { pages ->
                _allPages.value = pages
            }
        }

        // Restore action logs if persistence is enabled
        if (settingsRepository.persistActionLogs) {
            val savedJson = settingsRepository.actionLogsStorage
            if (!savedJson.isNullOrBlank()) {
                try {
                    val gson = Gson()
                    val savedList = gson.fromJson(savedJson, Array<String>::class.java).toList()
                    _lastActions.value = savedList
                } catch (e: Exception) {
                    logger.e("PageViewModel", "Error parsing stored action logs", e)
                }
            }
        }
    }

    fun loadPage(page: Page) {
        _currentPage.value = page
        scannerEngine.stopScanning()
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
        
        // When user directly activates, temporarily stop it so the action executor 
        // can handle resuming it based on settings after the action finishes
        stopScanningTemporarily()
        scannerEngine.setFocusedIndex(index)

        actionExecutor.executeButtonAction(buttonConfig)
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
        val timeFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        val timeString = timeFormat.format(java.util.Date())
        val entry = "[$timeString] $actionText"

        _lastActions.update { currentActions ->
            val updatedActions = currentActions.toMutableList()
            updatedActions.add(0, entry)
            if (updatedActions.size > 100) {
                updatedActions.removeLast()
            }

            // Save to storage if persistence is enabled
            if (settingsRepository.persistActionLogs) {
                settingsRepository.actionLogsStorage = Gson().toJson(updatedActions)
            }

            updatedActions
        }
    }

    fun clearActionLogs() {
        _lastActions.value = emptyList()
        if (settingsRepository.persistActionLogs) {
            settingsRepository.actionLogsStorage = "[]" // Or null
        }
    }

    fun createNewPage(name: String, rows: Int, columns: Int, bookId: String): String {
        val newPageId = UUID.randomUUID().toString()
        val totalSlots = rows * columns
        val buttonConfigs = MutableList<ButtonConfig?>(totalSlots) { null }

        if (totalSlots > 0) {
            val homePageId = settingsRepository.defaultStartPageId ?: _allPages.value.firstOrNull()?.id
            
            if (homePageId != null) {
                buttonConfigs[totalSlots - 1] = ButtonConfig(
                    id = UUID.randomUUID().toString(),
                    label = "zurück zum Start",
                    spokenText = "Zurück zur Startseite",
                    buttonAction = NavigateToPageButtonAction(
                        pageId = homePageId
                    ),
                    auditoryCue = AuditoryCue.TextToSpeechCue("Zurück zur Startseite")
                )
            }
        }

        val newPage = Page(
            id = newPageId,
            bookId = bookId,
            name = name,
            rows = rows,
            columns = columns,
            buttonConfigs = buttonConfigs
        )
        viewModelScope.launch(Dispatchers.IO) {
            pageRepository.insertPage(newPage)
        }
        return newPageId
    }

    fun updateButtonConfig(pageId: String, index: Int, newConfig: ButtonConfig?) {
        viewModelScope.launch(Dispatchers.IO) {
            val page = pageRepository.getPageById(pageId)
            if (page != null && index in page.buttonConfigs.indices) {
                val updatedConfigs = page.buttonConfigs.toMutableList()
                updatedConfigs[index] = newConfig
                val updatedPage = page.copy(buttonConfigs = updatedConfigs)
                pageRepository.updatePage(updatedPage)
                
                // If it's the currently active page being viewed/edited, refresh the state
                if (_currentPage.value?.id == pageId) {
                    _currentPage.value = updatedPage
                }
            }
        }
    }

    fun updatePageSettings(pageId: String, newName: String, newScanPattern: String?, newRowNames: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
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
        viewModelScope.launch(Dispatchers.IO) {
            val page = pageRepository.getPageById(pageId)
            if (page != null) {
                val updatedNames = page.rowNames.toMutableList()
                // Ensure the list is large enough
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
        viewModelScope.launch(Dispatchers.IO) {
            pageRepository.deletePage(page)
        }
    }

    fun importFromJson(jsonString: String, bookId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val result = importExportManager.importFromJson(jsonString, bookId)
            result.onSuccess {
                onSuccess()
            }.onFailure { e ->
                onError("Fehler beim Import: ${e.message}")
            }
        }
    }

    suspend fun exportToJson(): String {
        return importExportManager.exportToJson(_allPages.value)
    }

    override fun onCleared() {
        super.onCleared()
        ttsHelper?.shutdown()
        scannerEngine.clear()
    }
}

class PageViewModelFactory(
    private val application: Application,
    private val pageRepository: PageRepository,
    private val settingsRepository: SettingsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PageViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            val logger = AppLogger
            val importExportManager = PageImportExportManager(pageRepository, logger)
            return PageViewModel(application, pageRepository, settingsRepository, logger = logger, importExportManager = importExportManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

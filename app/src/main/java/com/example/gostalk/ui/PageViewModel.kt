package com.example.gostalk.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gostalk.model.Page
import com.example.gostalk.tts.TextToSpeechHelper
import com.example.gostalk.model.ButtonConfig
import com.example.gostalk.core.ScannerEngine
import com.example.gostalk.core.ActionExecutor
import com.example.gostalk.core.PageImportExportManager
import com.example.gostalk.data.SettingsRepository
import com.example.gostalk.data.PageRepository
import com.example.gostalk.domain.GetPagesUseCase
import com.example.gostalk.domain.ActionLogUseCase
import com.example.gostalk.domain.CreatePageUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.example.gostalk.core.util.Logger
import com.example.gostalk.core.util.AppLogger
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class PageViewModel(
    application: Application,
    private val pageRepository: PageRepository,
    private val settingsRepository: SettingsRepository,
    private var ttsHelper: TextToSpeechHelper? = null,
    private val logger: Logger = AppLogger,
    private val importExportManager: PageImportExportManager = PageImportExportManager(pageRepository, logger),
    private val getPagesUseCase: GetPagesUseCase = GetPagesUseCase(pageRepository),
    private val actionLogUseCase: ActionLogUseCase = ActionLogUseCase(settingsRepository, logger),
    private val createPageUseCase: CreatePageUseCase = CreatePageUseCase(pageRepository, settingsRepository)
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
        scannerEngine.ttsHelper = ttsHelper
        actionExecutor.ttsHelper = ttsHelper

        // Monitor settings
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                settingsRepository.ttsLanguageFlow,
                settingsRepository.ttsVoiceNameFlow
            ) { lang, voice -> lang to voice }
                .collect { (newLanguage, newVoice) ->
                    ttsHelper?.setLanguageAndVoice(newLanguage, newVoice)
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
        _lastActions.update { current ->
            actionLogUseCase.formatAndAddEntry(actionText, current)
        }
    }

    fun clearActionLogs() {
        _lastActions.value = emptyList()
        actionLogUseCase.clearLogs()
    }

    fun createNewPage(name: String, rows: Int, columns: Int, bookId: String): String {
        val newPageId = java.util.UUID.randomUUID().toString()
        viewModelScope.launch {
            createPageUseCase.execute(name, rows, columns, bookId, _allPages.value)
        }
        return newPageId // Note: In a real app, we might want to wait for the ID or return the flow
    }

    fun updateButtonConfig(pageId: String, index: Int, newConfig: ButtonConfig?) {
        viewModelScope.launch(Dispatchers.IO) {
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
            result.onSuccess { onSuccess() }.onFailure { e -> onError("Fehler beim Import: ${e.message}") }
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
            val manager = PageImportExportManager(pageRepository, logger)
            val getPages = GetPagesUseCase(pageRepository)
            val actionLog = ActionLogUseCase(settingsRepository, logger)
            val createPage = CreatePageUseCase(pageRepository, settingsRepository)
            return PageViewModel(
                application, pageRepository, settingsRepository, 
                logger = logger, 
                importExportManager = manager,
                getPagesUseCase = getPages,
                actionLogUseCase = actionLog,
                createPageUseCase = createPage
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

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
import com.example.gostalk.model.importexport.ImportExportData
import com.google.gson.Gson
import com.example.gostalk.data.SettingsRepository
import com.example.gostalk.data.PageDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class PageViewModel(
    application: Application,
    private val pageDao: PageDao,
    private val settingsRepository: SettingsRepository,
    private var ttsHelper: TextToSpeechHelper? = null
) : AndroidViewModel(application) {

    private val _activeBookId = MutableStateFlow<String?>(null)
    val activeBookId: StateFlow<String?> = _activeBookId.asStateFlow()

    private val _allPages = MutableStateFlow<List<Page>>(emptyList())
    val allPages: StateFlow<List<Page>> = _allPages.asStateFlow()

    private val _currentPage = MutableStateFlow<Page?>(null)
    val currentPage: StateFlow<Page?> = _currentPage.asStateFlow()

    private val _focusedButtonIndex = MutableStateFlow<Int?>(null)
    val focusedButtonIndex: StateFlow<Int?> = _focusedButtonIndex.asStateFlow()

    private val _lastActions = MutableStateFlow<List<String>>(emptyList())
    val lastActions: StateFlow<List<String>> = _lastActions.asStateFlow()

    private var scanJob: Job? = null
    // Standardverzögerung verknüpft mit Memory
    private var scanDelayMillis: Long = settingsRepository.scanDelayMillis

    fun setActiveBookId(bookId: String?) {
        _activeBookId.value = bookId
    }

    init {
        if (ttsHelper == null) {
            ttsHelper = TextToSpeechHelper(application.applicationContext)
        }
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
                    pageDao.getPagesForBookFlow(bookId)
                } else {
                    flowOf(emptyList()) // No book selected, no pages
                }
            }.collect { pages ->
                _allPages.value = pages
            }
        }
    }

    // Initialisierung: Lade eine Startseite, falls vorhanden (z.B. die erste aus dem Repository)
    // Diese Logik muss in MainActivity.kt verschoben oder angepasst werden,
    // da wir die erste Seite jetzt dort explizit laden.
    /*
    init {
        pagesRepository.values.firstOrNull()?.let {
            loadPage(it)
        }
    }
    */

    fun loadPage(page: Page) {
        _currentPage.value = page
        stopScanning()
        _focusedButtonIndex.value = null
    }

    fun resumeScanningIfEnabled() {
        if (settingsRepository.autoStartScanning) {
            startScanning()
        }
    }

    fun setScanDelay(delayMillis: Long) {
        scanDelayMillis = delayMillis
        if (scanJob?.isActive == true) {
            startScanning()
        }
    }

    fun startScanning() {
        scanJob?.cancel()
        val page = _currentPage.value ?: return
        val activeButtonsWithGlobalIndices = page.buttonConfigs
            .mapIndexedNotNull { index, buttonConfig ->
                if (buttonConfig != null) Pair(index, buttonConfig) else null
            }

        if (activeButtonsWithGlobalIndices.isEmpty()) {
            _focusedButtonIndex.value = null
            return
        }

        scanJob = viewModelScope.launch {
            for ((globalIndex, buttonConfig) in activeButtonsWithGlobalIndices) {
                _focusedButtonIndex.value = globalIndex
                val cue = buttonConfig.auditoryCue

                // Wait up to ~2 seconds if TTS is not ready yet for the very first item
                var retries = 0
                while (ttsHelper?.isReady != true && retries < 20) {
                    delay(100)
                    retries++
                }

                if (ttsHelper?.isReady == true) {
                    val cueText = (cue as? AuditoryCue.TextToSpeechCue)?.text?.takeIf { it.isNotBlank() } ?: buttonConfig.label
                    ttsHelper?.speak(cueText)
                }
                delay(scanDelayMillis)
            }
            _focusedButtonIndex.value = null
        }
    }

    fun stopScanning() {
        scanJob?.cancel()
        _focusedButtonIndex.value = null
    }

    fun activateButtonAtIndex(index: Int) {
        val page = _currentPage.value ?: return
        val buttonConfig = page.buttonConfigs.getOrNull(index) ?: return
        
        // When user directly activates, they might want to stop the auto-scanning focus loop
        stopScanning()
        _focusedButtonIndex.value = index

        when (val action = buttonConfig.buttonAction) {
            is SpeakTextButtonAction -> {
                val textToSpeak = buttonConfig.spokenText?.takeIf { it.isNotBlank() } ?: action.textToSpeech
                if (ttsHelper?.isReady == true) {
                    ttsHelper?.speak(textToSpeak)
                    logAction("Gesprochen: \"$textToSpeak\"")
                } else {
                    logAction("Sprechen (TTS nicht bereit): \"$textToSpeak\"")
                }
            }
            is NavigateToPageButtonAction -> {
                val feedback = buttonConfig.spokenText?.takeIf { it.isNotBlank() } ?: action.ttsFeedback
                feedback?.let { fb ->
                    if (ttsHelper?.isReady == true) {
                        ttsHelper?.speak(fb)
                        logAction("Navigations-Feedback: \"$fb\"")
                    } else {
                        logAction("Nav-Feedback (TTS nicht bereit): \"$fb\"")
                    }
                }
                viewModelScope.launch {
                    val nextPage = pageDao.getPageById(action.pageId)
                    if (nextPage != null) {
                        loadPage(nextPage) // Lädt die neue Seite
                        resumeScanningIfEnabled() // Restart scanning for the new page
                        logAction("Navigiert zu Seite: ${nextPage.name} (ID: ${action.pageId})")
                    } else {
                        logAction("Fehler: Seite mit ID '${action.pageId}' nicht gefunden.")
                        if (ttsHelper?.isReady == true) ttsHelper?.speak("Seite nicht gefunden")
                    }
                }
            }
            // Hier könnten weitere Action-Typen behandelt werden
        }
    }

    fun activateFocusedButton() {
        val focusedIdx = _focusedButtonIndex.value ?: return
        activateButtonAtIndex(focusedIdx)
    }

    private fun logAction(actionText: String) {
        _lastActions.update { currentActions ->
            val updatedActions = currentActions.toMutableList()
            updatedActions.add(0, actionText)
            if (updatedActions.size > 5) {
                updatedActions.removeLast()
            }
            updatedActions
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
                    buttonAction = NavigateToPageButtonAction(
                        pageId = homePageId,
                        ttsFeedback = "Zurück zur Startseite"
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
            pageDao.insertPage(newPage)
        }
        return newPageId
    }

    fun updateButtonConfig(pageId: String, index: Int, newConfig: com.example.gostalk.model.ButtonConfig?) {
        viewModelScope.launch(Dispatchers.IO) {
            val page = pageDao.getPageById(pageId)
            if (page != null && index in page.buttonConfigs.indices) {
                val updatedConfigs = page.buttonConfigs.toMutableList()
                updatedConfigs[index] = newConfig
                val updatedPage = page.copy(buttonConfigs = updatedConfigs)
                pageDao.updatePage(updatedPage)
                
                // If it's the currently active page being viewed/edited, refresh the state
                if (_currentPage.value?.id == pageId) {
                    _currentPage.value = updatedPage
                }
            }
        }
    }

    fun updatePageName(pageId: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val page = pageDao.getPageById(pageId)
            if (page != null) {
                val updatedPage = page.copy(name = newName)
                pageDao.updatePage(updatedPage)
                
                if (_currentPage.value?.id == pageId) {
                    _currentPage.value = updatedPage
                }
            }
        }
    }

    fun deletePage(page: Page) {
        viewModelScope.launch(Dispatchers.IO) {
            pageDao.deletePage(page)
        }
    }

    fun importFromJson(jsonString: String, bookId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                android.util.Log.d("GoSTalkImport", "Starting import mapping parsing...")
                val gson = Gson()
                val importData = gson.fromJson(jsonString, ImportExportData::class.java)

                if (importData?.pages == null) {
                    android.util.Log.e("GoSTalkImport", "Parsed JSON was invalid or missing 'pages'")
                    launch(Dispatchers.Main) { onError("Ungültiges JSON-Format. Seiten fehlen.") }
                    return@launch
                }
                
                android.util.Log.d("GoSTalkImport", "Parsed ${importData.pages.size} pages. Committing to Room DB...")

                // 1. Generate new UUIDs for all imported pages to map their relationships
                val pageIdMap = mutableMapOf<String, String>()
                importData.pages.forEach {
                    pageIdMap[it.importId] = UUID.randomUUID().toString()
                }

                // 2. Map pages and buttons
                val newPages = importData.pages.map { importPage ->
                    val pageId = pageIdMap[importPage.importId] ?: UUID.randomUUID().toString()

                    // Create empty grid
                    val buttonConfigs = MutableList<ButtonConfig?>(importPage.rows * importPage.columns) { null }

                    importPage.buttons.forEach { importButton ->
                        if (importButton.index <= Int.MAX_VALUE) {
                            val safeIndex = importButton.index.toInt()
                            if (safeIndex in buttonConfigs.indices) {
                                val auditoryCue = if (!importButton.auditoryCueText.isNullOrBlank()) {
                                    AuditoryCue.TextToSpeechCue(importButton.auditoryCueText)
                                } else null

                            val action = when (importButton.action?.type?.uppercase()) {
                                "NAVIGATE" -> {
                                    val targetId = pageIdMap[importButton.action.targetPageImportId] ?: ""
                                    NavigateToPageButtonAction(targetId, importButton.action.ttsFeedback)
                                }
                                "SPEAK" -> SpeakTextButtonAction(importButton.action.textToSpeech ?: importButton.label)
                                else -> SpeakTextButtonAction(importButton.label) // Fallback
                            }

                            buttonConfigs[safeIndex] = ButtonConfig(
                                id = UUID.randomUUID().toString(),
                                label = importButton.label,
                                auditoryCue = auditoryCue,
                                buttonAction = action
                            )
                        }
                    }
                }

                    Page(
                        id = pageId,
                        bookId = bookId,
                        name = importPage.name,
                        rows = importPage.rows,
                        columns = importPage.columns,
                        buttonConfigs = buttonConfigs
                    )
                }

                // 3. Save to DB
                newPages.forEach { pageDao.insertPage(it) }
                android.util.Log.d("GoSTalkImport", "Successfully committed ${newPages.size} pages to Database")
                
                launch(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                e.printStackTrace()
                android.util.Log.e("GoSTalkImport", "Exception during import: ${e.message}")
                launch(Dispatchers.Main) { onError("Fehler beim Import: ${e.message}") }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        ttsHelper?.shutdown()
        scanJob?.cancel()
    }
}

// ViewModel Factory, um Parameter an den PageViewModel zu übergeben
class PageViewModelFactory(
    private val application: Application,
    private val pageDao: PageDao,
    private val settingsRepository: SettingsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PageViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PageViewModel(application, pageDao, settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

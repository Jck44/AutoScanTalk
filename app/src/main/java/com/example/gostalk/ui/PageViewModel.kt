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
import com.example.gostalk.data.SettingsRepository
import com.example.gostalk.data.PageDao
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PageViewModel(
    application: Application,
    private val pageDao: PageDao,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    private var ttsHelper: TextToSpeechHelper? = null

    init {
        ttsHelper = TextToSpeechHelper(application.applicationContext)
        
        // Settings live überwachen
        viewModelScope.launch {
            settingsRepository.ttsLanguageFlow.collect { newLanguage ->
                ttsHelper?.setLanguage(newLanguage)
            }
        }

        viewModelScope.launch {
            settingsRepository.scanDelayFlow.collect { delay ->
                setScanDelay(delay)
            }
        }
    }

    private val _currentPage = MutableStateFlow<Page?>(null)
    val currentPage: StateFlow<Page?> = _currentPage.asStateFlow()

    private val _focusedButtonIndex = MutableStateFlow<Int?>(null)
    val focusedButtonIndex: StateFlow<Int?> = _focusedButtonIndex.asStateFlow()

    private val _lastActions = MutableStateFlow<List<String>>(emptyList())
    val lastActions: StateFlow<List<String>> = _lastActions.asStateFlow()

    private var scanJob: Job? = null
    // Standardverzögerung verknüpft mit Memory
    private var scanDelayMillis: Long = settingsRepository.scanDelayMillis

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

                if (cue is AuditoryCue.TextToSpeechCue && ttsHelper?.isReady == true) {
                    ttsHelper?.speak(cue.text)
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
                if (ttsHelper?.isReady == true) {
                    ttsHelper?.speak(action.textToSpeech)
                    logAction("Gesprochen: \"${action.textToSpeech}\"")
                } else {
                    logAction("Sprechen (TTS nicht bereit): \"${action.textToSpeech}\"")
                }
            }
            is NavigateToPageButtonAction -> {
                action.ttsFeedback?.let { feedback ->
                    if (ttsHelper?.isReady == true) {
                        ttsHelper?.speak(feedback)
                        logAction("Navigations-Feedback: \"$feedback\"")
                    } else {
                        logAction("Nav-Feedback (TTS nicht bereit): \"$feedback\"")
                    }
                }
                viewModelScope.launch {
                    val nextPage = pageDao.getPageById(action.pageId)
                    if (nextPage != null) {
                        loadPage(nextPage) // Lädt die neue Seite
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

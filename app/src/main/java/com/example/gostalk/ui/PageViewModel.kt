package com.example.gostalk.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gostalk.model.Action
import com.example.gostalk.model.AuditoryCue
// import com.example.gostalk.model.ButtonConfig // Nicht direkt für ButtonConfig benötigt, da es über Page kommt
import com.example.gostalk.model.Page
import com.example.gostalk.tts.TextToSpeechHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PageViewModel(application: Application) : AndroidViewModel(application) {

    private val ttsHelper = TextToSpeechHelper(application.applicationContext) {
        _ttsReady.value = true
    }

    private val _ttsReady = MutableStateFlow(false)
    val ttsReady: StateFlow<Boolean> = _ttsReady.asStateFlow()

    private val _currentPage = MutableStateFlow<Page?>(null)
    val currentPage: StateFlow<Page?> = _currentPage.asStateFlow()

    private val _focusedButtonIndex = MutableStateFlow<Int?>(null) // Globaler Index in page.buttonConfigs
    val focusedButtonIndex: StateFlow<Int?> = _focusedButtonIndex.asStateFlow()

    private val _lastActions = MutableStateFlow<List<String>>(emptyList())
    val lastActions: StateFlow<List<String>> = _lastActions.asStateFlow()

    private var scanJob: Job? = null
    private var scanDelayMillis: Long = 2000 // Standard-Scan-Verzögerung: 2 Sekunden

    fun loadPage(page: Page) {
        _currentPage.value = page
        stopScanning() // Vorhandenen Scan stoppen und Fokus zurücksetzen
        _focusedButtonIndex.value = null // Explizit Fokus für neue Seite zurücksetzen
    }

    fun setScanDelay(delayMillis: Long) {
        scanDelayMillis = delayMillis
        if (scanJob?.isActive == true) { // Wenn gerade gescannt wird, neu starten
            startScanning()
        }
    }

    fun startScanning() {
        scanJob?.cancel()
        val page = _currentPage.value ?: return

        // Erstellt eine Liste von Paaren: (globaler Index, ButtonConfig) für alle nicht-null Buttons
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
                _focusedButtonIndex.value = globalIndex // Setze Fokus auf den globalen Index

                val cue = buttonConfig.auditoryCue
                if (cue is AuditoryCue.TextToSpeechCue && _ttsReady.value) {
                    ttsHelper.speak(cue.text)
                }
                delay(scanDelayMillis)
            }
            // Nach einem vollständigen Durchlauf den Scan beenden und Fokus zurücksetzen
            _focusedButtonIndex.value = null
            // scanJob wird durch die Beendigung der Coroutine implizit als nicht mehr aktiv betrachtet
        }
    }

    fun stopScanning() {
        scanJob?.cancel()
        _focusedButtonIndex.value = null
    }

    fun activateFocusedButton() {
        val page = _currentPage.value ?: return
        val focusedIdx = _focusedButtonIndex.value ?: return // Nichts tun, wenn kein Fokus

        val buttonConfig = page.buttonConfigs.getOrNull(focusedIdx) ?: return

        when (val action = buttonConfig.action) {
            is Action.SpeakTextAction -> {
                if (_ttsReady.value) {
                    ttsHelper.speak(action.textToSpeech) // TODO: Evtl. anderer queueMode für Aktionen
                    logAction("Aktion: ${action.textToSpeech}")
                } else {
                    logAction("Aktion (TTS nicht bereit): ${action.textToSpeech}")
                }
            }
        }
        // Optional: Scan nach Aktion stoppen oder Fokus zurücksetzen, je nach gewünschter UX
        // Fürs Erste behalten wir den Fokus. Der Nutzer kann den Scan bei Bedarf neu starten.
    }

    private fun logAction(actionText: String) {
        _lastActions.update { currentActions ->
            val updatedActions = currentActions.toMutableList()
            // Neue Aktion am Anfang der Liste hinzufügen
            updatedActions.add(0, actionText)
            // Wenn die Liste zu lang wird, die älteste Aktion (am Ende) entfernen
            if (updatedActions.size > 5) {
                updatedActions.removeLast()
            }
            updatedActions
        }
    }

    override fun onCleared() {
        super.onCleared()
        ttsHelper.shutdown()
        scanJob?.cancel()
    }
}

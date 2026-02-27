package com.example.gostalk.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.gostalk.data.SettingsRepository
import com.example.gostalk.tts.TextToSpeechHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class SettingsViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    // Helper für das Abfragen der verfügbaren Sprachen
    private val tempTtsHelper = TextToSpeechHelper(application)

    private val _availableLanguages = MutableStateFlow<List<Locale>>(emptyList())
    val availableLanguages: StateFlow<List<Locale>> = _availableLanguages.asStateFlow()

    private val _selectedLanguageTag = MutableStateFlow("default")
    val selectedLanguageTag: StateFlow<String> = _selectedLanguageTag.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<android.speech.tts.Voice>>(emptyList())
    val availableVoices: StateFlow<List<android.speech.tts.Voice>> = _availableVoices.asStateFlow()

    private val _selectedVoiceName = MutableStateFlow<String?>(null)
    val selectedVoiceName: StateFlow<String?> = _selectedVoiceName.asStateFlow()

    private val _autoStartScanning = MutableStateFlow(true)
    val autoStartScanning: StateFlow<Boolean> = _autoStartScanning.asStateFlow()

    private val _scanDelayInput = MutableStateFlow("1000")
    val scanDelayInput: StateFlow<String> = _scanDelayInput.asStateFlow()

    private val _defaultStartPageId = MutableStateFlow<String?>(null)
    val defaultStartPageId: StateFlow<String?> = _defaultStartPageId.asStateFlow()

    init {
        // Initiale Einstellungen laden
        _selectedLanguageTag.value = settingsRepository.ttsLanguage ?: "default"
        _selectedVoiceName.value = settingsRepository.ttsVoiceName
        _autoStartScanning.value = settingsRepository.autoStartScanning
        _scanDelayInput.value = settingsRepository.scanDelayMillis.toString()
        _defaultStartPageId.value = settingsRepository.defaultStartPageId
        loadAvailableLanguages()
        loadAvailableVoices()
    }

    fun loadAvailableLanguages() {
        if (tempTtsHelper.isReady) {
            _availableLanguages.value = tempTtsHelper.getAvailableLanguages()
        }
    }

    fun loadAvailableVoices() {
        if (tempTtsHelper.isReady) {
            _availableVoices.value = tempTtsHelper.getAvailableVoices(_selectedLanguageTag.value)
        }
    }

    fun setTtsLanguage(languageTag: String) {
        val tagToSave = if (languageTag == "default") null else languageTag
        settingsRepository.ttsLanguage = tagToSave
        _selectedLanguageTag.value = languageTag
        
        // Sprache geändert => Stimme zurücksetzen, da alte Stimme zur alten Sprache gehört
        settingsRepository.ttsVoiceName = null
        _selectedVoiceName.value = null
        
        // Aktualisiere die Dropdowns
        loadAvailableVoices()
        
        // Sprache sofort anwenden und Feedback geben
        tempTtsHelper.setLanguageAndVoice(languageTag, null)
        tempTtsHelper.speak("Sprache geändert")
    }

    fun setTtsVoice(voiceName: String?) {
        settingsRepository.ttsVoiceName = voiceName
        _selectedVoiceName.value = voiceName
        
        tempTtsHelper.setVoice(voiceName)
        tempTtsHelper.speak("Stimme ausgewählt")
    }

    fun setAutoStartScanning(enabled: Boolean) {
        settingsRepository.autoStartScanning = enabled
        _autoStartScanning.value = enabled
    }

    fun setScanDelayInput(input: String) {
        // Erlaube nur Ziffern im Textfeld
        val digitsOnly = input.filter { it.isDigit() }
        _scanDelayInput.value = digitsOnly
        
        // Speichere ab 100ms ein, um Abstürze / irre schnelles Scannen zu vermeiden
        val parsed = digitsOnly.toLongOrNull()
        if (parsed != null && parsed >= 100L) {
            settingsRepository.scanDelayMillis = parsed
        }
    }

    fun setDefaultStartPageId(pageId: String?) {
        settingsRepository.defaultStartPageId = pageId
        _defaultStartPageId.value = pageId
    }

    override fun onCleared() {
        super.onCleared()
        tempTtsHelper.shutdown()
    }
}

class SettingsViewModelFactory(
    private val application: Application,
    private val settingsRepository: SettingsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(application, settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

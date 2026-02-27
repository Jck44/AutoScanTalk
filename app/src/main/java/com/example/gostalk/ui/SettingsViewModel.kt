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

    private val _autoStartScanning = MutableStateFlow(true)
    val autoStartScanning: StateFlow<Boolean> = _autoStartScanning.asStateFlow()

    private val _scanDelayInput = MutableStateFlow("1000")
    val scanDelayInput: StateFlow<String> = _scanDelayInput.asStateFlow()

    private val _defaultStartPageId = MutableStateFlow<String?>(null)
    val defaultStartPageId: StateFlow<String?> = _defaultStartPageId.asStateFlow()

    init {
        // Initiale Einstellungen laden
        _selectedLanguageTag.value = settingsRepository.ttsLanguage ?: "default"
        _autoStartScanning.value = settingsRepository.autoStartScanning
        _scanDelayInput.value = settingsRepository.scanDelayMillis.toString()
        _defaultStartPageId.value = settingsRepository.defaultStartPageId
        loadAvailableLanguages()
    }

    fun loadAvailableLanguages() {
        if (tempTtsHelper.isReady) {
            _availableLanguages.value = tempTtsHelper.getAvailableLanguages()
        }
    }

    fun setTtsLanguage(languageTag: String) {
        val tagToSave = if (languageTag == "default") null else languageTag
        settingsRepository.ttsLanguage = tagToSave
        _selectedLanguageTag.value = languageTag
        
        // Sprache sofort anwenden und Feedback geben
        tempTtsHelper.setLanguage(languageTag)
        tempTtsHelper.speak("Sprache geändert")
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

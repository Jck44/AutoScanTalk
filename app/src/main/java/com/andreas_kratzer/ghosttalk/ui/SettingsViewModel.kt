package com.andreas_kratzer.ghosttalk.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import com.andreas_kratzer.ghosttalk.core.AudioDeviceManager
import com.andreas_kratzer.ghosttalk.model.AudioOutputDevice

class SettingsViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    // Helper für das Abfragen der verfügbaren Sprachen
    private val tempTtsHelper = TextToSpeechHelper(application)
    private val audioDeviceManager = AudioDeviceManager(application)

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

    private val _resumeScanningFromStart = MutableStateFlow(true)
    val resumeScanningFromStart: StateFlow<Boolean> = _resumeScanningFromStart.asStateFlow()

    private val _defaultScanPattern = MutableStateFlow("linear")
    val defaultScanPattern: StateFlow<String> = _defaultScanPattern.asStateFlow()

    private val _defaultStartPageId = MutableStateFlow<String?>(null)
    val defaultStartPageId: StateFlow<String?> = _defaultStartPageId.asStateFlow()

    private val _availableAudioDevices = MutableStateFlow<List<AudioOutputDevice>>(emptyList())
    val availableAudioDevices: StateFlow<List<AudioOutputDevice>> = _availableAudioDevices.asStateFlow()

    private val _selectedTtsAudioDeviceAddress = MutableStateFlow<String?>(null)
    val selectedTtsAudioDeviceAddress: StateFlow<String?> = _selectedTtsAudioDeviceAddress.asStateFlow()

    private val _selectedCuesAudioDeviceAddress = MutableStateFlow<String?>(null)
    val selectedCuesAudioDeviceAddress: StateFlow<String?> = _selectedCuesAudioDeviceAddress.asStateFlow()

    private val _persistActionLogs = MutableStateFlow(false)
    val persistActionLogs: StateFlow<Boolean> = _persistActionLogs.asStateFlow()

    private val _switchActivationKey = MutableStateFlow("Space")
    val switchActivationKey: StateFlow<String> = _switchActivationKey.asStateFlow()

    private val _volumeKeysActivate = MutableStateFlow(false)
    val volumeKeysActivate: StateFlow<Boolean> = _volumeKeysActivate.asStateFlow()

    private val _holdingTimeInput = MutableStateFlow("0")
    val holdingTimeInput: StateFlow<String> = _holdingTimeInput.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        // Initiale Einstellungen laden oder aktualisieren
        _selectedLanguageTag.value = settingsRepository.ttsLanguage ?: "default"
        _selectedVoiceName.value = settingsRepository.ttsVoiceName
        _autoStartScanning.value = settingsRepository.autoStartScanning
        _scanDelayInput.value = settingsRepository.scanDelayMillis.toString()
        _resumeScanningFromStart.value = settingsRepository.resumeScanningFromStart
        _defaultScanPattern.value = settingsRepository.defaultScanPattern
        _defaultStartPageId.value = settingsRepository.defaultStartPageId
        _selectedTtsAudioDeviceAddress.value = settingsRepository.ttsAudioDeviceAddress
        _selectedCuesAudioDeviceAddress.value = settingsRepository.cuesAudioDeviceAddress
        _persistActionLogs.value = settingsRepository.persistActionLogs
        _switchActivationKey.value = settingsRepository.switchActivationKey
        _volumeKeysActivate.value = settingsRepository.volumeKeysActivate
        _holdingTimeInput.value = settingsRepository.holdingTimeMillis.toString()
        
        // Den lokalen TTS-Helper mit den gespeicherten Werten füttern,
        // sonst spricht er in den Einstellungen initial in Systemsprache
        tempTtsHelper.setLanguageAndVoice(_selectedLanguageTag.value, _selectedVoiceName.value)

        loadAvailableLanguages()
        loadAvailableVoices()
        loadAvailableAudioDevices()
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

    fun loadAvailableAudioDevices() {
        _availableAudioDevices.value = audioDeviceManager.getAvailableOutputDevices()
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
        tempTtsHelper.speakRouted("Sprache geändert", settingsRepository.ttsAudioDeviceAddress)
    }

    fun setTtsVoice(voiceName: String?) {
        settingsRepository.ttsVoiceName = voiceName
        _selectedVoiceName.value = voiceName
        
        tempTtsHelper.setVoice(voiceName)
        tempTtsHelper.speakRouted("Stimme ausgewählt", settingsRepository.ttsAudioDeviceAddress)
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

    fun setResumeScanningFromStart(fromStart: Boolean) {
        settingsRepository.resumeScanningFromStart = fromStart
        _resumeScanningFromStart.value = fromStart
    }

    fun setDefaultScanPattern(pattern: String) {
        settingsRepository.defaultScanPattern = pattern
        _defaultScanPattern.value = pattern
    }

    fun setPersistActionLogs(enabled: Boolean) {
        settingsRepository.persistActionLogs = enabled
        _persistActionLogs.value = enabled
    }

    fun setSwitchActivationKey(key: String) {
        settingsRepository.switchActivationKey = key
        _switchActivationKey.value = key
    }

    fun setVolumeKeysActivate(enabled: Boolean) {
        settingsRepository.volumeKeysActivate = enabled
        _volumeKeysActivate.value = enabled
    }

    fun setHoldingTimeInput(input: String) {
        val digitsOnly = input.filter { it.isDigit() }
        _holdingTimeInput.value = digitsOnly

        val parsed = digitsOnly.toLongOrNull()
        if (parsed != null) {
            settingsRepository.holdingTimeMillis = parsed
        }
    }

    fun setDefaultStartPageId(pageId: String?) {
        settingsRepository.defaultStartPageId = pageId
        _defaultStartPageId.value = pageId
    }

    fun setTtsAudioDevice(address: String?) {
        settingsRepository.ttsAudioDeviceAddress = address
        _selectedTtsAudioDeviceAddress.value = address
        tempTtsHelper.speakRouted("Ausgabegerät für Sprechen ausgewählt", address)
    }

    fun setCuesAudioDevice(address: String?) {
        settingsRepository.cuesAudioDeviceAddress = address
        _selectedCuesAudioDeviceAddress.value = address
        tempTtsHelper.speakRouted("Ausgabegerät für Feedback ausgewählt", address)
    }

    fun getResolvedDeviceName(savedAddress: String?): String {
        if (savedAddress.isNullOrBlank()) return "System-Standard (Automatisch)"
        val devices = _availableAudioDevices.value
        
        val exactMatch = devices.find { it.address == savedAddress }
        if (exactMatch != null) return exactMatch.name
        
        val parts = savedAddress.split("|", limit = 2)
        val fallbackPart = if (parts.size > 1) parts[1] else parts[0]
        
        val fuzzyMatch = devices.find { device ->
            val deviceParts = device.address.split("|", limit = 2)
            val deviceFallback = if (deviceParts.size > 1) deviceParts[1] else deviceParts[0]
            deviceFallback == fallbackPart
        }
        
        return fuzzyMatch?.name ?: "System-Standard (Automatisch)"
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

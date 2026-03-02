package com.andreas_kratzer.ghosttalk.ui

import android.app.Application
import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.andreas_kratzer.ghosttalk.core.AudioDeviceManager
import com.andreas_kratzer.ghosttalk.core.cloud.DriveAuthManager
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.domain.CloudSyncUseCase
import com.andreas_kratzer.ghosttalk.domain.GeminiUseCase
import com.andreas_kratzer.ghosttalk.model.AudioOutputDevice
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class SettingsViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository,
    private val driveAuthManager: DriveAuthManager,
    private val cloudSyncUseCase: CloudSyncUseCase,
    private val geminiUseCaseProvider: (suspend () -> String?) -> GeminiUseCase
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

    private val _isCloudSyncEnabled = MutableStateFlow(false)
    val isCloudSyncEnabled: StateFlow<Boolean> = _isCloudSyncEnabled.asStateFlow()

    private val _isGeminiEnabled = MutableStateFlow(false)
    val isGeminiEnabled: StateFlow<Boolean> = _isGeminiEnabled.asStateFlow()

    private val _selectedAppLanguage = MutableStateFlow<String?>("default")
    val selectedAppLanguage: StateFlow<String?> = _selectedAppLanguage.asStateFlow()

    val userEmail: StateFlow<String?> = driveAuthManager.userEmail

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _authIntentFlow = MutableSharedFlow<Intent>()
    val authIntentFlow = _authIntentFlow.asSharedFlow()

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
        _isCloudSyncEnabled.value = settingsRepository.isCloudSyncEnabled
        _isGeminiEnabled.value = settingsRepository.isGeminiEnabled
        _selectedAppLanguage.value = settingsRepository.appLanguage ?: "default"
        
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

    fun setCloudSyncEnabled(enabled: Boolean) {
        settingsRepository.isCloudSyncEnabled = enabled
        _isCloudSyncEnabled.value = enabled
    }

    fun setAppLanguage(languageCode: String?) {
        val codeToSave = if (languageCode == "default") null else languageCode
        settingsRepository.appLanguage = codeToSave
        _selectedAppLanguage.value = languageCode ?: "default"
        
        val appLocale: LocaleListCompat = if (languageCode == null || languageCode == "default") {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageCode)
        }
        AppCompatDelegate.setApplicationLocales(appLocale)
    }

    fun signIn(context: android.content.Context) {
        android.util.Log.d("SettingsViewModel", "signIn called")
        val activity = findActivity(context)
        if (activity == null) {
            android.widget.Toast.makeText(context, "Keine Activity gefunden!", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        
        viewModelScope.launch {
            android.widget.Toast.makeText(context, "Anmeldung wird gestartet...", android.widget.Toast.LENGTH_SHORT).show()
            val result = driveAuthManager.signIn(activity)
            if (result) {
                android.widget.Toast.makeText(context, "Anmeldung erfolgreich!", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(context, "Anmeldung fehlgeschlagen. Bitte prüfe, ob ein Google-Konto auf dem Gerät angemeldet ist und die Client ID korrekt konfiguriert wurde.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun findActivity(context: android.content.Context): android.app.Activity? {
        var currentContext = context
        while (currentContext is android.content.ContextWrapper) {
            if (currentContext is android.app.Activity) return currentContext
            currentContext = currentContext.baseContext
        }
        return null
    }

    fun signOut() {
        viewModelScope.launch {
            driveAuthManager.signOut()
        }
    }

    fun syncNow() {
        val context = getApplication<Application>().applicationContext
        val credential = driveAuthManager.getDriveCredential()
        if (credential == null) {
            android.util.Log.w("SettingsViewModel", "syncNow: No credential available. User might not be signed in.")
            android.widget.Toast.makeText(context, "Nicht angemeldet!", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val bookId = settingsRepository.activeBookId
        
        viewModelScope.launch {
            _isSyncing.value = true
            android.util.Log.d("SettingsViewModel", "Starting manual sync for book: $bookId")
            try {
                val drive = Drive.Builder(
                    NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
                ).setApplicationName("GhosTTalk").build()
                
                cloudSyncUseCase.syncBook(drive, bookId)
                android.util.Log.d("SettingsViewModel", "Sync completed successfully")
                android.widget.Toast.makeText(context, "Synchronisierung abgeschlossen", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: UserRecoverableAuthIOException) {
                android.util.Log.w("SettingsViewModel", "UserRecoverableAuthIOException: Emitting auth intent")
                _authIntentFlow.emit(e.intent)
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Sync failed with exception: ${e.message}", e)
                android.widget.Toast.makeText(context, "Fehler bei der Synchronisierung", android.widget.Toast.LENGTH_LONG).show()
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun activateGemini(context: android.content.Context) {
        val gemini = geminiUseCaseProvider {
            driveAuthManager.getDriveCredential()?.getToken()
        }
        
        viewModelScope.launch {
            try {
                android.util.Log.d("SettingsViewModel", "Triggering Gemini test call...")
                val response = gemini.generateResponse("Ping")
                android.util.Log.d("SettingsViewModel", "Gemini test call response: $response")
                
                settingsRepository.isGeminiEnabled = true
                _isGeminiEnabled.value = true
                android.widget.Toast.makeText(context, "Gemini aktiv!", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: UserRecoverableAuthIOException) {
                android.util.Log.e("SettingsViewModel", "Caught UserRecoverableAuthIOException, emitting intent", e)
                e.intent?.let { _authIntentFlow.emit(it) }
            } catch (e: UserRecoverableAuthException) {
                android.util.Log.e("SettingsViewModel", "Caught UserRecoverableAuthException, emitting intent", e)
                e.intent?.let { _authIntentFlow.emit(it) }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Gemini activation failed: ${e::class.java.name}: ${e.message}", e)
                
                // Debug: List models to Logcat
                viewModelScope.launch {
                    val models = gemini.listModels()
                    android.util.Log.d("SettingsViewModel", "Available Gemini models: $models")
                }
                
                // Try to find a wrapped recoverable exception
                var cause: Throwable? = e
                var handled = false
                while (cause != null) {
                    if (cause is UserRecoverableAuthIOException) {
                        cause.intent?.let { _authIntentFlow.emit(it) }
                        handled = true
                        break
                    }
                    if (cause is UserRecoverableAuthException) {
                        cause.intent?.let { _authIntentFlow.emit(it) }
                        handled = true
                        break
                    }
                    cause = cause.cause
                }
                
                if (!handled) {
                    android.widget.Toast.makeText(context, "Fehler: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
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
            val driveAuthManager = DriveAuthManager.getInstance(application)
            val db = com.andreas_kratzer.ghosttalk.data.AppDatabase.getDatabase(application)
            val pageRepo = com.andreas_kratzer.ghosttalk.data.PageRepository(db.pageDao())
            val importExportManager = com.andreas_kratzer.ghosttalk.core.PageImportExportManager(pageRepo, com.andreas_kratzer.ghosttalk.core.util.AppLogger)
            val cloudSyncUseCase =
                CloudSyncUseCase(application, pageRepo, settingsRepository, importExportManager)
            
            val geminiProvider = { tokenProvider: suspend () -> String? ->
                GeminiUseCase(
                    tokenProvider,
                    driveProvider = {
                        val credential = driveAuthManager.getDriveCredential() ?: return@GeminiUseCase null
                        Drive.Builder(
                            NetHttpTransport(),
                            GsonFactory.getDefaultInstance(),
                            credential
                        ).setApplicationName("GhosTTalk").build()
                    }
                )
            }
            
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(application, settingsRepository, driveAuthManager, cloudSyncUseCase, geminiProvider) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

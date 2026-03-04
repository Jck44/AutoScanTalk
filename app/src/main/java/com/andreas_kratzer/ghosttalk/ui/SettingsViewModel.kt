package com.andreas_kratzer.ghosttalk.ui

import android.app.Application
import android.content.Intent
import android.speech.tts.Voice
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andreas_kratzer.ghosttalk.core.AudioDeviceManager
import com.andreas_kratzer.ghosttalk.core.cloud.DriveAuthManager
import com.andreas_kratzer.ghosttalk.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.domain.CloudSyncUseCase
import com.andreas_kratzer.ghosttalk.domain.GeminiUseCaseFactory
import com.andreas_kratzer.ghosttalk.model.AudioOutputDevice
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val settingsRepository: SettingsRepository,
    private val driveAuthManager: DriveAuthManager,
    private val cloudSyncUseCase: CloudSyncUseCase,
    private val geminiUseCaseFactory: GeminiUseCaseFactory,
    private val tempTtsHelper: TextToSpeechHelper,
    private val audioDeviceManager: AudioDeviceManager,
    private val workManager: androidx.work.WorkManager,
    private val buttonUsageRepository: ButtonUsageRepository
) : AndroidViewModel(application) {

    private val _availableLanguages = MutableStateFlow<List<Locale>>(emptyList())
    val availableLanguages: StateFlow<List<Locale>> = _availableLanguages.asStateFlow()

    val activeBookId: String get() = settingsRepository.activeBookId

    private val _selectedLanguageTag = MutableStateFlow("default")
    val selectedLanguageTag: StateFlow<String> = _selectedLanguageTag.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<Voice>>(emptyList())
    val availableVoices: StateFlow<List<Voice>> = _availableVoices.asStateFlow()

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

    private val _showTestButtons = MutableStateFlow(false)
    val showTestButtons: StateFlow<Boolean> = _showTestButtons.asStateFlow()

    private val _holdingTimeInput = MutableStateFlow("0")
    val holdingTimeInput: StateFlow<String> = _holdingTimeInput.asStateFlow()

    private val _syncIntervalMinutesInput = MutableStateFlow("15")
    val syncIntervalMinutesInput: StateFlow<String> = _syncIntervalMinutesInput.asStateFlow()

    private val _syncMode = MutableStateFlow("TWO_WAY")
    val syncMode: StateFlow<String> = _syncMode.asStateFlow()

    private val _isCloudSyncEnabled = MutableStateFlow(false)
    val isCloudSyncEnabled: StateFlow<Boolean> = _isCloudSyncEnabled.asStateFlow()

    private val _isGeminiEnabled = MutableStateFlow(false)
    val isGeminiEnabled: StateFlow<Boolean> = _isGeminiEnabled.asStateFlow()

    private val _isSmartPredictionEnabled = MutableStateFlow(false)
    val isSmartPredictionEnabled: StateFlow<Boolean> = _isSmartPredictionEnabled.asStateFlow()

    private val _isNotificationReadingEnabled = MutableStateFlow(false)
    val isNotificationReadingEnabled: StateFlow<Boolean> = _isNotificationReadingEnabled.asStateFlow()

    private val _monitoredNotificationApps = MutableStateFlow<Set<String>>(emptySet())
    val monitoredNotificationApps: StateFlow<Set<String>> = _monitoredNotificationApps.asStateFlow()

    private val _geminiToolStatus = MutableStateFlow<Map<String, com.andreas_kratzer.ghosttalk.domain.GeminiUseCase.ToolStatus>>(emptyMap())
    val geminiToolStatus: StateFlow<Map<String, com.andreas_kratzer.ghosttalk.domain.GeminiUseCase.ToolStatus>> = _geminiToolStatus.asStateFlow()

    private val _showPageIdInLog = MutableStateFlow(true)
    val showPageIdInLog: StateFlow<Boolean> = _showPageIdInLog.asStateFlow()

    private val _selectedAppLanguage = MutableStateFlow<String?>("default")
    val selectedAppLanguage: StateFlow<String?> = _selectedAppLanguage.asStateFlow()

    private val _experimentalManualSorting = MutableStateFlow(false)
    val experimentalManualSorting: StateFlow<Boolean> = _experimentalManualSorting.asStateFlow()

    private val _themeMode = MutableStateFlow("SYSTEM")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    val userEmail: StateFlow<String?> = driveAuthManager.userEmail

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _smartPredictionDelayMillisInput = MutableStateFlow("2000")
    val smartPredictionDelayMillisInput: StateFlow<String> = _smartPredictionDelayMillisInput.asStateFlow()

    val lastSuccessfulSyncTime: StateFlow<Long> = settingsRepository.lastSuccessfulSyncTimeFlow

    private val _authIntentFlow = MutableSharedFlow<Intent>()
    val authIntentFlow = _authIntentFlow.asSharedFlow()

    init {
        refresh()
        
        viewModelScope.launch {
            settingsRepository.smartPredictionDelayMillisFlow.collect {
                _smartPredictionDelayMillisInput.value = it.toString()
            }
        }
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
        _showTestButtons.value = settingsRepository.showTestButtons
        _holdingTimeInput.value = settingsRepository.holdingTimeMillis.toString()
        _syncIntervalMinutesInput.value = settingsRepository.syncIntervalMinutes.toString()
        _syncMode.value = settingsRepository.syncMode
        _isCloudSyncEnabled.value = settingsRepository.isCloudSyncEnabled
        _isGeminiEnabled.value = settingsRepository.isGeminiEnabled
        _selectedAppLanguage.value = settingsRepository.appLanguage ?: "default"
        _themeMode.value = settingsRepository.themeMode
        _showPageIdInLog.value = settingsRepository.showPageIdInLog
        _experimentalManualSorting.value = settingsRepository.experimentalManualSorting
        _isSmartPredictionEnabled.value = settingsRepository.isSmartPredictionEnabled
        _isNotificationReadingEnabled.value = settingsRepository.isNotificationReadingEnabled
        _monitoredNotificationApps.value = settingsRepository.monitoredNotificationApps
        
        updateGeminiToolStatus()
        
        // Den lokalen TTS-Helper mit den gespeicherten Werten füttern,
        // sonst spricht er in den Einstellungen initial in Systemsprache
        tempTtsHelper.setLanguageAndVoice(_selectedLanguageTag.value, _selectedVoiceName.value)

        loadAvailableLanguages()
        loadAvailableVoices()
        loadAvailableAudioDevices()

        tempTtsHelper.fallbackListener = object : TextToSpeechHelper.OnVoiceFallbackListener {
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
        val available = audioDeviceManager.getAvailableOutputDevices()
        
        // Cache names of currently available devices
        available.forEach { device ->
            val persistentId = device.address.split("|").lastOrNull() ?: device.address
            settingsRepository.saveDeviceName(persistentId, device.name)
        }

        val ttsAddress = _selectedTtsAudioDeviceAddress.value
        val cuesAddress = _selectedCuesAudioDeviceAddress.value
        
        val mergedList = available.toMutableList()
        
        // Add "ghost" entries for selected but currently unavailable devices
        listOfNotNull(ttsAddress, cuesAddress).distinct().forEach { selectedAddress ->
            if (mergedList.none { it.address == selectedAddress }) {
                val persistentId = selectedAddress.split("|").lastOrNull() ?: selectedAddress
                val cachedName = settingsRepository.getDeviceName(persistentId)
                if (cachedName != null) {
                    val fallbackMatch = available.find { 
                        val devPersistentId = it.address.split("|").lastOrNull() ?: it.address
                        devPersistentId == persistentId 
                    }
                    
                    if (fallbackMatch == null) {
                        mergedList.add(
                            AudioOutputDevice(
                                address = selectedAddress,
                                name = "$cachedName (Inaktiv)",
                                type = 0,
                                isBuiltIn = false
                            )
                        )
                    }
                }
            }
        }

        _availableAudioDevices.value = mergedList
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

    fun setShowTestButtons(enabled: Boolean) {
        settingsRepository.showTestButtons = enabled
        _showTestButtons.value = enabled
    }

    fun setShowPageIdInLog(enabled: Boolean) {
        settingsRepository.showPageIdInLog = enabled
        _showPageIdInLog.value = enabled
    }

    fun setExperimentalManualSorting(enabled: Boolean) {
        settingsRepository.experimentalManualSorting = enabled
        _experimentalManualSorting.value = enabled
    }

    fun setHoldingTimeInput(input: String) {
        val digitsOnly = input.filter { it.isDigit() }
        _holdingTimeInput.value = digitsOnly

        val parsed = digitsOnly.toLongOrNull()
        if (parsed != null && parsed >= 0L) {
            settingsRepository.holdingTimeMillis = parsed
        }
    }

    fun setSyncIntervalMinutesInput(input: String) {
        val digitsOnly = input.filter { it.isDigit() }
        
        val parsed = digitsOnly.toLongOrNull()
        if (parsed != null && parsed >= 15L) {
            _syncIntervalMinutesInput.value = digitsOnly
            settingsRepository.syncIntervalMinutes = parsed
        } else {
            // Clamp UI and value to minimum 15
            _syncIntervalMinutesInput.value = "15"
            settingsRepository.syncIntervalMinutes = 15L
        }
        
        // If already enabled, reschedule with new interval
        if (_isCloudSyncEnabled.value) {
            scheduleCloudSync()
        }
    }

    fun setSyncMode(mode: String) {
        settingsRepository.syncMode = mode
        _syncMode.value = mode
        
        // Mode change doesn't technically require rescheduling as the worker reads it from repo,
        // but it's cleaner to have it consistent if we ever change worker params.
    }

    fun setCloudSyncEnabled(enabled: Boolean) {
        settingsRepository.isCloudSyncEnabled = enabled
        _isCloudSyncEnabled.value = enabled
        
        if (enabled) {
            scheduleCloudSync()
        } else {
            workManager.cancelUniqueWork("CloudSyncWorker")
        }
    }


    fun clearButtonUsageStats(bookId: String) {
        viewModelScope.launch {
            buttonUsageRepository.clearStats(bookId)
        }
    }

    private fun scheduleCloudSync() {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()
        
        val intervalMin = settingsRepository.syncIntervalMinutes
        val workRequest = androidx.work.PeriodicWorkRequestBuilder<com.andreas_kratzer.ghosttalk.core.cloud.CloudSyncWorker>(
            intervalMin, java.util.concurrent.TimeUnit.MINUTES
        ).setConstraints(constraints).build()
        
        workManager.enqueueUniquePeriodicWork(
            "CloudSyncWorker",
            androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
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

    fun setThemeMode(mode: String) {
        settingsRepository.themeMode = mode
        _themeMode.value = mode
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
        performManualSync(com.andreas_kratzer.ghosttalk.domain.SyncMode.TWO_WAY)
    }

    fun backupNow(driveOverride: Drive? = null) {
        performManualSync(com.andreas_kratzer.ghosttalk.domain.SyncMode.BACKUP_ONLY, driveOverride)
    }

    fun restoreNow(driveOverride: Drive? = null) {
        performManualSync(com.andreas_kratzer.ghosttalk.domain.SyncMode.RESTORE_ONLY, driveOverride)
    }

    private fun performManualSync(mode: com.andreas_kratzer.ghosttalk.domain.SyncMode, driveOverride: Drive? = null) {
        val context = getApplication<Application>().applicationContext
        val credential = driveAuthManager.getDriveCredential()
        if (credential == null && driveOverride == null) {
            android.util.Log.w("SettingsViewModel", "syncNow: No credential available. User might not be signed in.")
            android.widget.Toast.makeText(context, "Nicht angemeldet!", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val bookId = settingsRepository.activeBookId
        
        viewModelScope.launch {
            _isSyncing.value = true
            android.util.Log.d("SettingsViewModel", "Starting manual sync for book: $bookId with mode: $mode")
            try {
                val drive = driveOverride ?: Drive.Builder(
                    NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
                ).setApplicationName("GhosTTalk").build()
                
                cloudSyncUseCase.syncBook(drive, bookId, mode)
                settingsRepository.lastSuccessfulSyncTime = System.currentTimeMillis()
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

    fun setGeminiEnabled(enabled: Boolean) {
        settingsRepository.isGeminiEnabled = enabled
        _isGeminiEnabled.value = enabled
        updateGeminiToolStatus()
    }

    private fun updateGeminiToolStatus() {
        val gemini = geminiUseCaseFactory.create { null } // We just need the status logic
        _geminiToolStatus.value = gemini.getToolStatus(driveAuthManager.userEmail.value != null)
    }

    fun activateGemini(context: android.content.Context) {
        val gemini = geminiUseCaseFactory.create {
            driveAuthManager.getDriveCredential()?.getToken()
        }
        
        viewModelScope.launch {
            try {
                android.util.Log.d("SettingsViewModel", "Triggering Gemini test call...")
                val response = gemini.generateResponse("Ping")
                android.util.Log.d("SettingsViewModel", "Gemini test call response: $response")
                
                settingsRepository.isGeminiEnabled = true
                _isGeminiEnabled.value = true
                updateGeminiToolStatus()
                android.widget.Toast.makeText(context, getApplication<Application>().getString(com.andreas_kratzer.ghosttalk.R.string.settings_gemini_activation_success), android.widget.Toast.LENGTH_SHORT).show()
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
                    val errorMsg = getApplication<Application>().getString(com.andreas_kratzer.ghosttalk.R.string.settings_gemini_activation_error, e.message ?: "Unknown error")
                    android.widget.Toast.makeText(context, errorMsg, android.widget.Toast.LENGTH_LONG).show()
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
        
        // 1. Try currently available list
        val devices = _availableAudioDevices.value
        val exactMatch = devices.find { it.address == savedAddress }
        if (exactMatch != null) return exactMatch.name
        
        // 2. Try persistent cache
        val persistentId = savedAddress.split("|").lastOrNull() ?: savedAddress
        val cachedName = settingsRepository.getDeviceName(persistentId)
        if (cachedName != null) {
            return "$cachedName (Laden...)"
        }
        
        // 3. Fallback
        return "System-Standard (Automatisch)"
    }

    fun setSmartPredictionDelayInput(input: String) {
        val digitsOnly = input.filter { it.isDigit() }
        _smartPredictionDelayMillisInput.value = digitsOnly

        val parsed = digitsOnly.toLongOrNull()
        if (parsed != null && parsed >= 0L) {
            settingsRepository.smartPredictionDelayMillis = parsed
        }
    }

    fun setSmartPredictionEnabled(enabled: Boolean) {
        settingsRepository.isSmartPredictionEnabled = enabled
        _isSmartPredictionEnabled.value = enabled
    }

    fun setNotificationReadingEnabled(enabled: Boolean) {
        settingsRepository.isNotificationReadingEnabled = enabled
        _isNotificationReadingEnabled.value = enabled
    }

    fun toggleMonitoredNotificationApp(appPackage: String, enabled: Boolean) {
        val currentApps = _monitoredNotificationApps.value.toMutableSet()
        if (enabled) {
            currentApps.add(appPackage)
        } else {
            currentApps.remove(appPackage)
        }
        settingsRepository.monitoredNotificationApps = currentApps
        _monitoredNotificationApps.value = currentApps
    }

    override fun onCleared() {
        super.onCleared()
        tempTtsHelper.shutdown()
        
        // Cleanup device cache: Keep only currently selected devices
        val keepIds = mutableSetOf<String>()
        _selectedTtsAudioDeviceAddress.value?.split("|")?.lastOrNull()?.let { keepIds.add(it) }
        _selectedCuesAudioDeviceAddress.value?.split("|")?.lastOrNull()?.let { keepIds.add(it) }
        settingsRepository.cleanupDeviceCache(keepIds)
    }
}

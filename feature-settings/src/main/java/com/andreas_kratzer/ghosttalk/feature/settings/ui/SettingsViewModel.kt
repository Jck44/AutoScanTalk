package com.andreas_kratzer.ghosttalk.feature.settings.ui

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andreas_kratzer.ghosttalk.core.SecurityManager
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.data.export.PageImportExportProvider
import com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.GetPagesUseCase
import com.andreas_kratzer.ghosttalk.core.cloud.PhilipsHueManager
import com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.CloudSyncSettingsDelegate
import com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.ExperimentalSettingsDelegate
import com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.GenAiSettingsDelegate
import com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.ScanningSettingsDelegate
import com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates.TtsSettingsDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val application: Application,
    val settingsRepository: SettingsRepository,
    private val buttonUsageRepository: ButtonUsageRepository,
    val securityManager: SecurityManager,
    getPagesUseCase: GetPagesUseCase,
    val ttsDelegate: TtsSettingsDelegate,
    val scanningDelegate: ScanningSettingsDelegate,
    val cloudSyncDelegate: CloudSyncSettingsDelegate,
    val genAiDelegate: GenAiSettingsDelegate,
    val experimentalDelegate: ExperimentalSettingsDelegate,
    private val importExportManager: PageImportExportProvider,
    private val hueManager: PhilipsHueManager
) : AndroidViewModel(application) {

    private val _activeBookId = settingsRepository.activeBookIdFlow
    val allPages: StateFlow<List<Page>> = getPagesUseCase.execute(_activeBookId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // --- Observable State from Repository ---
    val availableLanguages = ttsDelegate.availableLanguages
    val availableVoices = ttsDelegate.availableVoices
    val availableAudioDevices = ttsDelegate.availableAudioDevices
    
    val selectedLanguageTag = settingsRepository.ttsLanguageFlow
    val selectedVoiceName = settingsRepository.ttsVoiceNameFlow
    
    val autoStartScanning = settingsRepository.autoStartScanningFlow
    val scanDelayMillis = settingsRepository.scanDelayFlow
    val resumeScanningFromStart = settingsRepository.resumeScanningFromStartFlow
    val defaultScanPattern = settingsRepository.defaultScanPatternFlow
    val holdingTimeMillis = settingsRepository.holdingTimeMillisFlow
    val bluetoothDelay = settingsRepository.bluetoothDelayFlow
    
    val defaultStartPageId = settingsRepository.defaultStartPageIdFlow
    val selectedTtsAudioDeviceAddress = settingsRepository.ttsAudioDeviceAddressFlow
    val selectedCuesAudioDeviceAddress = settingsRepository.cuesAudioDeviceAddressFlow
    
    val persistActionLogs = settingsRepository.persistActionLogsFlow
    val switchActivationKey = settingsRepository.switchActivationKeyFlow
    val volumeKeysActivate = settingsRepository.volumeKeysActivateFlow
    val showTestButtons = settingsRepository.showTestButtonsFlow
    val showPageIdInLog = settingsRepository.showPageIdInLogFlow
    
    val isCloudSyncEnabled = settingsRepository.isCloudSyncEnabledFlow
    val syncMode = settingsRepository.syncModeFlow
    val lastSuccessfulSyncTime = settingsRepository.lastSuccessfulSyncTimeFlow
    val syncIntervalMinutes = settingsRepository.syncIntervalMinutesFlow
    val googleHomeProjectId = settingsRepository.googleHomeProjectIdFlow
    val hueBridgeIp = settingsRepository.hueBridgeIpFlow
    val hueUsername = settingsRepository.hueUsernameFlow
    val hueAccessToken = settingsRepository.hueAccessTokenFlow
    val goveeApiKey = settingsRepository.goveeApiKeyFlow
    val isSyncing = cloudSyncDelegate.isSyncing
    val userEmail = cloudSyncDelegate.userEmail
    
    val availableBackups = cloudSyncDelegate.availableBackups
    val showBackupSelectionDialog = cloudSyncDelegate.showBackupSelectionDialog
    
    val isGeminiEnabled = settingsRepository.isGeminiEnabledFlow
    val useLocalGenerativeAi = settingsRepository.useLocalGenerativeAiFlow
    val geminiToolStatus = genAiDelegate.geminiToolStatus
    
    val isSmartPredictionEnabled = settingsRepository.isSmartPredictionEnabledFlow
    
    val isNotificationReadingEnabled = settingsRepository.isNotificationReadingEnabledFlow
    val monitoredNotificationApps = settingsRepository.monitoredNotificationAppsFlow
    
    val selectedAppLanguage = settingsRepository.appLanguageFlow
    val themeMode = settingsRepository.themeModeFlow
    val buttonHistory = buttonUsageRepository.buttonHistory

    val keepScreenOnUserMode = settingsRepository.keepScreenOnUserModeFlow
    val userModeScreenBehavior = settingsRepository.userModeScreenBehaviorFlow
    val geminiTimeout = settingsRepository.geminiTimeoutFlow
    val geminiRedoPrediction = settingsRepository.geminiRedoPredictionFlow

    val securityPin = settingsRepository.securityPinFlow
    val securityPinTimeoutMinutes = settingsRepository.securityPinTimeoutMinutesFlow
    val isPinRequiredForDeletion = settingsRepository.isPinRequiredForDeletionFlow
    val isBiometricEnabled = settingsRepository.isBiometricEnabledFlow
    val isSecurityRequiredForEdit = settingsRepository.isSecurityRequiredForEditFlow
    val isSecurityRequiredForSettings = settingsRepository.isSecurityRequiredForSettingsFlow
    val startupBehavior = settingsRepository.startupBehaviorFlow

    val authIntentFlow = kotlinx.coroutines.flow.merge(
        cloudSyncDelegate.authIntentFlow,
        genAiDelegate.authIntentFlow
    )
    
    val signInErrorMessage = cloudSyncDelegate.signInErrorMessage

    init {
        ttsDelegate.initialize(viewModelScope) { original, fallback ->
            val message = if (fallback != null) {
                "Stimme $original nicht verfügbar. Fallback auf $fallback."
            } else {
                "Stimme $original nicht verfügbar. Fallback auf System-Standard."
            }
            Toast.makeText(application, message, Toast.LENGTH_LONG).show()
        }
        genAiDelegate.updateGeminiToolStatus()
        viewModelScope.launch {
            genAiDelegate.performGeminiNanoIntegrityCheck()
        }
    }

    // --- Delegation Methods (UI Actions) ---
    fun refresh() {
        ttsDelegate.loadAvailableLanguages()
        ttsDelegate.loadAvailableVoices()
        ttsDelegate.loadAvailableAudioDevices()
        genAiDelegate.updateGeminiToolStatus()
        viewModelScope.launch {
            genAiDelegate.performGeminiNanoIntegrityCheck()
        }
    }

    fun setTtsLanguage(tag: String) = ttsDelegate.setTtsLanguage(tag)
    fun setTtsVoice(name: String?) = ttsDelegate.setTtsVoice(name)
    fun setTtsAudioDevice(addr: String?) = ttsDelegate.setTtsAudioDevice(addr)
    fun setCuesAudioDevice(addr: String?) = ttsDelegate.setCuesAudioDevice(addr)
    fun getResolvedDeviceName(addr: String?) = ttsDelegate.getResolvedDeviceName(addr)

    fun setAutoStartScanning(enabled: Boolean) = scanningDelegate.setAutoStartScanning(enabled)
    fun setScanDelayInput(input: String) = scanningDelegate.setScanDelayInput(input)
    fun setResumeScanningFromStart(b: Boolean) = scanningDelegate.setResumeScanningFromStart(b)
    fun setDefaultScanPattern(p: String) = scanningDelegate.setDefaultScanPattern(p)
    fun setHoldingTimeInput(i: String) = scanningDelegate.setHoldingTimeInput(i)
    fun setBluetoothDelay(d: String) = scanningDelegate.setBluetoothDelay(d)

    fun signIn(ctx: Context) = cloudSyncDelegate.signIn(ctx, viewModelScope)
    
    fun signOut() {
        cloudSyncDelegate.signOut(viewModelScope)
        // Disable cloud-dependent features on sign out
        settingsRepository.isCloudSyncEnabled = false
        settingsRepository.isGeminiEnabled = false
        genAiDelegate.updateGeminiToolStatus()
    }

    fun setCloudSyncEnabled(ctx: Context, e: Boolean) = cloudSyncDelegate.setCloudSyncEnabled(ctx, e, viewModelScope)
    fun syncNow() = cloudSyncDelegate.performManualSync(com.andreas_kratzer.ghosttalk.core.cloud.domain.SyncMode.TWO_WAY, viewModelScope)
    fun backupNow() = cloudSyncDelegate.performManualSync(com.andreas_kratzer.ghosttalk.core.cloud.domain.SyncMode.BACKUP_ONLY, viewModelScope)
    fun restoreNow() = cloudSyncDelegate.performManualSync(com.andreas_kratzer.ghosttalk.core.cloud.domain.SyncMode.RESTORE_ONLY, viewModelScope)
    fun fetchAvailableBackupsForImport() = cloudSyncDelegate.fetchAvailableBackupsForImport(viewModelScope)
    fun importCloudBackup(backupInfo: com.andreas_kratzer.ghosttalk.core.cloud.domain.RemoteBackupInfo) = cloudSyncDelegate.importCloudBackup(backupInfo, viewModelScope)
    fun dismissBackupSelectionDialog() = cloudSyncDelegate.dismissBackupSelectionDialog()
    
    fun setSyncMode(m: String) { settingsRepository.syncMode = m }
    fun setSyncIntervalMinutes(minutes: Long) { settingsRepository.syncIntervalMinutes = minutes }
    fun setGoogleHomeProjectId(id: String) { settingsRepository.googleHomeProjectId = id }
    fun setHueBridgeIp(ip: String) { settingsRepository.hueBridgeIp = ip }
    fun setHueUsername(username: String) { settingsRepository.hueUsername = username }
    fun setHueAccessToken(token: String) { settingsRepository.hueAccessToken = token }
    fun setGoveeApiKey(key: String) { settingsRepository.goveeApiKey = key }

    fun discoverHueBridges() {
        viewModelScope.launch {
            Toast.makeText(application, "Suche nach Hue Bridges...", Toast.LENGTH_SHORT).show()
            val bridges = hueManager.discoverBridges()
            if (bridges.isNotEmpty()) {
                settingsRepository.hueBridgeIp = bridges.first()
                Toast.makeText(application, "Bridge gefunden: ${bridges.first()}", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(application, "Keine Hue Bridge im Netzwerk gefunden.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun startHueOAuth() {
        // This is a placeholder for now as it requires a redirect activity
        Toast.makeText(application, "Philips Hue OAuth wird in Kürze implementiert.", Toast.LENGTH_LONG).show()
    }

    fun setGeminiEnabled(ctx: Context, e: Boolean) {
        genAiDelegate.setGeminiCloudEnabled(ctx, e, viewModelScope)
    }
    
    fun setGeminiNanoEnabled(ctx: Context, e: Boolean) {
        genAiDelegate.setGeminiNanoEnabled(ctx, e, viewModelScope)
    }

    fun activateGemini(ctx: Context) = genAiDelegate.activateGemini(ctx, viewModelScope)
    fun testGeminiNano(ctx: Context) = genAiDelegate.testGeminiNano(ctx, viewModelScope)
    
    val isDownloadDialogVisible = genAiDelegate.isDownloadDialogVisible
    val downloadProgress = genAiDelegate.downloadProgress
    val downloadStatusMessage = genAiDelegate.downloadStatusMessage
    val isDownloading = genAiDelegate.isDownloading
    val nanoFeatureStatus = genAiDelegate.nanoFeatureStatus

    fun startGeminiDownload() = genAiDelegate.startGeminiDownload(viewModelScope)
    fun dismissDownloadDialog() = genAiDelegate.dismissDownloadDialog()

    val isDeactivationDialogVisible = genAiDelegate.isDeactivationDialogVisible
    fun dismissDeactivationDialog() = genAiDelegate.dismissDeactivationDialog()

    fun setUseLocalGenerativeAi(e: Boolean, ctx: Context? = null) {
        if (e && ctx != null) {
            genAiDelegate.setGeminiNanoEnabled(ctx, e, viewModelScope)
        } else {
            settingsRepository.useLocalGenerativeAi = e
        }
    }

    fun setPersistActionLogs(e: Boolean) { settingsRepository.persistActionLogs = e }
    fun setSwitchActivationKey(k: String) { settingsRepository.switchActivationKey = k }
    fun setVolumeKeysActivate(e: Boolean) { settingsRepository.volumeKeysActivate = e }
    fun setShowTestButtons(e: Boolean) { settingsRepository.showTestButtons = e }
    fun setShowPageIdInLog(e: Boolean) { settingsRepository.showPageIdInLog = e }
    fun setSmartPredictionEnabled(e: Boolean) = experimentalDelegate.setSmartPredictionEnabled(e)
    fun setDefaultStartPageId(id: String?) { settingsRepository.defaultStartPageId = id }

    fun setAppLanguage(code: String?) {
        val finalCode = if (code == "default") null else code
        settingsRepository.appLanguage = finalCode
        val appLocale = if (finalCode == null) androidx.core.os.LocaleListCompat.getEmptyLocaleList() else androidx.core.os.LocaleListCompat.forLanguageTags(finalCode)
        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(appLocale)
    }
    fun setThemeMode(m: String) { settingsRepository.themeMode = m }

    fun setNotificationReadingEnabled(e: Boolean) { settingsRepository.isNotificationReadingEnabled = e }
    fun toggleMonitoredNotificationApp(pkg: String, e: Boolean) {
        val current = settingsRepository.monitoredNotificationApps.toMutableSet()
        if (e) current.add(pkg) else current.remove(pkg)
        settingsRepository.monitoredNotificationApps = current
    }

    fun clearButtonUsageStats(bookId: String) {
        viewModelScope.launch { buttonUsageRepository.clearStats(bookId) }
    }

    fun setKeepScreenOnUserMode(e: Boolean) { settingsRepository.keepScreenOnUserMode = e }
    fun setUserModeScreenBehavior(m: String) { settingsRepository.userModeScreenBehavior = m }
    fun setGeminiTimeoutInput(input: String) {
        input.toLongOrNull()?.let { settingsRepository.geminiTimeout = it }
    }
    fun setGeminiRedoPrediction(e: Boolean) { settingsRepository.geminiRedoPrediction = e }

    fun setSecurityPin(pin: String) {
        settingsRepository.securityPin = pin
    }

    fun clearSecurityPin() {
        securityManager.clearPin()
    }

    fun setSecurityPinTimeoutMinutes(minutes: Long) {
        settingsRepository.securityPinTimeoutMinutes = minutes
    }

    fun setPinRequiredForDeletion(required: Boolean) {
        settingsRepository.isPinRequiredForDeletion = required
    }

    fun setBiometricEnabled(enabled: Boolean) {
        settingsRepository.isBiometricEnabled = enabled
    }

    fun setSecurityRequiredForEdit(required: Boolean) {
        settingsRepository.isSecurityRequiredForEdit = required
    }

    fun setSecurityRequiredForSettings(required: Boolean) {
        settingsRepository.isSecurityRequiredForSettings = required
    }

    fun setStartupBehavior(behavior: String) {
        settingsRepository.startupBehavior = behavior
    }

    fun lock() {
        securityManager.lock()
    }

    val weatherCacheTimeout = settingsRepository.weatherCacheTimeoutFlow
    fun setWeatherCacheTimeoutInput(input: String) {
        input.toLongOrNull()?.let { settingsRepository.weatherCacheTimeout = it }
    }

    val activeBookId: String
        get() = settingsRepository.activeBookId

    override fun onCleared() {
        super.onCleared()
        val keep = mutableSetOf<String>()
        settingsRepository.ttsAudioDeviceAddress?.split("|")?.lastOrNull()?.let { keep.add(it) }
        settingsRepository.cuesAudioDeviceAddress?.split("|")?.lastOrNull()?.let { keep.add(it) }
        settingsRepository.cleanupDeviceCache(keep)
    }

    suspend fun exportLocalBackup(): String {
        return importExportManager.exportBookToJson(activeBookId)
    }

    suspend fun importLocalBackup(json: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val importBookId = importExportManager.extractBookIdFromJson(json)
        
        if (importBookId != activeBookId) {
            onError("Fehler: Buch-IDs stimmen nicht überein. Dieses Backup gehört zu einem anderen Buch.")
            return
        }

        val result = importExportManager.importFromJson(
            jsonString = json,
            bookId = activeBookId,
            regenerateIds = false, // Preserve IDs for matching book
            restoreSyncSettings = false
        )
        result.onSuccess { onSuccess() }
            .onFailure { e -> onError("Fehler beim Import: ${e.message}") }
    }

    suspend fun importGlobalManualBackup(json: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        val result = importExportManager.importCloudBackup(json, null)
        result.onSuccess { bookId -> onSuccess(bookId) }
            .onFailure { e -> onError("Fehler beim globalen Import: ${e.message}") }
    }
}

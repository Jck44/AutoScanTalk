package com.andreas_kratzer.ghosttalk.ui.settings

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andreas_kratzer.ghosttalk.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.domain.GetPagesUseCase
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.ui.settings.delegates.CloudSyncSettingsDelegate
import com.andreas_kratzer.ghosttalk.ui.settings.delegates.ExperimentalSettingsDelegate
import com.andreas_kratzer.ghosttalk.ui.settings.delegates.GenAiSettingsDelegate
import com.andreas_kratzer.ghosttalk.ui.settings.delegates.ScanningSettingsDelegate
import com.andreas_kratzer.ghosttalk.ui.settings.delegates.TtsSettingsDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val settingsRepository: SettingsRepository,
    private val buttonUsageRepository: ButtonUsageRepository,
    private val getPagesUseCase: GetPagesUseCase,
    val ttsDelegate: TtsSettingsDelegate,
    val scanningDelegate: ScanningSettingsDelegate,
    val cloudSyncDelegate: CloudSyncSettingsDelegate,
    val genAiDelegate: GenAiSettingsDelegate,
    val experimentalDelegate: ExperimentalSettingsDelegate
) : AndroidViewModel(application) {

    private val _activeBookId = MutableStateFlow(settingsRepository.activeBookId)
    val allPages: StateFlow<List<Page>> = getPagesUseCase.execute(_activeBookId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // --- Observable State from Repository ---
    val availableLanguages = ttsDelegate.availableLanguages
    val availableVoices = ttsDelegate.availableVoices
    val availableAudioDevices = ttsDelegate.availableAudioDevices
    
    val selectedLanguageTag = settingsRepository.ttsLanguageFlow
    val selectedVoiceName = settingsRepository.ttsVoiceNameFlow
    val ttsVolumeMultiplier = settingsRepository.ttsVolumeMultiplierFlow
    val cuesVolumeMultiplier = settingsRepository.cuesVolumeMultiplierFlow
    
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
    val syncIntervalMinutes = settingsRepository.syncIntervalMinutesFlow
    val syncMode = settingsRepository.syncModeFlow
    val lastSuccessfulSyncTime = settingsRepository.lastSuccessfulSyncTimeFlow
    val isSyncing = cloudSyncDelegate.isSyncing
    val userEmail = cloudSyncDelegate.userEmail
    
    val isGeminiEnabled = settingsRepository.isGeminiEnabledFlow
    val useLocalGenerativeAi = settingsRepository.useLocalGenerativeAiFlow
    val geminiToolStatus = genAiDelegate.geminiToolStatus
    
    val isSmartPredictionEnabled = settingsRepository.isSmartPredictionEnabledFlow
    
    val isNotificationReadingEnabled = settingsRepository.isNotificationReadingEnabledFlow
    val monitoredNotificationApps = settingsRepository.monitoredNotificationAppsFlow
    
    val selectedAppLanguage = settingsRepository.appLanguageFlow
    val themeMode = settingsRepository.themeModeFlow
    val experimentalManualSorting = settingsRepository.experimentalManualSortingFlow

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
    }

    // --- Delegation Methods (UI Actions) ---
    fun refresh() {
        _activeBookId.value = settingsRepository.activeBookId
        ttsDelegate.loadAvailableLanguages()
        ttsDelegate.loadAvailableVoices()
        ttsDelegate.loadAvailableAudioDevices()
        genAiDelegate.updateGeminiToolStatus()
    }

    fun setTtsLanguage(tag: String) = ttsDelegate.setTtsLanguage(tag)
    fun setTtsVoice(name: String?) = ttsDelegate.setTtsVoice(name)
    fun setTtsVolumeMultiplier(m: Float) = ttsDelegate.setTtsVolumeMultiplier(m)
    fun setCuesVolumeMultiplier(m: Float) = ttsDelegate.setCuesVolumeMultiplier(m)
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
    fun signOut() = cloudSyncDelegate.signOut(viewModelScope)
    fun setCloudSyncEnabled(e: Boolean) = cloudSyncDelegate.setCloudSyncEnabled(e)
    fun syncNow() = cloudSyncDelegate.performManualSync(com.andreas_kratzer.ghosttalk.domain.SyncMode.TWO_WAY, viewModelScope)
    fun backupNow() = cloudSyncDelegate.performManualSync(com.andreas_kratzer.ghosttalk.domain.SyncMode.BACKUP_ONLY, viewModelScope)
    fun restoreNow() = cloudSyncDelegate.performManualSync(com.andreas_kratzer.ghosttalk.domain.SyncMode.RESTORE_ONLY, viewModelScope)
    fun setSyncMode(m: String) { settingsRepository.syncMode = m }
    fun setSyncIntervalMinutesInput(i: String) {
        val parsed = i.toLongOrNull()?.coerceAtLeast(15L) ?: 15L
        settingsRepository.syncIntervalMinutes = parsed
        if (settingsRepository.isCloudSyncEnabled) cloudSyncDelegate.scheduleCloudSync()
    }

    fun setGeminiEnabled(e: Boolean) {
        settingsRepository.isGeminiEnabled = e
        genAiDelegate.updateGeminiToolStatus()
    }
    fun activateGemini(ctx: Context) = genAiDelegate.activateGemini(ctx, viewModelScope)
    fun testGeminiNano(ctx: Context) = genAiDelegate.testGeminiNano(ctx, viewModelScope)
    fun setUseLocalGenerativeAi(e: Boolean, ctx: Context? = null) {
        settingsRepository.useLocalGenerativeAi = e
        if (e && ctx != null) testGeminiNano(ctx)
    }

    fun setPersistActionLogs(e: Boolean) { settingsRepository.persistActionLogs = e }
    fun setSwitchActivationKey(k: String) { settingsRepository.switchActivationKey = k }
    fun setVolumeKeysActivate(e: Boolean) { settingsRepository.volumeKeysActivate = e }
    fun setShowTestButtons(e: Boolean) { settingsRepository.showTestButtons = e }
    fun setShowPageIdInLog(e: Boolean) { settingsRepository.showPageIdInLog = e }
    fun setExperimentalManualSorting(e: Boolean) = experimentalDelegate.setExperimentalManualSorting(e)
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

    val activeBookId: String
        get() = settingsRepository.activeBookId

    override fun onCleared() {
        super.onCleared()
        ttsDelegate.shutdown()
        val keep = mutableSetOf<String>()
        settingsRepository.ttsAudioDeviceAddress?.split("|")?.lastOrNull()?.let { keep.add(it) }
        settingsRepository.cuesAudioDeviceAddress?.split("|")?.lastOrNull()?.let { keep.add(it) }
        settingsRepository.cleanupDeviceCache(keep)
    }
}

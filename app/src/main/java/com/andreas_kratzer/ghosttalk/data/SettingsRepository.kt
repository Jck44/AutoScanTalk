package com.andreas_kratzer.ghosttalk.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.andreas_kratzer.ghosttalk.core.KeyEventSettings
import com.andreas_kratzer.ghosttalk.core.SecuritySettings
import com.andreas_kratzer.ghosttalk.core.actions.ControlDeviceSettings
import com.andreas_kratzer.ghosttalk.core.actions.SpeechSettings
import com.andreas_kratzer.ghosttalk.core.audio.AudioSettings
import com.andreas_kratzer.ghosttalk.data.settings.AdvancedSettingsRepository
import com.andreas_kratzer.ghosttalk.data.settings.CloudSettingsRepository
import com.andreas_kratzer.ghosttalk.data.settings.GenAiSettingsRepository
import com.andreas_kratzer.ghosttalk.data.settings.GeneralSettingsRepository
import com.andreas_kratzer.ghosttalk.data.settings.NotificationSettingsRepository
import com.andreas_kratzer.ghosttalk.data.settings.ScanningSettingsRepository
import com.andreas_kratzer.ghosttalk.data.settings.SecuritySettingsRepository
import com.andreas_kratzer.ghosttalk.data.settings.SettingsConstants
import com.andreas_kratzer.ghosttalk.data.settings.UserSettingsRepository
import com.andreas_kratzer.ghosttalk.data.settings.VoiceSettingsRepository
import com.andreas_kratzer.ghosttalk.core.settings.GenAiSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@SuppressLint("CommitPrefEdits", "ApplySharedPref", "UseKtx")
class SettingsRepository(context: Context) : SecuritySettings, KeyEventSettings, AudioSettings, ControlDeviceSettings, SpeechSettings, GenAiSettings {

    private val prefs: SharedPreferences = context.getSharedPreferences(SettingsConstants.PREFS_NAME, Context.MODE_PRIVATE)

    private val _activeBookIdFlow = MutableStateFlow(prefs.getString(SettingsConstants.KEY_ACTIVE_BOOK_ID, "book-default") ?: "book-default")
    val activeBookIdFlow: StateFlow<String> = _activeBookIdFlow.asStateFlow()

    var activeBookId: String
        get() = _activeBookIdFlow.value
        set(value) {
            _activeBookIdFlow.value = value
            prefs.edit().putString(SettingsConstants.KEY_ACTIVE_BOOK_ID, value).apply()
            refreshFlows()
        }

    // ── Sub-repositories ──────────────────────────────────────────────────

    private val voiceSettings = VoiceSettingsRepository(prefs, activeBookIdFlow)
    private val scanningSettings = ScanningSettingsRepository(prefs, activeBookIdFlow)
    private val securitySettings = SecuritySettingsRepository(prefs, activeBookIdFlow)
    private val cloudSettings = CloudSettingsRepository(prefs, activeBookIdFlow)
    private val genAiSettings = GenAiSettingsRepository(prefs, activeBookIdFlow)
    private val generalSettings = GeneralSettingsRepository(prefs, activeBookIdFlow)
    private val notificationSettings = NotificationSettingsRepository(prefs, activeBookIdFlow)
    private val advancedSettings = AdvancedSettingsRepository(prefs, activeBookIdFlow)
    private val userSettings = UserSettingsRepository(prefs, activeBookIdFlow)

    init {
        cleanupLegacyBookPins()
        migrateLegacyPinToHash()
    }

    private fun migrateLegacyPinToHash() {
        val legacyPin = securityPin
        if (!legacyPin.isNullOrEmpty()) {
            // See legacy implementation
        }
    }

    private fun cleanupLegacyBookPins() {
        val allPrefs = prefs.all
        val editor = prefs.edit()
        var changed = false
        allPrefs.keys.forEach { key ->
            if (key.endsWith("_" + SettingsConstants.KEY_SECURITY_PIN) && key != SettingsConstants.KEY_SECURITY_PIN) {
                editor.remove(key)
                changed = true
            }
        }
        if (changed) editor.apply()
    }

    private fun refreshFlows() {
        voiceSettings.refresh()
        scanningSettings.refresh()
        securitySettings.refresh()
        cloudSettings.refresh()
        genAiSettings.refresh()
        generalSettings.refresh()
        notificationSettings.refresh()
        advancedSettings.refresh()
        userSettings.refresh()
    }

    // ── Public API: Flows ────────────────────────────────────────────────

    val ttsLanguageFlow: StateFlow<String?> get() = voiceSettings.ttsLanguageFlow
    val ttsVoiceNameFlow: StateFlow<String?> get() = voiceSettings.ttsVoiceNameFlow
    val autoStartScanningFlow: StateFlow<Boolean> get() = scanningSettings.autoStartScanningFlow
    val scanDelayFlow: StateFlow<Long> get() = scanningSettings.scanDelayFlow
    val resumeScanningFromStartFlow: StateFlow<Boolean> get() = scanningSettings.resumeScanningFromStartFlow
    val defaultStartPageIdFlow: StateFlow<String?> get() = generalSettings.defaultStartPageIdFlow
    val ttsAudioDeviceAddressFlow: StateFlow<String?> get() = voiceSettings.ttsAudioDeviceAddressFlow
    val cuesAudioDeviceAddressFlow: StateFlow<String?> get() = voiceSettings.cuesAudioDeviceAddressFlow
    val holdingTimeMillisFlow: StateFlow<Long> get() = scanningSettings.holdingTimeMillisFlow
    val persistActionLogsFlow: StateFlow<Boolean> get() = advancedSettings.persistActionLogsFlow
    val actionLogsStorageFlow: StateFlow<String?> get() = advancedSettings.actionLogsStorageFlow
    val switchActivationKeyFlow: StateFlow<String> get() = scanningSettings.switchActivationKeyFlow
    val volumeKeysActivateFlow: StateFlow<Boolean> get() = scanningSettings.volumeKeysActivateFlow
    val showTestButtonsFlow: StateFlow<Boolean> get() = advancedSettings.showTestButtonsFlow
    val defaultScanPatternFlow: StateFlow<String> get() = scanningSettings.defaultScanPatternFlow
    val themeModeFlow: StateFlow<String> get() = generalSettings.themeModeFlow
    val pageSortOrderFlow: StateFlow<String> get() = generalSettings.pageSortOrderFlow
    val templateSortOrderFlow: StateFlow<String> get() = generalSettings.templateSortOrderFlow
    val lastSuccessfulSyncTimeFlow: StateFlow<Long> get() = cloudSettings.lastSuccessfulSyncTimeFlow
    val bluetoothDelayFlow: StateFlow<Long> get() = scanningSettings.bluetoothDelayFlow
    val isCloudSyncEnabledFlow: StateFlow<Boolean> get() = cloudSettings.isCloudSyncEnabledFlow
    val syncIntervalMinutesFlow: StateFlow<Long> get() = cloudSettings.syncIntervalMinutesFlow
    val syncModeFlow: StateFlow<String> get() = cloudSettings.syncModeFlow
    val isNotificationReadingEnabledFlow: StateFlow<Boolean> get() = notificationSettings.isNotificationReadingEnabledFlow
    val monitoredNotificationAppsFlow: StateFlow<Set<String>> get() = notificationSettings.monitoredNotificationAppsFlow
    val appLanguageFlow: StateFlow<String?> get() = generalSettings.appLanguageFlow
    val keepScreenOnUserModeFlow: StateFlow<Boolean> get() = userSettings.keepScreenOnUserModeFlow
    val userModeScreenBehaviorFlow: StateFlow<String> get() = userSettings.userModeScreenBehaviorFlow
    val weatherCacheTimeoutFlow: StateFlow<Long> get() = advancedSettings.weatherCacheTimeoutFlow
    val securityPinFlow: StateFlow<String?> get() = securitySettings.securityPinFlow
    val securityPinHashFlow: StateFlow<String?> get() = securitySettings.securityPinHashFlow
    val securityPinSaltFlow: StateFlow<String?> get() = securitySettings.securityPinSaltFlow
    val securityPinTimeoutMinutesFlow: StateFlow<Long> get() = securitySettings.securityPinTimeoutMinutesFlow
    val isPinRequiredForDeletionFlow: StateFlow<Boolean> get() = securitySettings.isPinRequiredForDeletionFlow
    val isBiometricEnabledFlow: StateFlow<Boolean> get() = securitySettings.isBiometricEnabledFlow
    val isSecurityRequiredForEditFlow: StateFlow<Boolean> get() = securitySettings.isSecurityRequiredForEditFlow
    val isSecurityRequiredForSettingsFlow: StateFlow<Boolean> get() = securitySettings.isSecurityRequiredForSettingsFlow
    val startupBehaviorFlow: StateFlow<String> get() = generalSettings.startupBehaviorFlow
    val favoriteBookIdFlow: StateFlow<String?> get() = generalSettings.favoriteBookIdFlow

    // ── Public API: Properties ───────────────────────────────────────────

    var ttsLanguage: String?
        get() = voiceSettings.ttsLanguage
        set(value) { voiceSettings.ttsLanguage = value }

    var ttsVoiceName: String?
        get() = voiceSettings.ttsVoiceName
        set(value) { voiceSettings.ttsVoiceName = value }

    var autoStartScanning: Boolean
        get() = scanningSettings.autoStartScanning
        set(value) { scanningSettings.autoStartScanning = value }

    var scanDelayMillis: Long
        get() = scanningSettings.scanDelayMillis
        set(value) { scanningSettings.scanDelayMillis = value }

    var resumeScanningFromStart: Boolean
        get() = scanningSettings.resumeScanningFromStart
        set(value) { scanningSettings.resumeScanningFromStart = value }

    var defaultStartPageId: String?
        get() = generalSettings.defaultStartPageId
        set(value) { generalSettings.defaultStartPageId = value }

    override var ttsAudioDeviceAddress: String?
        get() = voiceSettings.ttsAudioDeviceAddress
        set(value) { voiceSettings.ttsAudioDeviceAddress = value }

    override var cuesAudioDeviceAddress: String?
        get() = voiceSettings.cuesAudioDeviceAddress
        set(value) { voiceSettings.cuesAudioDeviceAddress = value }

    var holdingTimeMillis: Long
        get() = scanningSettings.holdingTimeMillis
        set(value) { scanningSettings.holdingTimeMillis = value }

    var persistActionLogs: Boolean
        get() = advancedSettings.persistActionLogs
        set(value) { advancedSettings.persistActionLogs = value }

    var actionLogsStorage: String?
        get() = advancedSettings.actionLogsStorage
        set(value) { advancedSettings.actionLogsStorage = value }

    override var switchActivationKey: String
        get() = scanningSettings.switchActivationKey
        set(value) { scanningSettings.switchActivationKey = value }

    override var volumeKeysActivate: Boolean
        get() = scanningSettings.volumeKeysActivate
        set(value) { scanningSettings.volumeKeysActivate = value }

    var showTestButtons: Boolean
        get() = advancedSettings.showTestButtons
        set(value) { advancedSettings.showTestButtons = value }

    var defaultScanPattern: String
        get() = scanningSettings.defaultScanPattern
        set(value) { scanningSettings.defaultScanPattern = value }

    var themeMode: String
        get() = generalSettings.themeMode
        set(value) { generalSettings.themeMode = value }

    var pageSortOrder: String
        get() = generalSettings.pageSortOrder
        set(value) { generalSettings.pageSortOrder = value }

    var templateSortOrder: String
        get() = generalSettings.templateSortOrder
        set(value) { generalSettings.templateSortOrder = value }

    var lastSuccessfulSyncTime: Long
        get() = cloudSettings.lastSuccessfulSyncTime
        set(value) { cloudSettings.lastSuccessfulSyncTime = value }

    var smartPredictionDelayMillis: Long
        get() = advancedSettings.smartPredictionDelay
        set(value) { advancedSettings.smartPredictionDelay = value }

    override var bluetoothDelay: Long
        get() = scanningSettings.bluetoothDelay
        set(value) { scanningSettings.bluetoothDelay = value }

    var isCloudSyncEnabled: Boolean
        get() = cloudSettings.isCloudSyncEnabled
        set(value) { cloudSettings.isCloudSyncEnabled = value }

    var syncIntervalMinutes: Long
        get() = cloudSettings.syncIntervalMinutes
        set(value) { cloudSettings.syncIntervalMinutes = value }

    var syncMode: String
        get() = cloudSettings.syncMode
        set(value) { cloudSettings.syncMode = value }

    override var isGeminiEnabled: Boolean
        get() = genAiSettings.isGeminiEnabled
        set(value) { genAiSettings.isGeminiEnabled = value }
    override val isGeminiEnabledFlow: StateFlow<Boolean> get() = genAiSettings.isGeminiEnabledFlow

    override var isSmartPredictionEnabled: Boolean
        get() = advancedSettings.isSmartPredictionEnabled
        set(value) { advancedSettings.isSmartPredictionEnabled = value }
    override val isSmartPredictionEnabledFlow: StateFlow<Boolean> get() = advancedSettings.isSmartPredictionEnabledFlow

    override var useLocalGenerativeAi: Boolean
        get() = genAiSettings.useLocalGenerativeAi
        set(value) { genAiSettings.useLocalGenerativeAi = value }
    override val useLocalGenerativeAiFlow: StateFlow<Boolean> get() = genAiSettings.useLocalGenerativeAiFlow

    override var showPageIdInLog: Boolean
        get() = advancedSettings.showPageIdInLog
        set(value) { advancedSettings.showPageIdInLog = value }
    override val showPageIdInLogFlow: StateFlow<Boolean> get() = advancedSettings.showPageIdInLogFlow

    override var isNotificationReadingEnabled: Boolean
        get() = notificationSettings.isNotificationReadingEnabled
        set(value) { notificationSettings.isNotificationReadingEnabled = value }

    override var monitoredNotificationApps: Set<String>
        get() = notificationSettings.monitoredNotificationApps
        set(value) { notificationSettings.monitoredNotificationApps = value }

    var appLanguage: String?
        get() = generalSettings.appLanguage
        set(value) { generalSettings.appLanguage = value }
    
    var initialTemplatesCreated: Boolean
        get() = prefs.getBoolean(SettingsConstants.KEY_INITIAL_TEMPLATES_CREATED, false)
        set(value) { prefs.edit().putBoolean(SettingsConstants.KEY_INITIAL_TEMPLATES_CREATED, value).apply() }

    var keepScreenOnUserMode: Boolean
        get() = userSettings.keepScreenOnUserMode
        set(value) { userSettings.keepScreenOnUserMode = value }

    var userModeScreenBehavior: String
        get() = userSettings.userModeScreenBehavior
        set(value) { userSettings.userModeScreenBehavior = value }

    override var geminiTimeout: Long
        get() = genAiSettings.geminiTimeout
        set(value) { genAiSettings.geminiTimeout = value }
    override val geminiTimeoutFlow: StateFlow<Long> get() = genAiSettings.geminiTimeoutFlow

    override var geminiRedoPrediction: Boolean
        get() = genAiSettings.geminiRedoPrediction
        set(value) { genAiSettings.geminiRedoPrediction = value }
    override val geminiRedoPredictionFlow: StateFlow<Boolean> get() = genAiSettings.geminiRedoPredictionFlow

    var weatherCacheTimeout: Long
        get() = advancedSettings.weatherCacheTimeout
        set(value) { advancedSettings.weatherCacheTimeout = value }

    override var securityPin: String?
        get() = securitySettings.securityPin
        set(value) { securitySettings.securityPin = value }

    override var securityPinHash: String?
        get() = securitySettings.securityPinHash
        set(value) { securitySettings.securityPinHash = value }

    override var securityPinSalt: String?
        get() = securitySettings.securityPinSalt
        set(value) { securitySettings.securityPinSalt = value }

    override var securityPinTimeoutMinutes: Long
        get() = securitySettings.securityPinTimeoutMinutes
        set(value) { securitySettings.securityPinTimeoutMinutes = value }

    override var isPinRequiredForDeletion: Boolean
        get() = securitySettings.isPinRequiredForDeletion
        set(value) { securitySettings.isPinRequiredForDeletion = value }

    var isBiometricEnabled: Boolean
        get() = securitySettings.isBiometricEnabled
        set(value) { securitySettings.isBiometricEnabled = value }

    override var isSecurityRequiredForEdit: Boolean
        get() = securitySettings.isSecurityRequiredForEdit
        set(value) { securitySettings.isSecurityRequiredForEdit = value }

    override var isSecurityRequiredForSettings: Boolean
        get() = securitySettings.isSecurityRequiredForSettings
        set(value) { securitySettings.isSecurityRequiredForSettings = value }

    var startupBehavior: String
        get() = generalSettings.startupBehavior
        set(value) { generalSettings.startupBehavior = value }

    var favoriteBookId: String?
        get() = generalSettings.favoriteBookId
        set(value) { generalSettings.favoriteBookId = value }

    // ── Book-specific helpers ─────────────────────────────────────────────

    fun getSecurityPinForBook(bookId: String): String? {
        val scopedKey = "${bookId}_${SettingsConstants.KEY_SECURITY_PIN}"
        if (prefs.contains(scopedKey)) {
            return prefs.getString(scopedKey, "")
        }
        return prefs.getString(SettingsConstants.KEY_SECURITY_PIN, "")
    }

    fun isPinRequiredForDeletionForBook(bookId: String): Boolean {
        // This is now a global setting
        return isPinRequiredForDeletion
    }

    // ── Device name cache ────────────────────────────────────────────────

    fun getDeviceName(persistentId: String): String? {
        return prefs.getString("device_name_$persistentId", null)
    }

    fun saveDeviceName(persistentId: String, name: String) {
        prefs.edit().putString("device_name_$persistentId", name).apply()
    }

    fun cleanupDeviceCache(keepPersistentIds: Set<String>) {
        val allPrefs = prefs.all
        val editor = prefs.edit()
        allPrefs.keys.forEach { key ->
            if (key.startsWith("device_name_")) {
                val persistentId = key.removePrefix("device_name_")
                if (!keepPersistentIds.contains(persistentId)) {
                    editor.remove(key)
                }
            }
        }
        editor.apply()
    }
}

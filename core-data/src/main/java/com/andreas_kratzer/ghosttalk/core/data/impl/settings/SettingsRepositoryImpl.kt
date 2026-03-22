package com.andreas_kratzer.ghosttalk.core.data.impl.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@SuppressLint("CommitPrefEdits", "ApplySharedPref", "UseKtx")
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val bookRepository: BookRepository,
    @ApplicationScope private val scope: CoroutineScope
) : SettingsRepository {

    private val prefs: SharedPreferences = context.getSharedPreferences(SettingsConstants.PREFS_NAME, Context.MODE_PRIVATE)

    private val _activeBookIdFlow = MutableStateFlow(prefs.getString(SettingsConstants.KEY_ACTIVE_BOOK_ID, "book-default") ?: "book-default")
    override val activeBookIdFlow: StateFlow<String?> = _activeBookIdFlow.asStateFlow()

    override var activeBookId: String
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
    private val smartHomeSettings = SmartHomeSettingsRepository(prefs, activeBookIdFlow)
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
        smartHomeSettings.refresh()
        genAiSettings.refresh()
        generalSettings.refresh()
        notificationSettings.refresh()
        advancedSettings.refresh()
        userSettings.refresh()
    }

    // ── Public API: Flows ────────────────────────────────────────────────

    override val ttsLanguageFlow: StateFlow<String?> get() = voiceSettings.ttsLanguageFlow
    override val ttsVoiceNameFlow: StateFlow<String?> get() = voiceSettings.ttsVoiceNameFlow
    
    // --- ScanningSettings ---
    override val autoStartScanningFlow: StateFlow<Boolean> get() = scanningSettings.autoStartScanningFlow
    override val scanDelayFlow: StateFlow<Long> get() = scanningSettings.scanDelayFlow
    override val resumeScanningFromStartFlow: StateFlow<Boolean> get() = scanningSettings.resumeScanningFromStartFlow
    override val defaultScanPatternFlow: StateFlow<String> get() = scanningSettings.defaultScanPatternFlow
    override val limitScanCyclesFlow: StateFlow<Boolean> get() = scanningSettings.limitScanCyclesFlow
    override val scanCycleLimitFlow: StateFlow<Int> get() = scanningSettings.scanCycleLimitFlow
    
    

    // --- FeatureSettings ---
    override val appLanguageFlow: StateFlow<String?> get() = generalSettings.appLanguageFlow
    
    // --- GeneralSettings ---
    override val templateSortOrderFlow: StateFlow<String> get() = generalSettings.templateSortOrderFlow
    override val pageSortOrderFlow: StateFlow<String> get() = generalSettings.pageSortOrderFlow
    override val themeModeFlow: StateFlow<String> get() = generalSettings.themeModeFlow
    override val defaultStartPageIdFlow: StateFlow<String?> get() = generalSettings.defaultStartPageIdFlow
    override val startupBehaviorFlow: StateFlow<String> get() = generalSettings.startupBehaviorFlow
    override val favoriteBookIdFlow: StateFlow<String?> get() = generalSettings.favoriteBookIdFlow
    override val forceSoftKeyboardFlow: StateFlow<Boolean> get() = generalSettings.forceSoftKeyboardFlow

    // --- UserSettings ---
    override val keepScreenOnUserModeFlow: StateFlow<Boolean> get() = userSettings.keepScreenOnUserModeFlow
    override val userModeScreenBehaviorFlow: StateFlow<String> get() = userSettings.userModeScreenBehaviorFlow

    // --- AdvancedSettings ---
    override val persistActionLogsFlow: StateFlow<Boolean> get() = advancedSettings.persistActionLogsFlow
    override val actionLogsStorageFlow: StateFlow<String?> get() = advancedSettings.actionLogsStorageFlow
    override val showTestButtonsFlow: StateFlow<Boolean> get() = advancedSettings.showTestButtonsFlow
    override val weatherCacheTimeoutFlow: StateFlow<Long> get() = advancedSettings.weatherCacheTimeoutFlow
    override val smartPredictionDelayFlow: StateFlow<Long> get() = advancedSettings.smartPredictionDelayFlow
    override val actionLogLimitFlow: StateFlow<Int> get() = advancedSettings.actionLogLimitFlow
    override val logIgnoredActionsFlow: StateFlow<Boolean> get() = advancedSettings.logIgnoredActionsFlow
    override val logStopActionsFlow: StateFlow<Boolean> get() = advancedSettings.logStopActionsFlow
    
    // --- NotificationSettings ---
    override val isNotificationReadingEnabledFlow: StateFlow<Boolean> get() = notificationSettings.isNotificationReadingEnabledFlow
    override val monitoredNotificationAppsFlow: StateFlow<Set<String>> get() = notificationSettings.monitoredNotificationAppsFlow
    
    // --- Other Flows ---
    override val ttsAudioDeviceAddressFlow: StateFlow<String?> get() = voiceSettings.ttsAudioDeviceAddressFlow
    override val holdingTimeMillisFlow: StateFlow<Long> get() = scanningSettings.holdingTimeMillisFlow
    override val switchActivationKeyFlow: StateFlow<String> get() = scanningSettings.switchActivationKeyFlow
    override val volumeKeysActivateFlow: StateFlow<Boolean> get() = scanningSettings.volumeKeysActivateFlow
    override val bluetoothDelayFlow: StateFlow<Long> get() = scanningSettings.bluetoothDelayFlow
    override val isCloudSyncEnabledFlow: StateFlow<Boolean> get() = cloudSettings.isCloudSyncEnabledFlow
    override val syncIntervalMinutesFlow: StateFlow<Long> get() = cloudSettings.syncIntervalMinutesFlow
    override val syncModeFlow: StateFlow<String> get() = cloudSettings.syncModeFlow
    override val lastSuccessfulSyncTimeFlow: StateFlow<Long> get() = cloudSettings.lastSuccessfulSyncTimeFlow
    override val googleHomeProjectIdFlow: StateFlow<String> get() = smartHomeSettings.googleHomeProjectIdFlow
    override val hueBridgeIpFlow: StateFlow<String> get() = smartHomeSettings.hueBridgeIpFlow
    override val hueUsernameFlow: StateFlow<String> get() = smartHomeSettings.hueUsernameFlow
    override val hueAccessTokenFlow: StateFlow<String> get() = smartHomeSettings.hueAccessTokenFlow
    override val hueRefreshTokenFlow: StateFlow<String> get() = smartHomeSettings.hueRefreshTokenFlow
    override val cuesAudioDeviceAddressFlow: StateFlow<String?> get() = voiceSettings.cuesAudioDeviceAddressFlow
    override val securityPinFlow: StateFlow<String?> get() = securitySettings.securityPinFlow
    override val securityPinHashFlow: StateFlow<String?> get() = securitySettings.securityPinHashFlow
    override val securityPinSaltFlow: StateFlow<String?> get() = securitySettings.securityPinSaltFlow
    override val securityPinTimeoutMinutesFlow: StateFlow<Long> get() = securitySettings.securityPinTimeoutMinutesFlow
    override val isPinRequiredForDeletionFlow: StateFlow<Boolean> get() = securitySettings.isPinRequiredForDeletionFlow
    override val isBiometricEnabledFlow: StateFlow<Boolean> get() = securitySettings.isBiometricEnabledFlow
    override val isSecurityRequiredForEditFlow: StateFlow<Boolean> get() = securitySettings.isSecurityRequiredForEditFlow
    override val isSecurityRequiredForSettingsFlow: StateFlow<Boolean> get() = securitySettings.isSecurityRequiredForSettingsFlow

    // ── Public API: Properties ───────────────────────────────────────────

    override var ttsLanguage: String?
        get() = voiceSettings.ttsLanguage
        set(value) { voiceSettings.ttsLanguage = value }

    override var ttsVoiceName: String?
        get() = voiceSettings.ttsVoiceName
        set(value) { voiceSettings.ttsVoiceName = value }

    override var autoStartScanning: Boolean
        get() = scanningSettings.autoStartScanning
        set(value) { scanningSettings.autoStartScanning = value }

    override var scanDelayMillis: Long
        get() = scanningSettings.scanDelayMillis
        set(value) { scanningSettings.scanDelayMillis = value }

    override var resumeScanningFromStart: Boolean
        get() = scanningSettings.resumeScanningFromStart
        set(value) { scanningSettings.resumeScanningFromStart = value }

    override var limitScanCycles: Boolean
        get() = scanningSettings.limitScanCycles
        set(value) {
            scanningSettings.limitScanCycles = value
            syncBookSettings()
        }

    override var scanCycleLimit: Int
        get() = scanningSettings.scanCycleLimit
        set(value) {
            scanningSettings.scanCycleLimit = value
            syncBookSettings()
        }

    override var defaultStartPageId: String?
        get() = generalSettings.defaultStartPageId
        set(value) { generalSettings.defaultStartPageId = value }

    override var ttsAudioDeviceAddress: String?
        get() = voiceSettings.ttsAudioDeviceAddress
        set(value) { voiceSettings.ttsAudioDeviceAddress = value }

    override var cuesAudioDeviceAddress: String?
        get() = voiceSettings.cuesAudioDeviceAddress
        set(value) { voiceSettings.cuesAudioDeviceAddress = value }

    override var holdingTimeMillis: Long
        get() = scanningSettings.holdingTimeMillis
        set(value) { scanningSettings.holdingTimeMillis = value }

    override var persistActionLogs: Boolean
        get() = advancedSettings.persistActionLogs
        set(value) { advancedSettings.persistActionLogs = value }

    override var actionLogsStorage: String?
        get() = advancedSettings.actionLogsStorage
        set(value) { advancedSettings.actionLogsStorage = value }

    override var switchActivationKey: String
        get() = scanningSettings.switchActivationKey
        set(value) { scanningSettings.switchActivationKey = value }

    override var volumeKeysActivate: Boolean
        get() = scanningSettings.volumeKeysActivate
        set(value) { scanningSettings.volumeKeysActivate = value }

    override var showTestButtons: Boolean
        get() = advancedSettings.showTestButtons
        set(value) { advancedSettings.showTestButtons = value }

    override var defaultScanPattern: String
        get() = scanningSettings.defaultScanPattern
        set(value) { scanningSettings.defaultScanPattern = value }

    override var themeMode: String
        get() = generalSettings.themeMode
        set(value) { generalSettings.themeMode = value }

    override var pageSortOrder: String
        get() = generalSettings.pageSortOrder
        set(value) { generalSettings.pageSortOrder = value }

    override var templateSortOrder: String
        get() = generalSettings.templateSortOrder
        set(value) { generalSettings.templateSortOrder = value }

    override var lastSuccessfulSyncTime: Long
        get() = cloudSettings.lastSuccessfulSyncTime
        set(value) { cloudSettings.lastSuccessfulSyncTime = value }

    override var smartPredictionDelay: Long
        get() = advancedSettings.smartPredictionDelay
        set(value) { advancedSettings.smartPredictionDelay = value }

    override var bluetoothDelay: Long
        get() = scanningSettings.bluetoothDelay
        set(value) { scanningSettings.bluetoothDelay = value }

    override var isCloudSyncEnabled: Boolean
        get() = cloudSettings.isCloudSyncEnabled
        set(value) { cloudSettings.isCloudSyncEnabled = value }

    override var syncIntervalMinutes: Long
        get() = cloudSettings.syncIntervalMinutes
        set(value) { cloudSettings.syncIntervalMinutes = value }

    override var syncMode: String
        get() = cloudSettings.syncMode
        set(value) { cloudSettings.syncMode = value }

    override var googleHomeProjectId: String
        get() = smartHomeSettings.googleHomeProjectId
        set(value) { smartHomeSettings.googleHomeProjectId = value }

    override var hueBridgeIp: String
        get() = smartHomeSettings.hueBridgeIp
        set(value) { smartHomeSettings.hueBridgeIp = value }

    override var hueUsername: String
        get() = smartHomeSettings.hueUsername
        set(value) { smartHomeSettings.hueUsername = value }

    override var hueAccessToken: String
        get() = smartHomeSettings.hueAccessToken
        set(value) { smartHomeSettings.hueAccessToken = value }

    override var hueRefreshToken: String
        get() = smartHomeSettings.hueRefreshToken
        set(value) { smartHomeSettings.hueRefreshToken = value }

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

    override var appLanguage: String?
        get() = generalSettings.appLanguage
        set(value) { generalSettings.appLanguage = value }
    
    override var initialTemplatesCreated: Boolean
        get() = prefs.getBoolean(SettingsConstants.KEY_INITIAL_TEMPLATES_CREATED, false)
        set(value) { prefs.edit().putBoolean(SettingsConstants.KEY_INITIAL_TEMPLATES_CREATED, value).apply() }

    override var keepScreenOnUserMode: Boolean
        get() = userSettings.keepScreenOnUserMode
        set(value) { userSettings.keepScreenOnUserMode = value }

    override var userModeScreenBehavior: String
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

    override var weatherCacheTimeout: Long
        get() = advancedSettings.weatherCacheTimeout
        set(value) { advancedSettings.weatherCacheTimeout = value }

    override var actionLogLimit: Int
        get() = advancedSettings.actionLogLimit
        set(value) {
            advancedSettings.actionLogLimit = value
            syncBookSettings()
        }

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

    override var isBiometricEnabled: Boolean
        get() = securitySettings.isBiometricEnabled
        set(value) { securitySettings.isBiometricEnabled = value }

    override var isSecurityRequiredForEdit: Boolean
        get() = securitySettings.isSecurityRequiredForEdit
        set(value) { securitySettings.isSecurityRequiredForEdit = value }

    override var isSecurityRequiredForSettings: Boolean
        get() = securitySettings.isSecurityRequiredForSettings
        set(value) { securitySettings.isSecurityRequiredForSettings = value }

    override var startupBehavior: String
        get() = generalSettings.startupBehavior
        set(value) { generalSettings.startupBehavior = value }

    override var favoriteBookId: String?
        get() = generalSettings.favoriteBookId
        set(value) { generalSettings.favoriteBookId = value }

    override var forceSoftKeyboard: Boolean
        get() = generalSettings.forceSoftKeyboard
        set(value) { generalSettings.forceSoftKeyboard = value }

    override var logIgnoredActions: Boolean
        get() = advancedSettings.logIgnoredActions
        set(value) {
            advancedSettings.logIgnoredActions = value
            syncBookSettings()
        }

    override var logStopActions: Boolean
        get() = advancedSettings.logStopActions
        set(value) {
            advancedSettings.logStopActions = value
            syncBookSettings()
        }

    // ── Book-specific helpers ─────────────────────────────────────────────

    override fun getSecurityPinForBook(bookId: String): String? {
        val scopedKey = "${bookId}_${SettingsConstants.KEY_SECURITY_PIN}"
        if (prefs.contains(scopedKey)) {
            return prefs.getString(scopedKey, "")
        }
        return prefs.getString(SettingsConstants.KEY_SECURITY_PIN, "")
    }

    override fun isPinRequiredForDeletionForBook(bookId: String): Boolean {
        // This is now a global setting
        return isPinRequiredForDeletion
    }

    // ── Device name cache ────────────────────────────────────────────────

    override fun getDeviceName(persistentId: String): String? {
        return prefs.getString("device_name_$persistentId", null)
    }

    override fun saveDeviceName(persistentId: String, name: String) {
        prefs.edit().putString("device_name_$persistentId", name).apply()
    }

    override fun cleanupDeviceCache(keepPersistentIds: Set<String>) {
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

    override fun getCachedDevices(): Map<String, String> {
        val allPrefs = prefs.all
        val cachedDevices = mutableMapOf<String, String>()
        allPrefs.forEach { (key, value) ->
            if (key.startsWith("device_name_") && value is String) {
                val persistentId = key.removePrefix("device_name_")
                cachedDevices[persistentId] = value
            }
        }
        return cachedDevices
    }

    private fun syncBookSettings() {
        scope.launch {
            val bookId = activeBookId
            val book = bookRepository.getBookById(bookId)
            if (book != null) {
                val updatedBook = book.copy(
                    limitScanCycles = limitScanCycles,
                    scanCycleLimit = scanCycleLimit,
                    actionLogLimit = actionLogLimit,
                    logIgnoredActions = logIgnoredActions,
                    logStopActions = logStopActions
                )
                if (updatedBook != book) {
                    bookRepository.updateBook(updatedBook)
                }
            }
        }
    }

    override fun resetToDefaults() {
        prefs.edit().clear().apply()
        _activeBookIdFlow.value = "book-default"
        refreshFlows()
    }
}

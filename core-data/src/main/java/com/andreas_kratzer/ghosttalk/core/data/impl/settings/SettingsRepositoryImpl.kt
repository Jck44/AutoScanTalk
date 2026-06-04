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
    @param:ApplicationScope private val scope: CoroutineScope
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
    private val callSettings = CallSettingsRepository(prefs, activeBookIdFlow)

    init {
        cleanupLegacyBookPins()
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
        callSettings.refresh()
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
    override val staticRowEnabledFlow: StateFlow<Boolean> get() = scanningSettings.staticRowEnabledFlow
    override val staticRowScanPatternFlow: StateFlow<String> get() = scanningSettings.staticRowScanPatternFlow
    
    

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
    override val syncLogsStorageFlow: StateFlow<String?> get() = generalSettings.syncLogsStorageFlow
    override val isSetupCompletedFlow: StateFlow<Boolean> get() = generalSettings.isSetupCompletedFlow

    // --- UserSettings ---
    override val keepScreenOnUserModeFlow: StateFlow<Boolean> get() = userSettings.keepScreenOnUserModeFlow
    override val userModeScreenBehaviorFlow: StateFlow<String> get() = userSettings.userModeScreenBehaviorFlow
    override val statsRetentionDaysFlow: StateFlow<Int> get() = userSettings.statsRetentionDaysFlow
    override val statsAggregationHoursFlow: StateFlow<Int> get() = userSettings.statsAggregationHoursFlow
    override val onlyRecordHardwareStatsFlow: StateFlow<Boolean> get() = userSettings.onlyRecordHardwareStatsFlow

    // --- AdvancedSettings ---
    override val persistActionLogsFlow: StateFlow<Boolean> get() = advancedSettings.persistActionLogsFlow
    override val actionLogsStorageFlow: StateFlow<String?> get() = advancedSettings.actionLogsStorageFlow
    override val showTestButtonsFlow: StateFlow<Boolean> get() = advancedSettings.showTestButtonsFlow
    override val weatherCacheTimeoutFlow: StateFlow<Long> get() = advancedSettings.weatherCacheTimeoutFlow
    override val smartPredictionDelayFlow: StateFlow<Long> get() = advancedSettings.smartPredictionDelayFlow
    override val actionLogLimitFlow: StateFlow<Int> get() = advancedSettings.actionLogLimitFlow
    override val logIgnoredActionsFlow: StateFlow<Boolean> get() = advancedSettings.logIgnoredActionsFlow
    override val logStopActionsFlow: StateFlow<Boolean> get() = advancedSettings.logStopActionsFlow
    override val backgroundLocationEnabledFlow: StateFlow<Boolean> get() = advancedSettings.backgroundLocationEnabledFlow
    override val backgroundLocationIntervalFlow: StateFlow<Long> get() = advancedSettings.backgroundLocationIntervalFlow
    override val backgroundWeatherEnabledFlow: StateFlow<Boolean> get() = advancedSettings.backgroundWeatherEnabledFlow
    override val backgroundWeatherIntervalFlow: StateFlow<Long> get() = advancedSettings.backgroundWeatherIntervalFlow
    
    // --- NotificationSettings ---
    override val isNotificationReadingEnabledFlow: StateFlow<Boolean> get() = notificationSettings.isNotificationReadingEnabledFlow
    override val monitoredNotificationAppsFlow: StateFlow<Set<String>> get() = notificationSettings.monitoredNotificationAppsFlow
    
    // --- Other Flows ---
    override val ttsAudioDeviceAddressFlow: StateFlow<String?> get() = voiceSettings.ttsAudioDeviceAddressFlow
    override val recordingAudioSourceFlow: StateFlow<Int> get() = voiceSettings.recordingAudioSourceFlow
    override val holdingTimeMillisFlow: StateFlow<Long> get() = scanningSettings.holdingTimeMillisFlow
    override val switchActivationKeyFlow: StateFlow<String> get() = scanningSettings.switchActivationKeyFlow
    override val volumeKeysActivateFlow: StateFlow<Boolean> get() = scanningSettings.volumeKeysActivateFlow
    override val bluetoothDelayFlow: StateFlow<Long> get() = scanningSettings.bluetoothDelayFlow
    override val blockVolumeKeysFlow: StateFlow<Boolean> get() = scanningSettings.blockVolumeKeysFlow
    override val speakerVolumeFlow: StateFlow<Int> get() = scanningSettings.speakerVolumeFlow
    override val headphoneVolumeFlow: StateFlow<Int> get() = scanningSettings.headphoneVolumeFlow
    override val isCloudSyncEnabledFlow: StateFlow<Boolean> get() = cloudSettings.isCloudSyncEnabledFlow
    override val lateClickThresholdFlow: StateFlow<Long> get() = scanningSettings.lateClickThresholdFlow

    override val syncIntervalMinutesFlow: StateFlow<Long> get() = cloudSettings.syncIntervalMinutesFlow
    override val syncModeBookFlow: StateFlow<String> get() = cloudSettings.syncModeBookFlow
    override val syncModeTtsFlow: StateFlow<String> get() = cloudSettings.syncModeTtsFlow
    override val syncModeStatsFlow: StateFlow<String> get() = cloudSettings.syncModeStatsFlow
    override val lastSuccessfulSyncTimeFlow: StateFlow<Long> get() = cloudSettings.lastSuccessfulSyncTimeFlow
    override val syncModeLogsFlow: StateFlow<String> get() = cloudSettings.syncModeLogsFlow
    override val syncLogsIntervalHoursFlow: StateFlow<Long> get() = cloudSettings.syncLogsIntervalHoursFlow
    override val lastLogsSyncTimeFlow: StateFlow<Long> get() = cloudSettings.lastLogsSyncTimeFlow
    override val lastUploadedLogHashFlow: StateFlow<String?> get() = cloudSettings.lastUploadedLogHashFlow
    override val hueBridgeIpFlow: StateFlow<String> get() = smartHomeSettings.hueBridgeIpFlow
    override val hueUsernameFlow: StateFlow<String> get() = smartHomeSettings.hueUsernameFlow
    override val hueBridgeFingerprintFlow: StateFlow<String> get() = smartHomeSettings.hueBridgeFingerprintFlow
    override val hueCachedDevicesFlow: StateFlow<String> get() = smartHomeSettings.hueCachedDevicesFlow
    override val cuesAudioDeviceAddressFlow: StateFlow<String?> get() = voiceSettings.cuesAudioDeviceAddressFlow
    override val securityPinFlow: StateFlow<String?> get() = securitySettings.securityPinFlow
    override val securityPinHashFlow: StateFlow<String?> get() = securitySettings.securityPinHashFlow
    override val securityPinSaltFlow: StateFlow<String?> get() = securitySettings.securityPinSaltFlow
    override val securityPinTimeoutMinutesFlow: StateFlow<Long> get() = securitySettings.securityPinTimeoutMinutesFlow
    override val isPinRequiredForDeletionFlow: StateFlow<Boolean> get() = securitySettings.isPinRequiredForDeletionFlow
    override val isBiometricEnabledFlow: StateFlow<Boolean> get() = securitySettings.isBiometricEnabledFlow
    override val isSecurityRequiredForEditFlow: StateFlow<Boolean> get() = securitySettings.isSecurityRequiredForEditFlow
    override val isSecurityRequiredForSettingsFlow: StateFlow<Boolean> get() = securitySettings.isSecurityRequiredForSettingsFlow
    override val isSecurityRequiredForAnalyticsFlow: StateFlow<Boolean> get() = securitySettings.isSecurityRequiredForAnalyticsFlow
    override val elevenLabsApiKeyFlow: StateFlow<String?> get() = cloudSettings.elevenLabsApiKeyFlow
    override val elevenLabsModelFlow: StateFlow<String> get() = cloudSettings.elevenLabsModelFlow
    override val elevenLabsStabilityFlow: StateFlow<Float> get() = cloudSettings.elevenLabsStabilityFlow
    override val elevenLabsSimilarityBoostFlow: StateFlow<Float> get() = cloudSettings.elevenLabsSimilarityBoostFlow
    override val spotifyAccessTokenFlow: StateFlow<String?> get() = cloudSettings.spotifyAccessTokenFlow
    override val spotifyRefreshTokenFlow: StateFlow<String?> get() = cloudSettings.spotifyRefreshTokenFlow
    override val spotifyTokenExpiresAtFlow: StateFlow<Long> get() = cloudSettings.spotifyTokenExpiresAtFlow
    override val spotifyUserDisplayNameFlow: StateFlow<String?> get() = cloudSettings.spotifyUserDisplayNameFlow
    override val googleDriveFolderIdFlow: StateFlow<String?> get() = cloudSettings.googleDriveFolderIdFlow
    override val googleDriveFolderNameFlow: StateFlow<String?> get() = cloudSettings.googleDriveFolderNameFlow
    override val syncTargetTypeFlow: StateFlow<String> get() = cloudSettings.syncTargetTypeFlow
    override val localFolderSafUriFlow: StateFlow<String?> get() = cloudSettings.localFolderSafUriFlow
    override val localFolderSafNameFlow: StateFlow<String?> get() = cloudSettings.localFolderSafNameFlow
    override val ttsEngineFlow: StateFlow<String?> get() = voiceSettings.ttsEngineFlow
    override val googleTtsLanguageFlow: StateFlow<String?> get() = voiceSettings.googleTtsLanguageFlow
    override val googleTtsVoiceNameFlow: StateFlow<String?> get() = voiceSettings.googleTtsVoiceNameFlow
    override val elevenLabsTtsLanguageFlow: StateFlow<String?> get() = voiceSettings.elevenLabsTtsLanguageFlow
    override val elevenLabsTtsVoiceNameFlow: StateFlow<String?> get() = voiceSettings.elevenLabsTtsVoiceNameFlow
    override val ttsPlaybackSpeedFlow: StateFlow<Float> get() = voiceSettings.ttsPlaybackSpeedFlow

    // --- CallSettings Flows ---
    override val maxCallDurationSecondsFlow: StateFlow<Int> get() = callSettings.maxCallDurationSecondsFlow
    override val callDurationFeedbackIntervalSecondsFlow: StateFlow<Int> get() = callSettings.callDurationFeedbackIntervalSecondsFlow
    override val outgoingCallIntroFlow: StateFlow<String> get() = callSettings.outgoingCallIntroFlow
    override val incomingCallIntroFlow: StateFlow<String> get() = callSettings.incomingCallIntroFlow
    override val incomingCallScanLimitUserModeActiveFlow: StateFlow<Int> get() = callSettings.incomingCallScanLimitUserModeActiveFlow
    override val incomingCallAutoActionUserModeActiveFlow: StateFlow<String> get() = callSettings.incomingCallAutoActionUserModeActiveFlow
    override val incomingCallDelayUserModeInactiveFlow: StateFlow<Int> get() = callSettings.incomingCallDelayUserModeInactiveFlow
    override val incomingCallAutoActionUserModeInactiveFlow: StateFlow<String> get() = callSettings.incomingCallAutoActionUserModeInactiveFlow
    override val callAnnouncementAsCueFlow: StateFlow<Boolean> get() = callSettings.callAnnouncementAsCueFlow
    override val autoEnableSpeakerphoneFlow: StateFlow<Boolean> get() = callSettings.autoEnableSpeakerphoneFlow
    override val simulateCallsEnabledFlow: StateFlow<Boolean> get() = callSettings.simulateCallsEnabledFlow
    override val hangUpPressesRequiredFlow: StateFlow<Int> get() = callSettings.hangUpPressesRequiredFlow
    override val filterCallsNotInContactsFlow: StateFlow<Boolean> get() = callSettings.filterCallsNotInContactsFlow

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

    override var staticRowEnabled: Boolean
        get() = scanningSettings.staticRowEnabled
        set(value) { scanningSettings.staticRowEnabled = value }

    override var staticRowScanPattern: String
        get() = scanningSettings.staticRowScanPattern
        set(value) { scanningSettings.staticRowScanPattern = value }

    override var lateClickThresholdMillis: Long
        get() = scanningSettings.lateClickThresholdMillis
        set(value) { scanningSettings.lateClickThresholdMillis = value }


    override var defaultStartPageId: String?
        get() = generalSettings.defaultStartPageId
        set(value) { generalSettings.defaultStartPageId = value }

    override var ttsAudioDeviceAddress: String?
        get() = voiceSettings.ttsAudioDeviceAddress
        set(value) { voiceSettings.ttsAudioDeviceAddress = value }

    override var recordingAudioSource: Int
        get() = voiceSettings.recordingAudioSource
        set(value) { voiceSettings.recordingAudioSource = value }

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

    override var syncModeLogs: String
        get() = cloudSettings.syncModeLogs
        set(value) { cloudSettings.syncModeLogs = value }

    override var syncLogsIntervalHours: Long
        get() = cloudSettings.syncLogsIntervalHours
        set(value) { cloudSettings.syncLogsIntervalHours = value }

    override var lastLogsSyncTime: Long
        get() = cloudSettings.lastLogsSyncTime
        set(value) { cloudSettings.lastLogsSyncTime = value }

    override var lastUploadedLogHash: String?
        get() = cloudSettings.lastUploadedLogHash
        set(value) { cloudSettings.lastUploadedLogHash = value }

    override var smartPredictionDelay: Long
        get() = advancedSettings.smartPredictionDelay
        set(value) { advancedSettings.smartPredictionDelay = value }

    override var bluetoothDelay: Long
        get() = scanningSettings.bluetoothDelay
        set(value) { scanningSettings.bluetoothDelay = value }

    override var blockVolumeKeys: Boolean
        get() = scanningSettings.blockVolumeKeys
        set(value) { scanningSettings.blockVolumeKeys = value }

    override var speakerVolume: Int
        get() = scanningSettings.speakerVolume
        set(value) { scanningSettings.speakerVolume = value }

    override var headphoneVolume: Int
        get() = scanningSettings.headphoneVolume
        set(value) { scanningSettings.headphoneVolume = value }

    override var isCloudSyncEnabled: Boolean
        get() = cloudSettings.isCloudSyncEnabled
        set(value) { cloudSettings.isCloudSyncEnabled = value }

    override var syncIntervalMinutes: Long
        get() = cloudSettings.syncIntervalMinutes
        set(value) { cloudSettings.syncIntervalMinutes = value }

    override var syncModeBook: String
        get() = cloudSettings.syncModeBook
        set(value) { cloudSettings.syncModeBook = value }
    override var syncModeTts: String
        get() = cloudSettings.syncModeTts
        set(value) { cloudSettings.syncModeTts = value }
    override var syncModeStats: String
        get() = cloudSettings.syncModeStats
        set(value) { cloudSettings.syncModeStats = value }
    override var hueBridgeIp: String
        get() = smartHomeSettings.hueBridgeIp
        set(value) { smartHomeSettings.hueBridgeIp = value }

    override var hueUsername: String
        get() = smartHomeSettings.hueUsername
        set(value) { smartHomeSettings.hueUsername = value }

    override var hueBridgeFingerprint: String
        get() = smartHomeSettings.hueBridgeFingerprint
        set(value) { smartHomeSettings.hueBridgeFingerprint = value }

    override var hueCachedDevices: String
        get() = smartHomeSettings.hueCachedDevices
        set(value) { smartHomeSettings.hueCachedDevices = value }

    override var isGeminiEnabled: Boolean
        get() = genAiSettings.isGeminiEnabled
        set(value) { genAiSettings.isGeminiEnabled = value }
    override val isGeminiEnabledFlow: StateFlow<Boolean> get() = genAiSettings.isGeminiEnabledFlow

    override var geminiApiKey: String?
        get() = genAiSettings.geminiApiKey
        set(value) { genAiSettings.geminiApiKey = value }
    override val geminiApiKeyFlow: StateFlow<String?> get() = genAiSettings.geminiApiKeyFlow

    override var useGeminiApiKey: Boolean
        get() = genAiSettings.useGeminiApiKey
        set(value) { genAiSettings.useGeminiApiKey = value }
    override val useGeminiApiKeyFlow: StateFlow<Boolean> get() = genAiSettings.useGeminiApiKeyFlow

    override var hasAcceptedPageSplitOptIn: Boolean
        get() = genAiSettings.hasAcceptedPageSplitOptIn
        set(value) { genAiSettings.hasAcceptedPageSplitOptIn = value }
    override val hasAcceptedPageSplitOptInFlow: StateFlow<Boolean> get() = genAiSettings.hasAcceptedPageSplitOptInFlow


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

    override var statsRetentionDays: Int
        get() = userSettings.statsRetentionDays
        set(value) { userSettings.statsRetentionDays = value }

    override var statsAggregationHours: Int
        get() = userSettings.statsAggregationHours
        set(value) { userSettings.statsAggregationHours = value }

    override var onlyRecordHardwareStats: Boolean
        get() = userSettings.onlyRecordHardwareStats
        set(value) { userSettings.onlyRecordHardwareStats = value }

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

    override var backgroundLocationEnabled: Boolean
        get() = advancedSettings.backgroundLocationEnabled
        set(value) { advancedSettings.backgroundLocationEnabled = value }

    override var backgroundLocationInterval: Long
        get() = advancedSettings.backgroundLocationInterval
        set(value) { advancedSettings.backgroundLocationInterval = value }

    override var backgroundWeatherEnabled: Boolean
        get() = advancedSettings.backgroundWeatherEnabled
        set(value) { advancedSettings.backgroundWeatherEnabled = value }

    override var backgroundWeatherInterval: Long
        get() = advancedSettings.backgroundWeatherInterval
        set(value) { advancedSettings.backgroundWeatherInterval = value }

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

    override var isSecurityRequiredForAnalytics: Boolean
        get() = securitySettings.isSecurityRequiredForAnalytics
        set(value) { securitySettings.isSecurityRequiredForAnalytics = value }

    override var startupBehavior: String
        get() = generalSettings.startupBehavior
        set(value) { generalSettings.startupBehavior = value }

    override var favoriteBookId: String?
        get() = generalSettings.favoriteBookId
        set(value) { generalSettings.favoriteBookId = value }

    override var forceSoftKeyboard: Boolean
        get() = generalSettings.forceSoftKeyboard
        set(value) { generalSettings.forceSoftKeyboard = value }

    override var syncLogsStorage: String?
        get() = generalSettings.syncLogsStorage
        set(value) { generalSettings.syncLogsStorage = value }

    override var isSetupCompleted: Boolean
        get() = generalSettings.isSetupCompleted
        set(value) { generalSettings.isSetupCompleted = value }

    override var elevenLabsApiKey: String?
        get() = cloudSettings.elevenLabsApiKey
        set(value) { cloudSettings.elevenLabsApiKey = value }

    override var elevenLabsModel: String
        get() = cloudSettings.elevenLabsModel
        set(value) { cloudSettings.elevenLabsModel = value }

    override var elevenLabsStability: Float
        get() = cloudSettings.elevenLabsStability
        set(value) { cloudSettings.elevenLabsStability = value }

    override var elevenLabsSimilarityBoost: Float
        get() = cloudSettings.elevenLabsSimilarityBoost
        set(value) { cloudSettings.elevenLabsSimilarityBoost = value }

    override var spotifyAccessToken: String?
        get() = cloudSettings.spotifyAccessToken
        set(value) { cloudSettings.spotifyAccessToken = value }

    override var spotifyRefreshToken: String?
        get() = cloudSettings.spotifyRefreshToken
        set(value) { cloudSettings.spotifyRefreshToken = value }

    override var spotifyTokenExpiresAt: Long
        get() = cloudSettings.spotifyTokenExpiresAt
        set(value) { cloudSettings.spotifyTokenExpiresAt = value }

    override var spotifyUserDisplayName: String?
        get() = cloudSettings.spotifyUserDisplayName
        set(value) { cloudSettings.spotifyUserDisplayName = value }

    override var googleDriveFolderId: String?
        get() = cloudSettings.googleDriveFolderId
        set(value) { cloudSettings.googleDriveFolderId = value }

    override var googleDriveFolderName: String?
        get() = cloudSettings.googleDriveFolderName
        set(value) { cloudSettings.googleDriveFolderName = value }

    override var syncTargetType: String
        get() = cloudSettings.syncTargetType
        set(value) { cloudSettings.syncTargetType = value }

    override var localFolderSafUri: String?
        get() = cloudSettings.localFolderSafUri
        set(value) { cloudSettings.localFolderSafUri = value }

    override var localFolderSafName: String?
        get() = cloudSettings.localFolderSafName
        set(value) { cloudSettings.localFolderSafName = value }


    override var ttsEngine: String?
        get() = voiceSettings.ttsEngine
        set(value) { voiceSettings.ttsEngine = value }

    override var googleTtsLanguage: String?
        get() = voiceSettings.googleTtsLanguage
        set(value) { voiceSettings.googleTtsLanguage = value }

    override var googleTtsVoiceName: String?
        get() = voiceSettings.googleTtsVoiceName
        set(value) { voiceSettings.googleTtsVoiceName = value }

    override var elevenLabsTtsLanguage: String?
        get() = voiceSettings.elevenLabsTtsLanguage
        set(value) { voiceSettings.elevenLabsTtsLanguage = value }

    override var elevenLabsTtsVoiceName: String?
        get() = voiceSettings.elevenLabsTtsVoiceName
        set(value) { voiceSettings.elevenLabsTtsVoiceName = value }

    override var ttsPlaybackSpeed: Float
        get() = voiceSettings.ttsPlaybackSpeed
        set(value) { voiceSettings.ttsPlaybackSpeed = value }

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

    override fun refresh() {
        refreshFlows()
    }

    // --- Book-specific settings implementation ---

    override fun getDefaultStartPageIdForBook(bookId: String): String? =
        generalSettings.getStringForBook(bookId, SettingsConstants.KEY_DEFAULT_START_PAGE_ID)

    override fun getPageSortOrderForBook(bookId: String): String =
        generalSettings.getStringForBook(bookId, SettingsConstants.KEY_PAGE_SORT_ORDER, "MANUAL") ?: "MANUAL"

    override fun getTemplateSortOrderForBook(bookId: String): String =
        generalSettings.getStringForBook(bookId, SettingsConstants.KEY_TEMPLATE_SORT_ORDER, "MANUAL") ?: "MANUAL"

    override fun getAutoStartScanningForBook(bookId: String): Boolean =
        scanningSettings.getBooleanForBook(bookId, SettingsConstants.KEY_AUTO_START_SCANNING, true)

    override fun getScanDelayMillisForBook(bookId: String): Long =
        scanningSettings.getLongForBook(bookId, SettingsConstants.KEY_SCAN_DELAY_MILLIS, 3000L)

    override fun getResumeScanningFromStartForBook(bookId: String): Boolean =
        scanningSettings.getBooleanForBook(bookId, SettingsConstants.KEY_RESUME_SCANNING_FROM_START, true)

    override fun getHoldingTimeMillisForBook(bookId: String): Long =
        scanningSettings.getLongForBook(bookId, SettingsConstants.KEY_HOLDING_TIME_MILLIS, 250L)

    override fun getSwitchActivationKeyForBook(bookId: String): String =
        scanningSettings.getStringForBook(bookId, SettingsConstants.KEY_SWITCH_ACTIVATION_KEY, "~3") ?: "~3"

    override fun getVolumeKeysActivateForBook(bookId: String): Boolean =
        scanningSettings.getBooleanForBook(bookId, SettingsConstants.KEY_VOLUME_KEYS_ACTIVATE, false)

    override fun getDefaultScanPatternForBook(bookId: String): String =
        scanningSettings.getStringForBook(bookId, SettingsConstants.KEY_DEFAULT_SCAN_PATTERN, "linear") ?: "linear"

    override fun getLimitScanCyclesForBook(bookId: String): Boolean =
        scanningSettings.getBooleanForBook(bookId, SettingsConstants.KEY_LIMIT_SCAN_CYCLES, false)

    override fun getScanCycleLimitForBook(bookId: String): Int =
        scanningSettings.getIntForBook(bookId, SettingsConstants.KEY_SCAN_CYCLE_LIMIT, 2)

    override fun getStaticRowEnabledForBook(bookId: String): Boolean =
        scanningSettings.getBooleanForBook(bookId, SettingsConstants.KEY_STATIC_ROW_ENABLED, false)

    override fun getStaticRowScanPatternForBook(bookId: String): String =
        scanningSettings.getStringForBook(bookId, SettingsConstants.KEY_STATIC_ROW_SCAN_PATTERN, "linear") ?: "linear"

    override fun getLateClickThresholdMillisForBook(bookId: String): Long =
        scanningSettings.getLongForBook(bookId, SettingsConstants.KEY_LATE_CLICK_THRESHOLD_MILLIS, 250L)

    override fun getSmartPredictionDelayForBook(bookId: String): Long =
        advancedSettings.getLongForBook(bookId, SettingsConstants.KEY_SMART_PREDICTION_DELAY, 2000L)

    override fun getIsSmartPredictionEnabledForBook(bookId: String): Boolean =
        advancedSettings.getBooleanForBook(bookId, SettingsConstants.KEY_SMART_PREDICTION_ENABLED, false)

    override fun getActionLogLimitForBook(bookId: String): Int =
        advancedSettings.getIntForBook(bookId, SettingsConstants.KEY_ACTION_LOG_LIMIT, 100)

    override fun getLogIgnoredActionsForBook(bookId: String): Boolean =
        advancedSettings.getBooleanForBook(bookId, SettingsConstants.KEY_LOG_IGNORED_ACTIONS, false)

    override fun getLogStopActionsForBook(bookId: String): Boolean =
        advancedSettings.getBooleanForBook(bookId, SettingsConstants.KEY_LOG_STOP_ACTIONS, false)

    // --- CallSettings Properties ---
    override var maxCallDurationSeconds: Int
        get() = callSettings.maxCallDurationSeconds
        set(value) { callSettings.maxCallDurationSeconds = value }
    
    override var callDurationFeedbackIntervalSeconds: Int
        get() = callSettings.callDurationFeedbackIntervalSeconds
        set(value) { callSettings.callDurationFeedbackIntervalSeconds = value }

    override var outgoingCallIntro: String
        get() = callSettings.outgoingCallIntro
        set(value) { callSettings.outgoingCallIntro = value }

    override var incomingCallIntro: String
        get() = callSettings.incomingCallIntro
        set(value) { callSettings.incomingCallIntro = value }

    override var incomingCallScanLimitUserModeActive: Int
        get() = callSettings.incomingCallScanLimitUserModeActive
        set(value) { callSettings.incomingCallScanLimitUserModeActive = value }

    override var incomingCallAutoActionUserModeActive: String
        get() = callSettings.incomingCallAutoActionUserModeActive
        set(value) { callSettings.incomingCallAutoActionUserModeActive = value }

    override var incomingCallDelayUserModeInactive: Int
        get() = callSettings.incomingCallDelayUserModeInactive
        set(value) { callSettings.incomingCallDelayUserModeInactive = value }

    override var incomingCallAutoActionUserModeInactive: String
        get() = callSettings.incomingCallAutoActionUserModeInactive
        set(value) { callSettings.incomingCallAutoActionUserModeInactive = value }

    override var callAnnouncementAsCue: Boolean
        get() = callSettings.callAnnouncementAsCue
        set(value) { callSettings.callAnnouncementAsCue = value }

    override var autoEnableSpeakerphone: Boolean
        get() = callSettings.autoEnableSpeakerphone
        set(value) { callSettings.autoEnableSpeakerphone = value }

    override var simulateCallsEnabled: Boolean
        get() = callSettings.simulateCallsEnabled
        set(value) { callSettings.simulateCallsEnabled = value }

    override var hangUpPressesRequired: Int
        get() = callSettings.hangUpPressesRequired
        set(value) { callSettings.hangUpPressesRequired = value }

    override var filterCallsNotInContacts: Boolean
        get() = callSettings.filterCallsNotInContacts
        set(value) { callSettings.filterCallsNotInContacts = value }
}

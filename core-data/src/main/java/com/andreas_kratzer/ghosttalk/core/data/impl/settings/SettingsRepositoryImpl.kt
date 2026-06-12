package com.andreas_kratzer.ghosttalk.core.data.impl.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.andreas_kratzer.ghosttalk.core.SecuritySettings
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.database.toDomain
import com.andreas_kratzer.ghosttalk.core.database.toEntity
import com.andreas_kratzer.ghosttalk.core.di.ApplicationScope
import com.andreas_kratzer.ghosttalk.core.model.ProfileConfig
import com.andreas_kratzer.ghosttalk.core.model.SettingsProfile
import com.andreas_kratzer.ghosttalk.core.settings.AdvancedSettings
import com.andreas_kratzer.ghosttalk.core.settings.CallSettings
import com.andreas_kratzer.ghosttalk.core.settings.CloudSettings
import com.andreas_kratzer.ghosttalk.core.settings.GenAiSettings
import com.andreas_kratzer.ghosttalk.core.settings.GeneralSettings
import com.andreas_kratzer.ghosttalk.core.settings.NotificationSettings
import com.andreas_kratzer.ghosttalk.core.settings.ScanningSettings
import com.andreas_kratzer.ghosttalk.core.settings.SmartHomeSettings
import com.andreas_kratzer.ghosttalk.core.settings.TtsSettings
import com.andreas_kratzer.ghosttalk.core.settings.UserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@SuppressLint("CommitPrefEdits", "ApplySharedPref", "UseKtx")
class SettingsRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val settingsProfileDao: com.andreas_kratzer.ghosttalk.core.database.SettingsProfileDao,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val activeBookIdManager: ActiveBookIdManager,
    private val voiceSettings: VoiceSettingsRepository,
    private val scanningSettings: ScanningSettingsRepository,
    private val securitySettings: SecuritySettingsRepository,
    private val cloudSettings: CloudSettingsRepository,
    private val smartHomeSettings: SmartHomeSettingsRepository,
    private val genAiSettings: GenAiSettingsRepository,
    private val generalSettings: GeneralSettingsRepository,
    private val notificationSettings: NotificationSettingsRepository,
    private val advancedSettings: AdvancedSettingsRepository,
    private val userSettings: UserSettingsRepository,
    private val callSettings: CallSettingsRepository
) : SettingsRepository,
    TtsSettings by voiceSettings,
    ScanningSettings by scanningSettings,
    SecuritySettings by securitySettings,
    GenAiSettings by genAiSettings,
    GeneralSettings by generalSettings,
    NotificationSettings by notificationSettings,
    UserSettings by userSettings,
    CallSettings by callSettings,
    CloudSettings by cloudSettings,
    SmartHomeSettings by smartHomeSettings,
    AdvancedSettings by advancedSettings {

    private val prefs: SharedPreferences = context.getSharedPreferences(SettingsConstants.PREFS_NAME, Context.MODE_PRIVATE)

    override val activeBookIdFlow: StateFlow<String?> = activeBookIdManager.activeBookIdFlow

    override var activeBookId: String
        get() = activeBookIdManager.activeBookId
        set(value) {
            activeBookIdManager.activeBookId = value
        }

    init {
        scope.launch {
            val migrationManager = SettingsMigrationManager(context, settingsProfileDao, prefs)
            val activeProfileId = generalSettings.activeProfileId
            migrationManager.migrateIfNeeded(activeBookIdManager.activeBookId, activeProfileId)
        }
        scope.launch {
            activeBookIdManager.activeBookIdFlow.collect {
                refreshFlows()
            }
        }

        val listener: (String) -> Unit = { key ->
            if (key !in SettingsMapper.NON_SYNCABLE_SETTINGS) {
                updateConfigLastModified(activeBookId)
            }
            if (!isApplyingProfile) {
                val activeId = activeProfileId
                when (key) {
                    SettingsConstants.KEY_ELEVENLABS_API_KEY -> prefs.edit().putString("profile_${activeId}_elevenlabs_api_key", prefs.getString(key, null)).apply()
                    SettingsConstants.KEY_GEMINI_API_KEY -> prefs.edit().putString("profile_${activeId}_gemini_api_key", prefs.getString(key, null)).apply()
                    SettingsConstants.KEY_SECURITY_PIN_HASH -> prefs.edit().putString("profile_${activeId}_security_pin_hash", prefs.getString(key, null)).apply()
                    SettingsConstants.KEY_SECURITY_PIN_SALT -> prefs.edit().putString("profile_${activeId}_security_pin_salt", prefs.getString(key, null)).apply()
                    SettingsConstants.KEY_HUE_USERNAME -> prefs.edit().putString("profile_${activeId}_hue_username", prefs.getString(key, null)).apply()
                    SettingsConstants.KEY_HUE_BRIDGE_FINGERPRINT -> prefs.edit().putString("profile_${activeId}_hue_bridge_fingerprint", prefs.getString(key, null)).apply()
                }
                when (key) {
                    SettingsConstants.KEY_LIMIT_SCAN_CYCLES,
                    SettingsConstants.KEY_SCAN_CYCLE_LIMIT,
                    SettingsConstants.KEY_ACTION_LOG_LIMIT,
                    SettingsConstants.KEY_LOG_IGNORED_ACTIONS,
                    SettingsConstants.KEY_LOG_STOP_ACTIONS -> {
                        syncBookSettings()
                    }
                }
                // If a syncable profile preference changes, write it back to the database
                scope.launch {
                    val profile = getProfileById(activeId)
                    if (profile != null) {
                        val currentConfig = ProfileConfig(
                            favoriteBookId = favoriteBookId,
                            startupBehavior = startupBehavior,
                            userModeScreenBehavior = userModeScreenBehavior,
                            securityPinTimeoutMinutes = securityPinTimeoutMinutes,
                            isPinRequiredForDeletion = isPinRequiredForDeletion,
                            isSecurityRequiredForEdit = isSecurityRequiredForEdit,
                            isSecurityRequiredForSettings = isSecurityRequiredForSettings,
                            isSecurityRequiredForAnalytics = isSecurityRequiredForAnalytics,
                            useGeminiApiKey = useGeminiApiKey,
                            autoStartScanning = autoStartScanning,
                            scanDelayMillis = scanDelayMillis,
                            holdingTimeMillis = holdingTimeMillis,
                            resumeScanningFromStart = resumeScanningFromStart,
                            switchActivationKey = switchActivationKey,
                            volumeKeysActivate = volumeKeysActivate,
                            defaultScanPattern = defaultScanPattern,
                            limitScanCycles = limitScanCycles,
                            scanCycleLimit = scanCycleLimit,
                            staticRowEnabled = staticRowEnabled,
                            lateClickThresholdMillis = lateClickThresholdMillis,
                            forceSoftKeyboard = forceSoftKeyboard,
                            vocalSwitchEnabled = isVocalSwitchEnabled,
                            ttsEngine = ttsEngine,
                            ttsLanguage = ttsLanguage,
                            ttsVoiceName = ttsVoiceName,
                            googleTtsLanguage = googleTtsLanguage,
                            googleTtsVoiceName = googleTtsVoiceName,
                            elevenLabsTtsLanguage = elevenLabsTtsLanguage,
                            elevenLabsTtsVoiceName = elevenLabsTtsVoiceName,
                            elevenLabsModel = elevenLabsModel,
                            elevenLabsStability = elevenLabsStability,
                            elevenLabsSimilarityBoost = elevenLabsSimilarityBoost,
                            ttsPlaybackSpeed = ttsPlaybackSpeed,
                            isSmartPredictionEnabled = isSmartPredictionEnabled,
                            smartPredictionDelay = smartPredictionDelay,
                            isGeminiEnabled = isGeminiEnabled,
                            geminiRedoPrediction = geminiRedoPrediction,
                            geminiTimeout = geminiTimeout,
                            maxCallDurationSeconds = maxCallDurationSeconds,
                            callDurationFeedbackIntervalSeconds = callDurationFeedbackIntervalSeconds,
                            outgoingCallIntro = outgoingCallIntro,
                            incomingCallIntro = incomingCallIntro,
                            incomingCallScanLimitUserModeActive = incomingCallScanLimitUserModeActive,
                            incomingCallAutoActionUserModeActive = incomingCallAutoActionUserModeActive,
                            incomingCallDelayUserModeInactive = incomingCallDelayUserModeInactive,
                            incomingCallAutoActionUserModeInactive = incomingCallAutoActionUserModeInactive,
                            callAnnouncementAsCue = callAnnouncementAsCue,
                            autoEnableSpeakerphone = autoEnableSpeakerphone,
                            simulateCallsEnabled = simulateCallsEnabled,
                            hangUpPressesRequired = hangUpPressesRequired,
                            filterCallsNotInContacts = filterCallsNotInContacts,
                            isNotificationReadingEnabled = isNotificationReadingEnabled,
                            monitoredNotificationApps = monitoredNotificationApps,
                            autoReadMode = autoReadMode.name,
                            autoReadOnlyInUserMode = autoReadOnlyInUserMode,
                            autoReadInStandby = autoReadInStandby,
                            themeMode = themeMode,
                            appLanguage = appLanguage,
                            pageSortOrder = pageSortOrder,
                            templateSortOrder = templateSortOrder,
                            keepScreenOnUserMode = keepScreenOnUserMode,
                            actionLogLimit = actionLogLimit,
                            persistActionLogs = persistActionLogs,
                            showPageIdInLog = showPageIdInLog,
                            onlyRecordHardwareStats = onlyRecordHardwareStats,
                            firebaseAnalyticsEnabled = firebaseAnalyticsEnabled,
                            statsRetentionDays = statsRetentionDays,
                            statsAggregationHours = statsAggregationHours,
                            weatherCacheTimeout = weatherCacheTimeout,
                            backgroundLocationEnabled = backgroundLocationEnabled,
                            backgroundLocationInterval = backgroundLocationInterval,
                            backgroundWeatherEnabled = backgroundWeatherEnabled,
                            backgroundWeatherInterval = backgroundWeatherInterval,
                            syncIntervalMinutes = syncIntervalMinutes,
                            foregroundSyncIntervalMinutes = foregroundSyncIntervalMinutes,
                            syncModeBook = syncModeBook,
                            syncModeStats = syncModeStats,
                            syncModeTts = syncModeTts,
                            syncModeLogs = syncModeLogs,
                            syncLogsIntervalHours = syncLogsIntervalHours,
                            preferredMainSpeakerName = preferredMainSpeakerName,
                            preferredCueSpeakerName = preferredCueSpeakerName,
                            fallbackToInternalAudio = prefs.getBoolean("fallback_to_internal_audio", true),
                            logIgnoredActions = logIgnoredActions,
                            logStopActions = logStopActions,
                            bluetoothDelay = bluetoothDelay,
                            hueBridgeIp = hueBridgeIp,
                            hueCachedDevices = hueCachedDevices,
                            speakerVolume = speakerVolume,
                            headphoneVolume = headphoneVolume,
                            blockVolumeKeys = blockVolumeKeys,
                            isDataCloudSyncEnabled = isDataCloudSyncEnabled,
                            googleDriveFolderId = googleDriveFolderId,
                            googleDriveFolderName = googleDriveFolderName
                        )
                        if (profile.config != currentConfig) {
                            val updatedProfile = profile.copy(config = currentConfig, updatedAt = System.currentTimeMillis())
                            updateProfile(updatedProfile)
                        }
                    }
                }
            }
        }
        voiceSettings.changeListener = listener
        scanningSettings.changeListener = listener
        securitySettings.changeListener = listener
        cloudSettings.changeListener = listener
        smartHomeSettings.changeListener = listener
        genAiSettings.changeListener = listener
        generalSettings.changeListener = listener
        notificationSettings.changeListener = listener
        advancedSettings.changeListener = listener
        userSettings.changeListener = listener
        callSettings.changeListener = listener

        cleanupLegacyBookPins()

        scope.launch {
            activeBookIdFlow.collect {
                refreshFlows()
            }
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
        callSettings.refresh()
    }

    // --- Resolved delegation conflicts explicitly ---

    override var appLanguage: String?
        get() = generalSettings.appLanguage
        set(value) { generalSettings.appLanguage = value }
    override val appLanguageFlow: StateFlow<String?>
        get() = generalSettings.appLanguageFlow

    override var cuesAudioDeviceAddress: String?
        get() = voiceSettings.cuesAudioDeviceAddress
        set(value) { voiceSettings.cuesAudioDeviceAddress = value }
    override val cuesAudioDeviceAddressFlow: StateFlow<String?>
        get() = voiceSettings.cuesAudioDeviceAddressFlow

    override var isSmartPredictionEnabled: Boolean
        get() = advancedSettings.isSmartPredictionEnabled
        set(value) { advancedSettings.isSmartPredictionEnabled = value }
    override val isSmartPredictionEnabledFlow: StateFlow<Boolean>
        get() = advancedSettings.isSmartPredictionEnabledFlow

    override var showPageIdInLog: Boolean
        get() = advancedSettings.showPageIdInLog
        set(value) { advancedSettings.showPageIdInLog = value }
    override val showPageIdInLogFlow: StateFlow<Boolean>
        get() = advancedSettings.showPageIdInLogFlow

    override var isNotificationReadingEnabled: Boolean
        get() = notificationSettings.isNotificationReadingEnabled
        set(value) { notificationSettings.isNotificationReadingEnabled = value }
    override val isNotificationReadingEnabledFlow: StateFlow<Boolean>
        get() = notificationSettings.isNotificationReadingEnabledFlow

    override var monitoredNotificationApps: Set<String>
        get() = notificationSettings.monitoredNotificationApps
        set(value) { notificationSettings.monitoredNotificationApps = value }
    override val monitoredNotificationAppsFlow: StateFlow<Set<String>>
        get() = notificationSettings.monitoredNotificationAppsFlow

    override var autoReadMode: com.andreas_kratzer.ghosttalk.core.settings.AutoReadMode
        get() = notificationSettings.autoReadMode
        set(value) { notificationSettings.autoReadMode = value }
    override val autoReadModeFlow: StateFlow<com.andreas_kratzer.ghosttalk.core.settings.AutoReadMode>
        get() = notificationSettings.autoReadModeFlow

    override var syncLogsStorage: String?
        get() = generalSettings.syncLogsStorage
        set(value) { generalSettings.syncLogsStorage = value }
    override val syncLogsStorageFlow: StateFlow<String?>
        get() = generalSettings.syncLogsStorageFlow

    override var elevenLabsTtsLanguage: String?
        get() = voiceSettings.elevenLabsTtsLanguage
        set(value) {
            voiceSettings.elevenLabsTtsLanguage = value
            cloudSettings.elevenLabsTtsLanguage = value
        }
    override val elevenLabsTtsLanguageFlow: StateFlow<String?>
        get() = voiceSettings.elevenLabsTtsLanguageFlow

    override var autoReadOnlyInUserMode: Boolean
        get() = notificationSettings.autoReadOnlyInUserMode
        set(value) { notificationSettings.autoReadOnlyInUserMode = value }
    override val autoReadOnlyInUserModeFlow: StateFlow<Boolean>
        get() = notificationSettings.autoReadOnlyInUserModeFlow

    override var autoReadInStandby: Boolean
        get() = notificationSettings.autoReadInStandby
        set(value) { notificationSettings.autoReadInStandby = value }
    override val autoReadInStandbyFlow: StateFlow<Boolean>
        get() = notificationSettings.autoReadInStandbyFlow

    override var simulateCallsEnabled: Boolean
        get() = callSettings.simulateCallsEnabled
        set(value) { callSettings.simulateCallsEnabled = value }
    override val simulateCallsEnabledFlow: StateFlow<Boolean>
        get() = callSettings.simulateCallsEnabledFlow

    override var speakerVolume: Int
        get() = scanningSettings.speakerVolume
        set(value) { scanningSettings.speakerVolume = value }
    override val speakerVolumeFlow: StateFlow<Int>
        get() = scanningSettings.speakerVolumeFlow

    override var headphoneVolume: Int
        get() = scanningSettings.headphoneVolume
        set(value) { scanningSettings.headphoneVolume = value }
    override val headphoneVolumeFlow: StateFlow<Int>
        get() = scanningSettings.headphoneVolumeFlow

    override var volumeKeysActivate: Boolean
        get() = scanningSettings.volumeKeysActivate
        set(value) { scanningSettings.volumeKeysActivate = value }
    override val volumeKeysActivateFlow: StateFlow<Boolean>
        get() = scanningSettings.volumeKeysActivateFlow

    override var switchActivationKey: String
        get() = scanningSettings.switchActivationKey
        set(value) { scanningSettings.switchActivationKey = value }
    override val switchActivationKeyFlow: StateFlow<String>
        get() = scanningSettings.switchActivationKeyFlow

    override var bluetoothDelay: Long
        get() = scanningSettings.bluetoothDelay
        set(value) { scanningSettings.bluetoothDelay = value }
    override val bluetoothDelayFlow: StateFlow<Long>
        get() = scanningSettings.bluetoothDelayFlow

    override var blockVolumeKeys: Boolean
        get() = scanningSettings.blockVolumeKeys
        set(value) { scanningSettings.blockVolumeKeys = value }
    override val blockVolumeKeysFlow: StateFlow<Boolean>
        get() = scanningSettings.blockVolumeKeysFlow

    override var recordingAudioSource: Int
        get() = voiceSettings.recordingAudioSource
        set(value) { voiceSettings.recordingAudioSource = value }
    override val recordingAudioSourceFlow: StateFlow<Int>
        get() = voiceSettings.recordingAudioSourceFlow

    override var preferredMainSpeakerName: String?
        get() = voiceSettings.preferredMainSpeakerName
        set(value) { voiceSettings.preferredMainSpeakerName = value }
    override val preferredMainSpeakerNameFlow: StateFlow<String?>
        get() = voiceSettings.preferredMainSpeakerNameFlow

    override var preferredCueSpeakerName: String?
        get() = voiceSettings.preferredCueSpeakerName
        set(value) { voiceSettings.preferredCueSpeakerName = value }
    override val preferredCueSpeakerNameFlow: StateFlow<String?>
        get() = voiceSettings.preferredCueSpeakerNameFlow

    override var ttsAudioDeviceAddress: String?
        get() = voiceSettings.ttsAudioDeviceAddress
        set(value) { voiceSettings.ttsAudioDeviceAddress = value }
    override val ttsAudioDeviceAddressFlow: StateFlow<String?>
        get() = voiceSettings.ttsAudioDeviceAddressFlow

    // --- DatabaseSettings ---
    override var initialTemplatesCreated: Boolean
        get() = prefs.getBoolean(SettingsConstants.KEY_INITIAL_TEMPLATES_CREATED, false)
        set(value) { prefs.edit().putBoolean(SettingsConstants.KEY_INITIAL_TEMPLATES_CREATED, value).apply() }

    // --- FeatureSettings additional fields ---
    override var isVocalSwitchEnabled: Boolean
        get() = scanningSettings.vocalSwitchEnabled
        set(value) { scanningSettings.vocalSwitchEnabled = value }
    override val isVocalSwitchEnabledFlow: StateFlow<Boolean>
        get() = scanningSettings.vocalSwitchEnabledFlow

    // --- Other SettingsRepository custom methods ---
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
        activeBookIdManager.activeBookId = "book-default"
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

    override fun updateConfigLastModified(bookId: String) {
        prefs.edit().putLong("config_last_modified_$bookId", System.currentTimeMillis()).apply()
    }

    override fun getConfigLastModified(bookId: String): Long {
        return prefs.getLong("config_last_modified_$bookId", 0L)
    }

    // --- Profile Management ---
    private val jsonSerializer = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    @Suppress("UNCHECKED_CAST")
    override val activeProfileIdFlow: StateFlow<String>
        get() = generalSettings.activeProfileIdFlow as StateFlow<String>

    override var activeProfileId: String
        get() = generalSettings.activeProfileId
        set(value) {
            generalSettings.activeProfileId = value
            scope.launch {
                loadProfile(value)
            }
        }

    override val isCaregiverDeviceFlow: StateFlow<Boolean>
        get() = generalSettings.isCaregiverDeviceFlow

    override var isCaregiverDevice: Boolean
        get() = generalSettings.isCaregiverDevice
        set(value) { generalSettings.isCaregiverDevice = value }

    override fun getAllProfilesFlow(): Flow<List<SettingsProfile>> {
        return settingsProfileDao.getAllProfilesFlow().map { list ->
            list.map { it.toDomain(jsonSerializer) }
        }
    }

    override suspend fun getAllProfiles(): List<SettingsProfile> {
        return settingsProfileDao.getAllProfiles().map { it.toDomain(jsonSerializer) }
    }

    override suspend fun getProfileById(id: String): SettingsProfile? {
        return settingsProfileDao.getProfileById(id)?.toDomain(jsonSerializer)
    }

    override suspend fun insertProfile(profile: SettingsProfile) {
        val entity = profile.toEntity(jsonSerializer)
        settingsProfileDao.insertProfile(entity)
    }

    override suspend fun updateProfile(profile: SettingsProfile) {
        val entity = profile.toEntity(jsonSerializer)
        settingsProfileDao.updateProfile(entity)
    }

    override suspend fun deleteProfile(profile: SettingsProfile) {
        val entity = profile.toEntity(jsonSerializer)
        settingsProfileDao.deleteProfile(entity)
        prefs.edit().apply {
            remove("profile_${profile.id}_elevenlabs_api_key")
            remove("profile_${profile.id}_gemini_api_key")
            remove("profile_${profile.id}_security_pin_hash")
            remove("profile_${profile.id}_security_pin_salt")
            remove("profile_${profile.id}_hue_username")
            remove("profile_${profile.id}_hue_bridge_fingerprint")
            apply()
        }
    }

    private var isApplyingProfile = false

    override suspend fun loadProfile(profileId: String) {
        val profile = getProfileById(profileId) ?: return
        isApplyingProfile = true
        try {
            val config = profile.config
            // Write config JSON to SharedPreferences cached profile field
            val configJson = jsonSerializer.encodeToString(ProfileConfig.serializer(), config)
            prefs.edit().putString("cached_active_profile_config", configJson).apply()

            // Update SharedPreferences keys corresponding to the profile
            val editor = prefs.edit()

            editor.putString(SettingsConstants.KEY_FAVORITE_BOOK_ID, config.favoriteBookId)
            editor.putString(SettingsConstants.KEY_STARTUP_BEHAVIOR, config.startupBehavior)
            editor.putString(SettingsConstants.KEY_USER_MODE_SCREEN_BEHAVIOR, config.userModeScreenBehavior)
            editor.putString(SettingsConstants.KEY_SECURITY_PIN_HASH, prefs.getString("profile_${profileId}_security_pin_hash", null))
            editor.putString(SettingsConstants.KEY_SECURITY_PIN_SALT, prefs.getString("profile_${profileId}_security_pin_salt", null))
            editor.putLong(SettingsConstants.KEY_SECURITY_PIN_TIMEOUT_MINUTES, config.securityPinTimeoutMinutes)
            editor.putBoolean(SettingsConstants.KEY_IS_PIN_REQUIRED_FOR_DELETION, config.isPinRequiredForDeletion)
            editor.putBoolean(SettingsConstants.KEY_SECURITY_REQUIRED_FOR_EDIT, config.isSecurityRequiredForEdit)
            editor.putBoolean(SettingsConstants.KEY_SECURITY_REQUIRED_FOR_SETTINGS, config.isSecurityRequiredForSettings)
            editor.putBoolean(SettingsConstants.KEY_SECURITY_REQUIRED_FOR_ANALYTICS, config.isSecurityRequiredForAnalytics)
            editor.putString(SettingsConstants.KEY_ELEVENLABS_API_KEY, prefs.getString("profile_${profileId}_elevenlabs_api_key", null))
            editor.putString(SettingsConstants.KEY_GEMINI_API_KEY, prefs.getString("profile_${profileId}_gemini_api_key", null))
            editor.putBoolean(SettingsConstants.KEY_USE_GEMINI_API_KEY, config.useGeminiApiKey)
            editor.putBoolean(SettingsConstants.KEY_AUTO_START_SCANNING, config.autoStartScanning)
            editor.putLong(SettingsConstants.KEY_SCAN_DELAY_MILLIS, config.scanDelayMillis)
            editor.putLong(SettingsConstants.KEY_HOLDING_TIME_MILLIS, config.holdingTimeMillis)
            editor.putBoolean(SettingsConstants.KEY_RESUME_SCANNING_FROM_START, config.resumeScanningFromStart)
            editor.putString(SettingsConstants.KEY_SWITCH_ACTIVATION_KEY, config.switchActivationKey)
            editor.putBoolean(SettingsConstants.KEY_VOLUME_KEYS_ACTIVATE, config.volumeKeysActivate)
            editor.putString(SettingsConstants.KEY_DEFAULT_SCAN_PATTERN, config.defaultScanPattern)
            editor.putBoolean(SettingsConstants.KEY_LIMIT_SCAN_CYCLES, config.limitScanCycles)
            editor.putInt(SettingsConstants.KEY_SCAN_CYCLE_LIMIT, config.scanCycleLimit)
            editor.putBoolean(SettingsConstants.KEY_STATIC_ROW_ENABLED, config.staticRowEnabled)
            editor.putLong(SettingsConstants.KEY_LATE_CLICK_THRESHOLD_MILLIS, config.lateClickThresholdMillis)
            editor.putBoolean(SettingsConstants.KEY_FORCE_SOFT_KEYBOARD, config.forceSoftKeyboard)
            editor.putBoolean(SettingsConstants.KEY_VOCAL_SWITCH_ENABLED, config.vocalSwitchEnabled)
            editor.putString(SettingsConstants.KEY_TTS_ENGINE, config.ttsEngine)
            editor.putString(SettingsConstants.KEY_TTS_LANGUAGE, config.ttsLanguage)
            editor.putString(SettingsConstants.KEY_TTS_VOICE_NAME, config.ttsVoiceName)
            editor.putString(SettingsConstants.KEY_GOOGLE_TTS_LANGUAGE, config.googleTtsLanguage)
            editor.putString(SettingsConstants.KEY_GOOGLE_TTS_VOICE_NAME, config.googleTtsVoiceName)
            editor.putString(SettingsConstants.KEY_ELEVENLABS_TTS_LANGUAGE, config.elevenLabsTtsLanguage)
            editor.putString(SettingsConstants.KEY_ELEVENLABS_TTS_VOICE_NAME, config.elevenLabsTtsVoiceName)
            editor.putString(SettingsConstants.KEY_ELEVENLABS_MODEL, config.elevenLabsModel)
            editor.putFloat(SettingsConstants.KEY_ELEVENLABS_STABILITY, config.elevenLabsStability)
            editor.putFloat(SettingsConstants.KEY_ELEVENLABS_SIMILARITY_BOOST, config.elevenLabsSimilarityBoost)
            editor.putFloat(SettingsConstants.KEY_TTS_PLAYBACK_SPEED, config.ttsPlaybackSpeed)
            editor.putBoolean(SettingsConstants.KEY_SMART_PREDICTION_ENABLED, config.isSmartPredictionEnabled)
            editor.putLong(SettingsConstants.KEY_SMART_PREDICTION_DELAY, config.smartPredictionDelay)
            editor.putBoolean(SettingsConstants.KEY_GEMINI_ENABLED, config.isGeminiEnabled)
            editor.putBoolean(SettingsConstants.KEY_GEMINI_REDO_PREDICTION, config.geminiRedoPrediction)
            editor.putLong(SettingsConstants.KEY_GEMINI_TIMEOUT, config.geminiTimeout)
            editor.putInt(SettingsConstants.KEY_MAX_CALL_DURATION_SECONDS, config.maxCallDurationSeconds)
            editor.putInt(SettingsConstants.KEY_CALL_DURATION_FEEDBACK_INTERVAL_SECONDS, config.callDurationFeedbackIntervalSeconds)
            editor.putString(SettingsConstants.KEY_OUTGOING_CALL_INTRO, config.outgoingCallIntro)
            editor.putString(SettingsConstants.KEY_INCOMING_CALL_INTRO, config.incomingCallIntro)
            editor.putInt(SettingsConstants.KEY_INCOMING_CALL_SCAN_LIMIT_ACTIVE, config.incomingCallScanLimitUserModeActive)
            editor.putString(SettingsConstants.KEY_INCOMING_CALL_AUTO_ACTION_ACTIVE, config.incomingCallAutoActionUserModeActive)
            editor.putInt(SettingsConstants.KEY_INCOMING_CALL_DELAY_INACTIVE, config.incomingCallDelayUserModeInactive)
            editor.putString(SettingsConstants.KEY_INCOMING_CALL_AUTO_ACTION_INACTIVE, config.incomingCallAutoActionUserModeInactive)
            editor.putBoolean(SettingsConstants.KEY_CALL_ANNOUNCEMENT_AS_CUE, config.callAnnouncementAsCue)
            editor.putBoolean(SettingsConstants.KEY_CALL_AUTO_ENABLE_SPEAKERPHONE, config.autoEnableSpeakerphone)
            editor.putBoolean(SettingsConstants.KEY_SIMULATE_CALLS_ENABLED, config.simulateCallsEnabled)
            editor.putInt(SettingsConstants.KEY_CALL_HANG_UP_PRESSES_REQUIRED, config.hangUpPressesRequired)
            editor.putBoolean(SettingsConstants.KEY_FILTER_CALLS_NOT_IN_CONTACTS, config.filterCallsNotInContacts)
            editor.putBoolean(SettingsConstants.KEY_NOTIFICATION_READING_ENABLED, config.isNotificationReadingEnabled)
            editor.putStringSet(SettingsConstants.KEY_MONITORED_NOTIFICATION_APPS, config.monitoredNotificationApps)
            editor.putString(SettingsConstants.KEY_AUTO_READ_MODE, config.autoReadMode)
            editor.putBoolean(SettingsConstants.KEY_AUTO_READ_ONLY_IN_USER_MODE, config.autoReadOnlyInUserMode)
            editor.putBoolean(SettingsConstants.KEY_AUTO_READ_IN_STANDBY, config.autoReadInStandby)
            editor.putString(SettingsConstants.KEY_THEME_MODE, config.themeMode)
            editor.putString(SettingsConstants.KEY_APP_LANGUAGE, config.appLanguage)
            editor.putString(SettingsConstants.KEY_PAGE_SORT_ORDER, config.pageSortOrder)
            editor.putString(SettingsConstants.KEY_TEMPLATE_SORT_ORDER, config.templateSortOrder)
            editor.putBoolean(SettingsConstants.KEY_KEEP_SCREEN_ON_USER_MODE, config.keepScreenOnUserMode)
            editor.putInt(SettingsConstants.KEY_ACTION_LOG_LIMIT, config.actionLogLimit)
            editor.putBoolean(SettingsConstants.KEY_PERSIST_ACTION_LOGS, config.persistActionLogs)
            editor.putBoolean(SettingsConstants.KEY_SHOW_PAGE_ID_IN_LOG, config.showPageIdInLog)
            editor.putBoolean(SettingsConstants.KEY_ONLY_RECORD_HARDWARE_STATS, config.onlyRecordHardwareStats)
            editor.putBoolean(SettingsConstants.KEY_FIREBASE_ANALYTICS_ENABLED, config.firebaseAnalyticsEnabled)
            editor.putInt(SettingsConstants.KEY_STATS_RETENTION_DAYS, config.statsRetentionDays)
            editor.putInt(SettingsConstants.KEY_STATS_AGGREGATION_HOURS, config.statsAggregationHours)
            editor.putLong(SettingsConstants.KEY_WEATHER_CACHE_TIMEOUT, config.weatherCacheTimeout)
            editor.putBoolean(SettingsConstants.KEY_BACKGROUND_LOCATION_ENABLED, config.backgroundLocationEnabled)
            editor.putLong(SettingsConstants.KEY_BACKGROUND_LOCATION_INTERVAL, config.backgroundLocationInterval)
            editor.putBoolean(SettingsConstants.KEY_BACKGROUND_WEATHER_ENABLED, config.backgroundWeatherEnabled)
            editor.putLong(SettingsConstants.KEY_BACKGROUND_WEATHER_INTERVAL, config.backgroundWeatherInterval)
            editor.putLong(SettingsConstants.KEY_SYNC_INTERVAL_MINUTES, config.syncIntervalMinutes)
            editor.putString(SettingsConstants.KEY_SYNC_MODE_BOOK, config.syncModeBook)
            editor.putString(SettingsConstants.KEY_SYNC_MODE_STATS, config.syncModeStats)
            editor.putString(SettingsConstants.KEY_SYNC_MODE_TTS, config.syncModeTts)
            editor.putString(SettingsConstants.KEY_SYNC_MODE_LOGS, config.syncModeLogs)
            editor.putLong(SettingsConstants.KEY_SYNC_LOGS_INTERVAL_HOURS, config.syncLogsIntervalHours)
            editor.putString("preferred_main_speaker_name", config.preferredMainSpeakerName)
            editor.putString("preferred_cue_speaker_name", config.preferredCueSpeakerName)
            editor.putBoolean("fallback_to_internal_audio", config.fallbackToInternalAudio)
            editor.putBoolean(SettingsConstants.KEY_LOG_IGNORED_ACTIONS, config.logIgnoredActions)
            editor.putBoolean(SettingsConstants.KEY_LOG_STOP_ACTIONS, config.logStopActions)
            editor.putLong(SettingsConstants.KEY_BLUETOOTH_DELAY, config.bluetoothDelay)
            editor.putString(SettingsConstants.KEY_HUE_BRIDGE_IP, config.hueBridgeIp)
            editor.putString(SettingsConstants.KEY_HUE_USERNAME, prefs.getString("profile_${profileId}_hue_username", ""))
            editor.putString(SettingsConstants.KEY_HUE_BRIDGE_FINGERPRINT, prefs.getString("profile_${profileId}_hue_bridge_fingerprint", ""))
            editor.putString(SettingsConstants.KEY_HUE_CACHED_DEVICES, config.hueCachedDevices)
            editor.putInt(SettingsConstants.KEY_SPEAKER_VOLUME, config.speakerVolume)
            editor.putInt(SettingsConstants.KEY_HEADPHONE_VOLUME, config.headphoneVolume)
            editor.putBoolean(SettingsConstants.KEY_BLOCK_VOLUME_KEYS, config.blockVolumeKeys)
            editor.putBoolean(SettingsConstants.KEY_CLOUD_SYNC_ENABLED, config.isDataCloudSyncEnabled)
            editor.putString(SettingsConstants.KEY_GOOGLE_DRIVE_FOLDER_ID, config.googleDriveFolderId)
            editor.putString(SettingsConstants.KEY_GOOGLE_DRIVE_FOLDER_NAME, config.googleDriveFolderName)
            editor.apply()

            refreshFlows()
        } finally {
            isApplyingProfile = false
        }
    }
}

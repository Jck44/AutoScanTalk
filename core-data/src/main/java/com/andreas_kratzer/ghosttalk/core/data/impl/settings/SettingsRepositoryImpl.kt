package com.andreas_kratzer.ghosttalk.core.data.impl.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.model.CloudAuthType
import com.andreas_kratzer.ghosttalk.core.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.andreas_kratzer.ghosttalk.core.model.ProfileConfig
import com.andreas_kratzer.ghosttalk.core.model.SettingsProfile
import com.andreas_kratzer.ghosttalk.core.database.toDomain
import com.andreas_kratzer.ghosttalk.core.database.toEntity
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@SuppressLint("CommitPrefEdits", "ApplySharedPref", "UseKtx")
class SettingsRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val settingsProfileDao: com.andreas_kratzer.ghosttalk.core.database.SettingsProfileDao,
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
    private val cloudSettings = CloudSettingsRepository(prefs, activeBookIdFlow, context)
    private val smartHomeSettings = SmartHomeSettingsRepository(prefs, activeBookIdFlow)
    private val genAiSettings = GenAiSettingsRepository(prefs, activeBookIdFlow, context)
    private val generalSettings = GeneralSettingsRepository(prefs, activeBookIdFlow)
    private val notificationSettings = NotificationSettingsRepository(prefs, activeBookIdFlow)
    private val advancedSettings = AdvancedSettingsRepository(prefs, activeBookIdFlow)
    private val userSettings = UserSettingsRepository(prefs, activeBookIdFlow)
    private val callSettings = CallSettingsRepository(prefs, activeBookIdFlow)

    init {
        // Run bootstrapping on start
        scope.launch {
            try {
                ProfileBootstrapper(settingsProfileDao, context, com.andreas_kratzer.ghosttalk.core.util.AppLogger(context)).bootstrapIfNeeded()
                
                // One-time migration of book-scoped scanning settings to global settings & active profile config
                if (!prefs.getBoolean("migration_scanning_settings_to_profile_done", false)) {
                    val activeBookId = _activeBookIdFlow.value
                    
                    val keysToMigrate = listOf(
                        Triple(SettingsConstants.KEY_AUTO_START_SCANNING, "boolean", true),
                        Triple(SettingsConstants.KEY_SCAN_DELAY_MILLIS, "long", 3000L),
                        Triple(SettingsConstants.KEY_RESUME_SCANNING_FROM_START, "boolean", true),
                        Triple(SettingsConstants.KEY_HOLDING_TIME_MILLIS, "long", 250L),
                        Triple(SettingsConstants.KEY_DEFAULT_SCAN_PATTERN, "string", "linear"),
                        Triple(SettingsConstants.KEY_LIMIT_SCAN_CYCLES, "boolean", false),
                        Triple(SettingsConstants.KEY_SCAN_CYCLE_LIMIT, "int", 2),
                        Triple(SettingsConstants.KEY_STATIC_ROW_ENABLED, "boolean", false),
                        Triple(SettingsConstants.KEY_LATE_CLICK_THRESHOLD_MILLIS, "long", 250L),
                        Triple(SettingsConstants.KEY_VOCAL_SWITCH_ENABLED, "boolean", false),
                        Triple(SettingsConstants.KEY_SWITCH_ACTIVATION_KEY, "string", "~3")
                    )
                    
                    val editor = prefs.edit()
                    var profileConfigUpdated = false
                    
                    val activeId = generalSettings.activeProfileId
                    val profile = settingsProfileDao.getProfileById(activeId)?.toDomain(jsonSerializer)
                    var currentConfig = profile?.config ?: ProfileConfig()
                    
                    for ((key, type, defaultValue) in keysToMigrate) {
                        var migratedValue: Any? = null
                        val activeScopedKey = "${activeBookId}_$key"
                        
                        // 1. Check active book
                        if (prefs.contains(activeScopedKey)) {
                            migratedValue = when (type) {
                                "boolean" -> prefs.getBoolean(activeScopedKey, defaultValue as Boolean)
                                "long" -> prefs.getLong(activeScopedKey, defaultValue as Long)
                                "int" -> prefs.getInt(activeScopedKey, defaultValue as Int)
                                "string" -> prefs.getString(activeScopedKey, defaultValue as String)
                                else -> null
                            }
                        }
                        
                        // 2. Fallback: check any other book-scoped key in preferences
                        if (migratedValue == null || migratedValue == defaultValue) {
                            val allPrefs = prefs.all
                            for ((prefKey, prefValue) in allPrefs) {
                                if (prefKey.endsWith("_$key") && prefValue != null && prefValue != defaultValue) {
                                    migratedValue = prefValue
                                    break
                                }
                            }
                        }
                        
                        // If we found a custom key value, write it globally and update config builder
                        if (migratedValue != null && migratedValue != defaultValue) {
                            when (type) {
                                "boolean" -> editor.putBoolean(key, migratedValue as Boolean)
                                "long" -> editor.putLong(key, migratedValue as Long)
                                "int" -> editor.putInt(key, migratedValue as Int)
                                "string" -> editor.putString(key, migratedValue as String)
                            }
                            
                            // Update our local config representation
                            currentConfig = when (key) {
                                SettingsConstants.KEY_AUTO_START_SCANNING -> currentConfig.copy(autoStartScanning = migratedValue as Boolean)
                                SettingsConstants.KEY_SCAN_DELAY_MILLIS -> currentConfig.copy(scanDelayMillis = migratedValue as Long)
                                SettingsConstants.KEY_RESUME_SCANNING_FROM_START -> currentConfig.copy(resumeScanningFromStart = migratedValue as Boolean)
                                SettingsConstants.KEY_HOLDING_TIME_MILLIS -> currentConfig.copy(holdingTimeMillis = migratedValue as Long)
                                SettingsConstants.KEY_DEFAULT_SCAN_PATTERN -> currentConfig.copy(defaultScanPattern = migratedValue as String)
                                SettingsConstants.KEY_LIMIT_SCAN_CYCLES -> currentConfig.copy(limitScanCycles = migratedValue as Boolean)
                                SettingsConstants.KEY_SCAN_CYCLE_LIMIT -> currentConfig.copy(scanCycleLimit = migratedValue as Int)
                                SettingsConstants.KEY_STATIC_ROW_ENABLED -> currentConfig.copy(staticRowEnabled = migratedValue as Boolean)
                                SettingsConstants.KEY_LATE_CLICK_THRESHOLD_MILLIS -> currentConfig.copy(lateClickThresholdMillis = migratedValue as Long)
                                SettingsConstants.KEY_VOCAL_SWITCH_ENABLED -> currentConfig.copy(vocalSwitchEnabled = migratedValue as Boolean)
                                SettingsConstants.KEY_SWITCH_ACTIVATION_KEY -> currentConfig.copy(switchActivationKey = migratedValue as String)
                                else -> currentConfig
                            }
                            profileConfigUpdated = true
                        }
                    }
                    
                    editor.putBoolean("migration_scanning_settings_to_profile_done", true)
                    editor.apply()
                    
                    if (profileConfigUpdated && profile != null) {
                        val updatedProfile = profile.copy(config = currentConfig, updatedAt = System.currentTimeMillis())
                        settingsProfileDao.updateProfile(updatedProfile.toEntity(jsonSerializer))
                    }
                }
            } catch (e: Exception) {
                // Non-fatal bootstrapper/migration error
            }
        }

        val listener: (String) -> Unit = { key ->
            if (key !in SettingsMapper.NON_SYNCABLE_SETTINGS) {
                updateConfigLastModified(activeBookId)
            }
            if (!isApplyingProfile) {
                // If a syncable profile preference changes, write it back to the database
                scope.launch {
                    val activeId = activeProfileId
                    val profile = getProfileById(activeId)
                    if (profile != null) {
                        val currentConfig = ProfileConfig(
                            favoriteBookId = prefs.getString(SettingsConstants.KEY_FAVORITE_BOOK_ID, null),
                            startupBehavior = prefs.getString(SettingsConstants.KEY_STARTUP_BEHAVIOR, "BOOK_SELECTION") ?: "BOOK_SELECTION",
                            userModeScreenBehavior = prefs.getString(SettingsConstants.KEY_USER_MODE_SCREEN_BEHAVIOR, "GRID") ?: "GRID",
                            securityPinHash = prefs.getString(SettingsConstants.KEY_SECURITY_PIN_HASH, null),
                            securityPinSalt = prefs.getString(SettingsConstants.KEY_SECURITY_PIN_SALT, null),
                            securityPinTimeoutMinutes = prefs.getLong(SettingsConstants.KEY_SECURITY_PIN_TIMEOUT_MINUTES, 30L),
                            isPinRequiredForDeletion = prefs.getBoolean(SettingsConstants.KEY_IS_PIN_REQUIRED_FOR_DELETION, false),
                            isSecurityRequiredForEdit = prefs.getBoolean(SettingsConstants.KEY_SECURITY_REQUIRED_FOR_EDIT, false),
                            isSecurityRequiredForSettings = prefs.getBoolean(SettingsConstants.KEY_SECURITY_REQUIRED_FOR_SETTINGS, false),
                            isSecurityRequiredForAnalytics = prefs.getBoolean(SettingsConstants.KEY_SECURITY_REQUIRED_FOR_ANALYTICS, false),
                            elevenLabsApiKey = prefs.getString(SettingsConstants.KEY_ELEVENLABS_API_KEY, null),
                            geminiApiKey = prefs.getString(SettingsConstants.KEY_GEMINI_API_KEY, null),
                            useGeminiApiKey = prefs.getBoolean(SettingsConstants.KEY_USE_GEMINI_API_KEY, false),
                            autoStartScanning = prefs.getBoolean(SettingsConstants.KEY_AUTO_START_SCANNING, true),
                            scanDelayMillis = prefs.getLong(SettingsConstants.KEY_SCAN_DELAY_MILLIS, 3000L),
                            holdingTimeMillis = prefs.getLong(SettingsConstants.KEY_HOLDING_TIME_MILLIS, 250L),
                            resumeScanningFromStart = prefs.getBoolean(SettingsConstants.KEY_RESUME_SCANNING_FROM_START, true),
                            switchActivationKey = prefs.getString(SettingsConstants.KEY_SWITCH_ACTIVATION_KEY, "~3") ?: "~3",
                            volumeKeysActivate = prefs.getBoolean(SettingsConstants.KEY_VOLUME_KEYS_ACTIVATE, false),
                            defaultScanPattern = prefs.getString(SettingsConstants.KEY_DEFAULT_SCAN_PATTERN, "linear") ?: "linear",
                            limitScanCycles = prefs.getBoolean(SettingsConstants.KEY_LIMIT_SCAN_CYCLES, false),
                            scanCycleLimit = prefs.getInt(SettingsConstants.KEY_SCAN_CYCLE_LIMIT, 2),
                            staticRowEnabled = prefs.getBoolean(SettingsConstants.KEY_STATIC_ROW_ENABLED, false),
                            lateClickThresholdMillis = prefs.getLong(SettingsConstants.KEY_LATE_CLICK_THRESHOLD_MILLIS, 250L),
                            forceSoftKeyboard = prefs.getBoolean(SettingsConstants.KEY_FORCE_SOFT_KEYBOARD, true),
                            vocalSwitchEnabled = prefs.getBoolean(SettingsConstants.KEY_VOCAL_SWITCH_ENABLED, false),
                            ttsEngine = prefs.getString(SettingsConstants.KEY_TTS_ENGINE, null),
                            ttsLanguage = prefs.getString(SettingsConstants.KEY_TTS_LANGUAGE, null),
                            ttsVoiceName = prefs.getString(SettingsConstants.KEY_TTS_VOICE_NAME, null),
                            googleTtsLanguage = prefs.getString(SettingsConstants.KEY_GOOGLE_TTS_LANGUAGE, null),
                            googleTtsVoiceName = prefs.getString(SettingsConstants.KEY_GOOGLE_TTS_VOICE_NAME, null),
                            elevenLabsTtsLanguage = prefs.getString(SettingsConstants.KEY_ELEVENLABS_TTS_LANGUAGE, null),
                            elevenLabsTtsVoiceName = prefs.getString(SettingsConstants.KEY_ELEVENLABS_TTS_VOICE_NAME, null),
                            elevenLabsModel = prefs.getString(SettingsConstants.KEY_ELEVENLABS_MODEL, "eleven_multilingual_v2") ?: "eleven_multilingual_v2",
                            elevenLabsStability = prefs.getFloat(SettingsConstants.KEY_ELEVENLABS_STABILITY, 0.5f),
                            elevenLabsSimilarityBoost = prefs.getFloat(SettingsConstants.KEY_ELEVENLABS_SIMILARITY_BOOST, 0.75f),
                            ttsPlaybackSpeed = prefs.getFloat(SettingsConstants.KEY_TTS_PLAYBACK_SPEED, 1.0f),
                            isSmartPredictionEnabled = prefs.getBoolean(SettingsConstants.KEY_SMART_PREDICTION_ENABLED, false),
                            smartPredictionDelay = prefs.getLong(SettingsConstants.KEY_SMART_PREDICTION_DELAY, 2000L),
                            isGeminiEnabled = prefs.getBoolean(SettingsConstants.KEY_GEMINI_ENABLED, false),
                            useLocalGenerativeAi = prefs.getBoolean(SettingsConstants.KEY_USE_LOCAL_GENERATIVE_AI, true),
                            geminiRedoPrediction = prefs.getBoolean(SettingsConstants.KEY_GEMINI_REDO_PREDICTION, false),
                            geminiTimeout = prefs.getLong(SettingsConstants.KEY_GEMINI_TIMEOUT, 10000L),
                            maxCallDurationSeconds = prefs.getInt(SettingsConstants.KEY_MAX_CALL_DURATION_SECONDS, 300),
                            callDurationFeedbackIntervalSeconds = prefs.getInt(SettingsConstants.KEY_CALL_DURATION_FEEDBACK_INTERVAL_SECONDS, 60),
                            outgoingCallIntro = prefs.getString(SettingsConstants.KEY_OUTGOING_CALL_INTRO, "") ?: "",
                            incomingCallIntro = prefs.getString(SettingsConstants.KEY_INCOMING_CALL_INTRO, "") ?: "",
                            incomingCallScanLimitUserModeActive = prefs.getInt(SettingsConstants.KEY_INCOMING_CALL_SCAN_LIMIT_ACTIVE, 2),
                            incomingCallAutoActionUserModeActive = prefs.getString(SettingsConstants.KEY_INCOMING_CALL_AUTO_ACTION_ACTIVE, "NONE") ?: "NONE",
                            incomingCallDelayUserModeInactive = prefs.getInt(SettingsConstants.KEY_INCOMING_CALL_DELAY_INACTIVE, 10),
                            incomingCallAutoActionUserModeInactive = prefs.getString(SettingsConstants.KEY_INCOMING_CALL_AUTO_ACTION_INACTIVE, "NONE") ?: "NONE",
                            callAnnouncementAsCue = prefs.getBoolean(SettingsConstants.KEY_CALL_ANNOUNCEMENT_AS_CUE, false),
                            autoEnableSpeakerphone = prefs.getBoolean(SettingsConstants.KEY_CALL_AUTO_ENABLE_SPEAKERPHONE, true),
                            simulateCallsEnabled = prefs.getBoolean(SettingsConstants.KEY_SIMULATE_CALLS_ENABLED, false),
                            hangUpPressesRequired = prefs.getInt(SettingsConstants.KEY_CALL_HANG_UP_PRESSES_REQUIRED, 1),
                            filterCallsNotInContacts = prefs.getBoolean(SettingsConstants.KEY_FILTER_CALLS_NOT_IN_CONTACTS, false),
                            isNotificationReadingEnabled = prefs.getBoolean(SettingsConstants.KEY_NOTIFICATION_READING_ENABLED, false),
                            monitoredNotificationApps = prefs.getStringSet(SettingsConstants.KEY_MONITORED_NOTIFICATION_APPS, emptySet()) ?: emptySet(),
                            autoReadMode = prefs.getString(SettingsConstants.KEY_AUTO_READ_MODE, "OFF") ?: "OFF",
                            autoReadOnlyInUserMode = prefs.getBoolean(SettingsConstants.KEY_AUTO_READ_ONLY_IN_USER_MODE, true),
                            autoReadInStandby = prefs.getBoolean(SettingsConstants.KEY_AUTO_READ_IN_STANDBY, false),
                            themeMode = prefs.getString(SettingsConstants.KEY_THEME_MODE, "LIGHT") ?: "LIGHT",
                            appLanguage = prefs.getString(SettingsConstants.KEY_APP_LANGUAGE, null),
                            pageSortOrder = prefs.getString(SettingsConstants.KEY_PAGE_SORT_ORDER, "MANUAL") ?: "MANUAL",
                            templateSortOrder = prefs.getString(SettingsConstants.KEY_TEMPLATE_SORT_ORDER, "MANUAL") ?: "MANUAL",
                            keepScreenOnUserMode = prefs.getBoolean(SettingsConstants.KEY_KEEP_SCREEN_ON_USER_MODE, true),
                            actionLogLimit = prefs.getInt(SettingsConstants.KEY_ACTION_LOG_LIMIT, 100),
                            persistActionLogs = prefs.getBoolean(SettingsConstants.KEY_PERSIST_ACTION_LOGS, true),
                            showPageIdInLog = prefs.getBoolean(SettingsConstants.KEY_SHOW_PAGE_ID_IN_LOG, false),
                            onlyRecordHardwareStats = prefs.getBoolean(SettingsConstants.KEY_ONLY_RECORD_HARDWARE_STATS, false),
                            statsRetentionDays = prefs.getInt(SettingsConstants.KEY_STATS_RETENTION_DAYS, 30),
                            statsAggregationHours = prefs.getInt(SettingsConstants.KEY_STATS_AGGREGATION_HOURS, 24),
                            actionLogsStorage = prefs.getString(SettingsConstants.KEY_ACTION_LOGS_STORAGE, null),
                            syncLogsStorage = prefs.getString(SettingsConstants.KEY_SYNC_LOGS_STORAGE, null),
                            weatherCacheTimeout = prefs.getLong(SettingsConstants.KEY_WEATHER_CACHE_TIMEOUT, 60L),
                            backgroundLocationEnabled = prefs.getBoolean(SettingsConstants.KEY_BACKGROUND_LOCATION_ENABLED, false),
                            backgroundLocationInterval = prefs.getLong(SettingsConstants.KEY_BACKGROUND_LOCATION_INTERVAL, 4L),
                            backgroundWeatherEnabled = prefs.getBoolean(SettingsConstants.KEY_BACKGROUND_WEATHER_ENABLED, false),
                            backgroundWeatherInterval = prefs.getLong(SettingsConstants.KEY_BACKGROUND_WEATHER_INTERVAL, 4L),
                            syncIntervalMinutes = prefs.getLong(SettingsConstants.KEY_SYNC_INTERVAL_MINUTES, 60L),
                            syncModeBook = prefs.getString(SettingsConstants.KEY_SYNC_MODE_BOOK, "TWO_WAY") ?: "TWO_WAY",
                            syncModeStats = prefs.getString(SettingsConstants.KEY_SYNC_MODE_STATS, "RESTORE_ONLY") ?: "RESTORE_ONLY",
                            syncModeTts = prefs.getString(SettingsConstants.KEY_SYNC_MODE_TTS, "TWO_WAY") ?: "TWO_WAY",
                            syncModeLogs = prefs.getString(SettingsConstants.KEY_SYNC_MODE_LOGS, "TWO_WAY") ?: "TWO_WAY",
                            syncLogsIntervalHours = prefs.getLong(SettingsConstants.KEY_SYNC_LOGS_INTERVAL_HOURS, 24L),
                            preferredMainSpeakerName = prefs.getString("preferred_main_speaker_name", null),
                            preferredCueSpeakerName = prefs.getString("preferred_cue_speaker_name", null),
                            fallbackToInternalAudio = prefs.getBoolean("fallback_to_internal_audio", true),
                            logIgnoredActions = prefs.getBoolean(SettingsConstants.KEY_LOG_IGNORED_ACTIONS, false),
                            bluetoothDelay = prefs.getLong(SettingsConstants.KEY_BLUETOOTH_DELAY, 100L),
                            hueBridgeIp = prefs.getString(SettingsConstants.KEY_HUE_BRIDGE_IP, "") ?: "",
                            hueUsername = prefs.getString(SettingsConstants.KEY_HUE_USERNAME, "") ?: "",
                            hueBridgeFingerprint = prefs.getString(SettingsConstants.KEY_HUE_BRIDGE_FINGERPRINT, "") ?: "",
                            hueCachedDevices = prefs.getString(SettingsConstants.KEY_HUE_CACHED_DEVICES, "") ?: "",
                            speakerVolume = prefs.getInt(SettingsConstants.KEY_SPEAKER_VOLUME, 100),
                            headphoneVolume = prefs.getInt(SettingsConstants.KEY_HEADPHONE_VOLUME, 100),
                            blockVolumeKeys = prefs.getBoolean(SettingsConstants.KEY_BLOCK_VOLUME_KEYS, false),
                            isCloudSyncEnabled = prefs.getBoolean(SettingsConstants.KEY_CLOUD_SYNC_ENABLED, false),
                            googleDriveFolderId = prefs.getString(SettingsConstants.KEY_GOOGLE_DRIVE_FOLDER_ID, null),
                            googleDriveFolderName = prefs.getString(SettingsConstants.KEY_GOOGLE_DRIVE_FOLDER_NAME, null)
                        )
                        val updatedProfile = profile.copy(config = currentConfig, updatedAt = System.currentTimeMillis())
                        updateProfile(updatedProfile)
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
    
    

    // --- FeatureSettings ---
    override val appLanguageFlow: StateFlow<String?> get() = generalSettings.appLanguageFlow
    override val isVocalSwitchEnabledFlow: StateFlow<Boolean> get() = scanningSettings.vocalSwitchEnabledFlow

    
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
    override val autoReadModeFlow: StateFlow<com.andreas_kratzer.ghosttalk.core.settings.AutoReadMode> get() = notificationSettings.autoReadModeFlow
    override val autoReadOnlyInUserModeFlow: StateFlow<Boolean> get() = notificationSettings.autoReadOnlyInUserModeFlow
    override val autoReadInStandbyFlow: StateFlow<Boolean> get() = notificationSettings.autoReadInStandbyFlow
    
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
    override val preferredMainSpeakerNameFlow: StateFlow<String?> get() = voiceSettings.preferredMainSpeakerNameFlow
    override val preferredCueSpeakerNameFlow: StateFlow<String?> get() = voiceSettings.preferredCueSpeakerNameFlow
    override val isCloudSyncEnabledFlow: StateFlow<Boolean> get() = cloudSettings.isCloudSyncEnabledFlow
    override val lateClickThresholdFlow: StateFlow<Long> get() = scanningSettings.lateClickThresholdFlow

    override val syncIntervalMinutesFlow: StateFlow<Long> get() = cloudSettings.syncIntervalMinutesFlow
    override val syncModeBookFlow: StateFlow<String> get() = cloudSettings.syncModeBookFlow
    override val syncModeTtsFlow: StateFlow<String> get() = cloudSettings.syncModeTtsFlow
    override val syncModeStatsFlow: StateFlow<String> get() = cloudSettings.syncModeStatsFlow
    override val syncModeSettingsFlow: StateFlow<String> get() = cloudSettings.syncModeSettingsFlow
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

    override val googleAuthTypeFlow: StateFlow<CloudAuthType> get() = cloudSettings.googleAuthTypeFlow
    override val googleAccessTokenFlow: StateFlow<String?> get() = cloudSettings.googleAccessTokenFlow
    override val googleRefreshTokenFlow: StateFlow<String?> get() = cloudSettings.googleRefreshTokenFlow
    override val googleTokenExpiresAtFlow: StateFlow<Long> get() = cloudSettings.googleTokenExpiresAtFlow
    override val googleUserEmailFlow: StateFlow<String?> get() = cloudSettings.googleUserEmailFlow
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

    override var preferredMainSpeakerName: String?
        get() = voiceSettings.preferredMainSpeakerName
        set(value) { voiceSettings.preferredMainSpeakerName = value }

    override var preferredCueSpeakerName: String?
        get() = voiceSettings.preferredCueSpeakerName
        set(value) { voiceSettings.preferredCueSpeakerName = value }

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
    override var syncModeSettings: String
        get() = cloudSettings.syncModeSettings
        set(value) { cloudSettings.syncModeSettings = value }
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

    override var autoReadMode: com.andreas_kratzer.ghosttalk.core.settings.AutoReadMode
        get() = notificationSettings.autoReadMode
        set(value) { notificationSettings.autoReadMode = value }

    override var autoReadOnlyInUserMode: Boolean
        get() = notificationSettings.autoReadOnlyInUserMode
        set(value) { notificationSettings.autoReadOnlyInUserMode = value }

    override var autoReadInStandby: Boolean
        get() = notificationSettings.autoReadInStandby
        set(value) { notificationSettings.autoReadInStandby = value }

    override var appLanguage: String?
        get() = generalSettings.appLanguage
        set(value) { generalSettings.appLanguage = value }

    override var isVocalSwitchEnabled: Boolean
        get() = scanningSettings.vocalSwitchEnabled
        set(value) { scanningSettings.vocalSwitchEnabled = value }
    
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

    override var googleAuthType: CloudAuthType
        get() = cloudSettings.googleAuthType
        set(value) { cloudSettings.googleAuthType = value }

    override var googleAccessToken: String?
        get() = cloudSettings.googleAccessToken
        set(value) { cloudSettings.googleAccessToken = value }

    override var googleRefreshToken: String?
        get() = cloudSettings.googleRefreshToken
        set(value) { cloudSettings.googleRefreshToken = value }

    override var googleTokenExpiresAt: Long
        get() = cloudSettings.googleTokenExpiresAt
        set(value) { cloudSettings.googleTokenExpiresAt = value }

    override var googleUserEmail: String?
        get() = cloudSettings.googleUserEmail
        set(value) { cloudSettings.googleUserEmail = value }


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

    override fun getAllProfilesFlow(): kotlinx.coroutines.flow.Flow<List<SettingsProfile>> {
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
            editor.putString(SettingsConstants.KEY_SECURITY_PIN_HASH, config.securityPinHash)
            editor.putString(SettingsConstants.KEY_SECURITY_PIN_SALT, config.securityPinSalt)
            editor.putLong(SettingsConstants.KEY_SECURITY_PIN_TIMEOUT_MINUTES, config.securityPinTimeoutMinutes)
            editor.putBoolean(SettingsConstants.KEY_IS_PIN_REQUIRED_FOR_DELETION, config.isPinRequiredForDeletion)
            editor.putBoolean(SettingsConstants.KEY_SECURITY_REQUIRED_FOR_EDIT, config.isSecurityRequiredForEdit)
            editor.putBoolean(SettingsConstants.KEY_SECURITY_REQUIRED_FOR_SETTINGS, config.isSecurityRequiredForSettings)
            editor.putBoolean(SettingsConstants.KEY_SECURITY_REQUIRED_FOR_ANALYTICS, config.isSecurityRequiredForAnalytics)
            editor.putString(SettingsConstants.KEY_ELEVENLABS_API_KEY, config.elevenLabsApiKey)
            editor.putString(SettingsConstants.KEY_GEMINI_API_KEY, config.geminiApiKey)
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
            editor.putBoolean(SettingsConstants.KEY_USE_LOCAL_GENERATIVE_AI, config.useLocalGenerativeAi)
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
            editor.putInt(SettingsConstants.KEY_STATS_RETENTION_DAYS, config.statsRetentionDays)
            editor.putInt(SettingsConstants.KEY_STATS_AGGREGATION_HOURS, config.statsAggregationHours)
            editor.putString(SettingsConstants.KEY_ACTION_LOGS_STORAGE, config.actionLogsStorage)
            editor.putString(SettingsConstants.KEY_SYNC_LOGS_STORAGE, config.syncLogsStorage)
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
            editor.putString(SettingsConstants.KEY_HUE_USERNAME, config.hueUsername)
            editor.putString(SettingsConstants.KEY_HUE_BRIDGE_FINGERPRINT, config.hueBridgeFingerprint)
            editor.putString(SettingsConstants.KEY_HUE_CACHED_DEVICES, config.hueCachedDevices)
            editor.putInt(SettingsConstants.KEY_SPEAKER_VOLUME, config.speakerVolume)
            editor.putInt(SettingsConstants.KEY_HEADPHONE_VOLUME, config.headphoneVolume)
            editor.putBoolean(SettingsConstants.KEY_BLOCK_VOLUME_KEYS, config.blockVolumeKeys)
            editor.putBoolean(SettingsConstants.KEY_CLOUD_SYNC_ENABLED, config.isCloudSyncEnabled)
            editor.putString(SettingsConstants.KEY_GOOGLE_DRIVE_FOLDER_ID, config.googleDriveFolderId)
            editor.putString(SettingsConstants.KEY_GOOGLE_DRIVE_FOLDER_NAME, config.googleDriveFolderName)
            editor.apply()

            refreshFlows()
        } finally {
            isApplyingProfile = false
        }
    }
}

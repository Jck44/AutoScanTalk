package com.andreas_kratzer.ghosttalk.core.data.impl.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.andreas_kratzer.ghosttalk.core.database.SettingsProfileDao
import com.andreas_kratzer.ghosttalk.core.database.SettingsProfileEntity
import com.andreas_kratzer.ghosttalk.core.model.ProfileConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileBootstrapper @Inject constructor(
    private val settingsProfileDao: SettingsProfileDao,
    private val context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = true }
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(SettingsConstants.PREFS_NAME, Context.MODE_PRIVATE)

    @SuppressLint("HardwareIds")
    suspend fun bootstrapIfNeeded() = withContext(Dispatchers.IO) {
        val currentProfileId = prefs.getString("local_active_profile_id", null)
        if (currentProfileId == null || settingsProfileDao.getAllProfiles().isEmpty()) {
            val androidId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: ""
            val deviceId = if (androidId.isNotBlank()) androidId else UUID.randomUUID().toString().substring(0, 8)
            val newUuid = "profile_${UUID.randomUUID()}"
            val profileName = "Profile $deviceId"

            // Read the syncable settings directly using the keys
            val legacyConfig = ProfileConfig(
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
                staticRowScanPattern = prefs.getString(SettingsConstants.KEY_STATIC_ROW_SCAN_PATTERN, "linear") ?: "linear",
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
                syncLogsIntervalHours = prefs.getLong(SettingsConstants.KEY_SYNC_LOGS_INTERVAL_HOURS, 24L)
            )

            val configJson = json.encodeToString(ProfileConfig.serializer(), legacyConfig)
            val defaultEntity = SettingsProfileEntity(
                id = newUuid,
                name = profileName,
                configJson = configJson,
                profileVersionSequence = 1L,
                updatedAt = System.currentTimeMillis()
            )

            settingsProfileDao.insertProfile(defaultEntity)

            prefs.edit {
                putString("local_active_profile_id", newUuid)
                putString("cached_active_profile_config", configJson)
            }
        }
    }
}

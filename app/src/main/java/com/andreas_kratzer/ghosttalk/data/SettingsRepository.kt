package com.andreas_kratzer.ghosttalk.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@SuppressLint("CommitPrefEdits", "ApplySharedPref", "UseKtx")
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _activeBookIdFlow = MutableStateFlow(prefs.getString(KEY_ACTIVE_BOOK_ID, "book-default") ?: "book-default")
    val activeBookIdFlow: StateFlow<String> = _activeBookIdFlow.asStateFlow()

    var activeBookId: String
        get() = _activeBookIdFlow.value
        set(value) {
            _activeBookIdFlow.value = value
            prefs.edit().putString(KEY_ACTIVE_BOOK_ID, value).apply()
            refreshFlows()
        }

    // ── Scoped SharedPreferences helpers ──────────────────────────────────

    private fun getScopedKey(key: String): String = "${activeBookId}_$key"

    private fun getStringScoped(key: String, defaultValue: String? = null): String? {
        val scopedKey = getScopedKey(key)
        if (prefs.contains(scopedKey)) {
            return prefs.getString(scopedKey, defaultValue)
        }
        return prefs.getString(key, defaultValue)
    }

    private fun getBooleanScoped(key: String, defaultValue: Boolean): Boolean {
        val scopedKey = getScopedKey(key)
        if (prefs.contains(scopedKey)) {
            return prefs.getBoolean(scopedKey, defaultValue)
        }
        return prefs.getBoolean(key, defaultValue)
    }

    private fun getLongScoped(key: String, defaultValue: Long): Long {
        val scopedKey = getScopedKey(key)
        if (prefs.contains(scopedKey)) {
            return prefs.getLong(scopedKey, defaultValue)
        }
        return prefs.getLong(key, defaultValue)
    }

    private fun getFloatScoped(key: String, defaultValue: Float): Float {
        val scopedKey = getScopedKey(key)
        if (prefs.contains(scopedKey)) {
            return prefs.getFloat(scopedKey, defaultValue)
        }
        return prefs.getFloat(key, defaultValue)
    }

    private fun getStringSetScoped(key: String, defaultValue: Set<String>? = null): Set<String>? {
        val scopedKey = getScopedKey(key)
        if (prefs.contains(scopedKey)) {
            return prefs.getStringSet(scopedKey, defaultValue)
        }
        return prefs.getStringSet(key, defaultValue)
    }

    private fun putStringScoped(key: String, value: String?) {
        prefs.edit().putString(getScopedKey(key), value).apply()
    }

    private fun putBooleanScoped(key: String, value: Boolean) {
        prefs.edit().putBoolean(getScopedKey(key), value).apply()
    }

    private fun putLongScoped(key: String, value: Long) {
        prefs.edit().putLong(getScopedKey(key), value).apply()
    }

    private fun putFloatScoped(key: String, value: Float) {
        prefs.edit().putFloat(getScopedKey(key), value).apply()
    }

    private fun putStringSetScoped(key: String, value: Set<String>?) {
        prefs.edit().putStringSet(getScopedKey(key), value).apply()
    }

    // ── ObservableSetting: eliminates per-setting boilerplate ─────────────
    //
    // Each ObservableSetting encapsulates:
    //   - A MutableStateFlow that emits the current value
    //   - A public StateFlow for observing
    //   - read()/write() methods for SharedPreferences
    //   - refresh() to re-read when activeBookId changes
    //
    // This replaces the old pattern of 8+ lines per setting.

    private inner class StringSetting(
        private val key: String,
        private val default: String? = null
    ) {
        private val _flow = MutableStateFlow(getStringScoped(key, default))
        val flow: StateFlow<String?> = _flow.asStateFlow()

        var value: String?
            get() = getStringScoped(key, default)
            set(v) {
                putStringScoped(key, v)
                _flow.value = v
            }

        fun refresh() { _flow.value = value }
    }

    private inner class NonNullStringSetting(
        private val key: String,
        private val default: String
    ) {
        private val _flow = MutableStateFlow(getStringScoped(key, default) ?: default)
        val flow: StateFlow<String> = _flow.asStateFlow()

        var value: String
            get() = getStringScoped(key, default) ?: default
            set(v) {
                putStringScoped(key, v)
                _flow.value = v
            }

        fun refresh() { _flow.value = value }
    }

    private inner class BooleanSetting(
        private val key: String,
        private val default: Boolean
    ) {
        private val _flow = MutableStateFlow(getBooleanScoped(key, default))
        val flow: StateFlow<Boolean> = _flow.asStateFlow()

        var value: Boolean
            get() = getBooleanScoped(key, default)
            set(v) {
                putBooleanScoped(key, v)
                _flow.value = v
            }

        fun refresh() { _flow.value = value }
    }

    private inner class LongSetting(
        private val key: String,
        private val default: Long
    ) {
        private val _flow = MutableStateFlow(getLongScoped(key, default))
        val flow: StateFlow<Long> = _flow.asStateFlow()

        var value: Long
            get() = getLongScoped(key, default)
            set(v) {
                putLongScoped(key, v)
                _flow.value = v
            }

        fun refresh() { _flow.value = value }
    }

    private inner class FloatSetting(
        private val key: String,
        private val default: Float,
        private val coerce: ((Float) -> Float)? = null
    ) {
        private val _flow = MutableStateFlow(getFloatScoped(key, default))
        val flow: StateFlow<Float> = _flow.asStateFlow()

        var value: Float
            get() = getFloatScoped(key, default)
            set(v) {
                val coerced = coerce?.invoke(v) ?: v
                putFloatScoped(key, coerced)
                _flow.value = coerced
            }

        fun refresh() { _flow.value = value }
    }

    private inner class StringSetSetting(
        private val key: String,
        private val default: Set<String> = emptySet()
    ) {
        private val _flow = MutableStateFlow(getStringSetScoped(key, default) ?: default)
        val flow: StateFlow<Set<String>> = _flow.asStateFlow()

        var value: Set<String>
            get() = getStringSetScoped(key, default) ?: default
            set(v) {
                putStringSetScoped(key, v)
                _flow.value = v
            }

        fun refresh() { _flow.value = value }
    }

    // ── Observable settings (have a StateFlow for UI observation) ─────────

    private val _ttsLanguage = StringSetting(KEY_TTS_LANGUAGE)
    private val _ttsVoiceName = StringSetting(KEY_TTS_VOICE_NAME)
    private val _autoStartScanning = BooleanSetting(KEY_AUTO_START_SCANNING, true)
    private val _scanDelay = LongSetting(KEY_SCAN_DELAY_MILLIS, 3000L)
    private val _resumeScanningFromStart = BooleanSetting(KEY_RESUME_SCANNING_FROM_START, true)
    private val _defaultStartPageId = StringSetting(KEY_DEFAULT_START_PAGE_ID)
    private val _ttsAudioDeviceAddress = StringSetting(KEY_TTS_AUDIO_DEVICE)
    private val _cuesAudioDeviceAddress = StringSetting(KEY_CUES_AUDIO_DEVICE)
    private val _holdingTimeMillis = LongSetting(KEY_HOLDING_TIME_MILLIS, 0L)
    private val _persistActionLogs = BooleanSetting(KEY_PERSIST_ACTION_LOGS, false)
    private val _actionLogsStorage = StringSetting(KEY_ACTION_LOGS_STORAGE)
    private val _switchActivationKey = NonNullStringSetting(KEY_SWITCH_ACTIVATION_KEY, "Space")
    private val _volumeKeysActivate = BooleanSetting(KEY_VOLUME_KEYS_ACTIVATE, false)
    private val _showTestButtons = BooleanSetting(KEY_SHOW_TEST_BUTTONS, false)
    private val _defaultScanPattern = NonNullStringSetting(KEY_DEFAULT_SCAN_PATTERN, "linear")
    private val _themeMode = NonNullStringSetting(KEY_THEME_MODE, "SYSTEM")
    private val _pageSortOrder = NonNullStringSetting(KEY_PAGE_SORT_ORDER, "MANUAL")
    private val _templateSortOrder = NonNullStringSetting(KEY_TEMPLATE_SORT_ORDER, "MANUAL")
    private val _lastSuccessfulSyncTime = LongSetting(KEY_LAST_SYNC_TIME, 0L)
    private val _experimentalManualSorting = BooleanSetting(KEY_EXPERIMENTAL_MANUAL_SORTING, false)
    private val _smartPredictionDelay = LongSetting(KEY_SMART_PREDICTION_DELAY, 2000L)
    private val _isSmartPredictionEnabled = BooleanSetting(KEY_SMART_PREDICTION_ENABLED, false)
    private val _bluetoothDelay = LongSetting(KEY_BLUETOOTH_DELAY, 1500L)
    private val _ttsVolume = FloatSetting(KEY_TTS_VOLUME_MULTIPLIER, 1.0f) { it.coerceIn(0.0f, 1.0f) }
    private val _cuesVolume = FloatSetting(KEY_CUES_VOLUME_MULTIPLIER, 1.0f) { it.coerceIn(0.0f, 1.0f) }
    private val _isCloudSyncEnabled = BooleanSetting(KEY_CLOUD_SYNC_ENABLED, false)
    private val _syncIntervalMinutes = LongSetting(KEY_SYNC_INTERVAL_MINUTES, 15L)
    private val _syncMode = NonNullStringSetting(KEY_SYNC_MODE, "TWO_WAY")
    private val _isGeminiEnabled = BooleanSetting(KEY_GEMINI_ENABLED, false)
    private val _useLocalGenerativeAi = BooleanSetting(KEY_USE_LOCAL_GENERATIVE_AI, false)
    private val _showPageIdInLog = BooleanSetting(KEY_SHOW_PAGE_ID_IN_LOG, false)
    private val _isNotificationReadingEnabled = BooleanSetting(KEY_NOTIFICATION_READING_ENABLED, false)
    private val _monitoredNotificationApps = StringSetSetting(KEY_MONITORED_NOTIFICATION_APPS)
    private val _appLanguage = StringSetting(KEY_APP_LANGUAGE)
    private val _keepScreenOnUserMode = BooleanSetting(KEY_KEEP_SCREEN_ON_USER_MODE, true)
    private val _userModeScreenBehavior = NonNullStringSetting(KEY_USER_MODE_SCREEN_BEHAVIOR, "NORMAL")
    private val _geminiTimeout = LongSetting(KEY_GEMINI_TIMEOUT, 6000L)
    private val _geminiRedoPrediction = BooleanSetting(KEY_GEMINI_REDO_PREDICTION, false)
    private val _weatherCacheTimeout = LongSetting(KEY_WEATHER_CACHE_TIMEOUT, 60L)

    private fun refreshFlows() {
        _ttsLanguage.refresh()
        _ttsVoiceName.refresh()
        _autoStartScanning.refresh()
        _scanDelay.refresh()
        _resumeScanningFromStart.refresh()
        _defaultStartPageId.refresh()
        _ttsAudioDeviceAddress.refresh()
        _cuesAudioDeviceAddress.refresh()
        _holdingTimeMillis.refresh()
        _persistActionLogs.refresh()
        _actionLogsStorage.refresh()
        _switchActivationKey.refresh()
        _volumeKeysActivate.refresh()
        _showTestButtons.refresh()
        _defaultScanPattern.refresh()
        _themeMode.refresh()
        _pageSortOrder.refresh()
        _templateSortOrder.refresh()
        _lastSuccessfulSyncTime.refresh()
        _experimentalManualSorting.refresh()
        _smartPredictionDelay.refresh()
        _isSmartPredictionEnabled.refresh()
        _bluetoothDelay.refresh()
        _ttsVolume.refresh()
        _cuesVolume.refresh()
        _isCloudSyncEnabled.refresh()
        _syncIntervalMinutes.refresh()
        _syncMode.refresh()
        _isGeminiEnabled.refresh()
        _useLocalGenerativeAi.refresh()
        _showPageIdInLog.refresh()
        _isNotificationReadingEnabled.refresh()
        _monitoredNotificationApps.refresh()
        _appLanguage.refresh()
        _keepScreenOnUserMode.refresh()
        _userModeScreenBehavior.refresh()
        _geminiTimeout.refresh()
        _geminiRedoPrediction.refresh()
        _weatherCacheTimeout.refresh()
    }

    // ── Public API: Flows ────────────────────────────────────────────────

    val ttsLanguageFlow: StateFlow<String?> get() = _ttsLanguage.flow
    val ttsVoiceNameFlow: StateFlow<String?> get() = _ttsVoiceName.flow
    val autoStartScanningFlow: StateFlow<Boolean> get() = _autoStartScanning.flow
    val scanDelayFlow: StateFlow<Long> get() = _scanDelay.flow
    val resumeScanningFromStartFlow: StateFlow<Boolean> get() = _resumeScanningFromStart.flow
    val defaultStartPageIdFlow: StateFlow<String?> get() = _defaultStartPageId.flow
    val ttsAudioDeviceAddressFlow: StateFlow<String?> get() = _ttsAudioDeviceAddress.flow
    val cuesAudioDeviceAddressFlow: StateFlow<String?> get() = _cuesAudioDeviceAddress.flow
    val holdingTimeMillisFlow: StateFlow<Long> get() = _holdingTimeMillis.flow
    val persistActionLogsFlow: StateFlow<Boolean> get() = _persistActionLogs.flow
    val actionLogsStorageFlow: StateFlow<String?> get() = _actionLogsStorage.flow
    val switchActivationKeyFlow: StateFlow<String> get() = _switchActivationKey.flow
    val volumeKeysActivateFlow: StateFlow<Boolean> get() = _volumeKeysActivate.flow
    val showTestButtonsFlow: StateFlow<Boolean> get() = _showTestButtons.flow
    val defaultScanPatternFlow: StateFlow<String> get() = _defaultScanPattern.flow
    val themeModeFlow: StateFlow<String> get() = _themeMode.flow
    val pageSortOrderFlow: StateFlow<String> get() = _pageSortOrder.flow
    val templateSortOrderFlow: StateFlow<String> get() = _templateSortOrder.flow
    val lastSuccessfulSyncTimeFlow: StateFlow<Long> get() = _lastSuccessfulSyncTime.flow
    val experimentalManualSortingFlow: StateFlow<Boolean> get() = _experimentalManualSorting.flow
    val isSmartPredictionEnabledFlow: StateFlow<Boolean> get() = _isSmartPredictionEnabled.flow
    val bluetoothDelayFlow: StateFlow<Long> get() = _bluetoothDelay.flow
    val ttsVolumeMultiplierFlow: StateFlow<Float> get() = _ttsVolume.flow
    val cuesVolumeMultiplierFlow: StateFlow<Float> get() = _cuesVolume.flow
    val isCloudSyncEnabledFlow: StateFlow<Boolean> get() = _isCloudSyncEnabled.flow
    val syncIntervalMinutesFlow: StateFlow<Long> get() = _syncIntervalMinutes.flow
    val syncModeFlow: StateFlow<String> get() = _syncMode.flow
    val isGeminiEnabledFlow: StateFlow<Boolean> get() = _isGeminiEnabled.flow
    val useLocalGenerativeAiFlow: StateFlow<Boolean> get() = _useLocalGenerativeAi.flow
    val showPageIdInLogFlow: StateFlow<Boolean> get() = _showPageIdInLog.flow
    val isNotificationReadingEnabledFlow: StateFlow<Boolean> get() = _isNotificationReadingEnabled.flow
    val monitoredNotificationAppsFlow: StateFlow<Set<String>> get() = _monitoredNotificationApps.flow
    val appLanguageFlow: StateFlow<String?> get() = _appLanguage.flow
    val keepScreenOnUserModeFlow: StateFlow<Boolean> get() = _keepScreenOnUserMode.flow
    val userModeScreenBehaviorFlow: StateFlow<String> get() = _userModeScreenBehavior.flow
    val geminiTimeoutFlow: StateFlow<Long> get() = _geminiTimeout.flow
    val geminiRedoPredictionFlow: StateFlow<Boolean> get() = _geminiRedoPrediction.flow
    val weatherCacheTimeoutFlow: StateFlow<Long> get() = _weatherCacheTimeout.flow

    // ── Public API: Properties ───────────────────────────────────────────

    var ttsLanguage: String?
        get() = _ttsLanguage.value
        set(value) { _ttsLanguage.value = value }

    var ttsVoiceName: String?
        get() = _ttsVoiceName.value
        set(value) { _ttsVoiceName.value = value }

    var autoStartScanning: Boolean
        get() = _autoStartScanning.value
        set(value) { _autoStartScanning.value = value }

    var scanDelayMillis: Long
        get() = _scanDelay.value
        set(value) { _scanDelay.value = value }

    var resumeScanningFromStart: Boolean
        get() = _resumeScanningFromStart.value
        set(value) { _resumeScanningFromStart.value = value }

    var defaultStartPageId: String?
        get() = _defaultStartPageId.value
        set(value) { _defaultStartPageId.value = value }

    var ttsAudioDeviceAddress: String?
        get() = _ttsAudioDeviceAddress.value
        set(value) { _ttsAudioDeviceAddress.value = value }

    var cuesAudioDeviceAddress: String?
        get() = _cuesAudioDeviceAddress.value
        set(value) { _cuesAudioDeviceAddress.value = value }

    var holdingTimeMillis: Long
        get() = _holdingTimeMillis.value
        set(value) { _holdingTimeMillis.value = value }

    var persistActionLogs: Boolean
        get() = _persistActionLogs.value
        set(value) { _persistActionLogs.value = value }

    var actionLogsStorage: String?
        get() = _actionLogsStorage.value
        set(value) { _actionLogsStorage.value = value }

    var switchActivationKey: String
        get() = _switchActivationKey.value
        set(value) { _switchActivationKey.value = value }

    var volumeKeysActivate: Boolean
        get() = _volumeKeysActivate.value
        set(value) { _volumeKeysActivate.value = value }

    var showTestButtons: Boolean
        get() = _showTestButtons.value
        set(value) { _showTestButtons.value = value }

    var defaultScanPattern: String
        get() = _defaultScanPattern.value
        set(value) { _defaultScanPattern.value = value }

    var themeMode: String
        get() = _themeMode.value
        set(value) { _themeMode.value = value }

    var pageSortOrder: String
        get() = _pageSortOrder.value
        set(value) { _pageSortOrder.value = value }

    var templateSortOrder: String
        get() = _templateSortOrder.value
        set(value) { _templateSortOrder.value = value }

    var lastSuccessfulSyncTime: Long
        get() = _lastSuccessfulSyncTime.value
        set(value) { _lastSuccessfulSyncTime.value = value }

    var experimentalManualSorting: Boolean
        get() = _experimentalManualSorting.value
        set(value) { _experimentalManualSorting.value = value }

    var smartPredictionDelayMillis: Long
        get() = _smartPredictionDelay.value
        set(value) { _smartPredictionDelay.value = value }

    var isSmartPredictionEnabled: Boolean
        get() = _isSmartPredictionEnabled.value
        set(value) { _isSmartPredictionEnabled.value = value }

    var bluetoothDelay: Long
        get() = _bluetoothDelay.value
        set(value) { _bluetoothDelay.value = value }

    var ttsVolumeMultiplier: Float
        get() = _ttsVolume.value
        set(value) { _ttsVolume.value = value }

    var cuesVolumeMultiplier: Float
        get() = _cuesVolume.value
        set(value) { _cuesVolume.value = value }

    var isCloudSyncEnabled: Boolean
        get() = _isCloudSyncEnabled.value
        set(value) { _isCloudSyncEnabled.value = value }

    var syncIntervalMinutes: Long
        get() = _syncIntervalMinutes.value
        set(value) { _syncIntervalMinutes.value = value }

    var syncMode: String
        get() = _syncMode.value
        set(value) { _syncMode.value = value }

    var isGeminiEnabled: Boolean
        get() = _isGeminiEnabled.value
        set(value) { _isGeminiEnabled.value = value }

    var useLocalGenerativeAi: Boolean
        get() = _useLocalGenerativeAi.value
        set(value) { _useLocalGenerativeAi.value = value }

    var showPageIdInLog: Boolean
        get() = _showPageIdInLog.value
        set(value) { _showPageIdInLog.value = value }

    var isNotificationReadingEnabled: Boolean
        get() = _isNotificationReadingEnabled.value
        set(value) { _isNotificationReadingEnabled.value = value }

    var monitoredNotificationApps: Set<String>
        get() = _monitoredNotificationApps.value
        set(value) { _monitoredNotificationApps.value = value }

    var appLanguage: String?
        get() = _appLanguage.value
        set(value) { _appLanguage.value = value }
    
    var initialTemplatesCreated: Boolean
        get() = prefs.getBoolean(KEY_INITIAL_TEMPLATES_CREATED, false)
        set(value) { prefs.edit().putBoolean(KEY_INITIAL_TEMPLATES_CREATED, value).apply() }

    var keepScreenOnUserMode: Boolean
        get() = _keepScreenOnUserMode.value
        set(value) { _keepScreenOnUserMode.value = value }

    var userModeScreenBehavior: String
        get() = _userModeScreenBehavior.value
        set(value) { _userModeScreenBehavior.value = value }

    var geminiTimeout: Long
        get() = _geminiTimeout.value
        set(value) { _geminiTimeout.value = value }

    var geminiRedoPrediction: Boolean
        get() = _geminiRedoPrediction.value
        set(value) { _geminiRedoPrediction.value = value }

    var weatherCacheTimeout: Long
        get() = _weatherCacheTimeout.value
        set(value) { _weatherCacheTimeout.value = value }

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

    companion object {
        private const val PREFS_NAME = "ghosttalk_settings"
        private const val KEY_TTS_LANGUAGE = "tts_language"
        private const val KEY_TTS_VOICE_NAME = "tts_voice_name"
        private const val KEY_AUTO_START_SCANNING = "auto_start_scanning"
        private const val KEY_SCAN_DELAY_MILLIS = "scan_delay_millis"
        private const val KEY_RESUME_SCANNING_FROM_START = "resume_scanning_from_start"
        private const val KEY_DEFAULT_START_PAGE_ID = "default_start_page_id"
        private const val KEY_TTS_AUDIO_DEVICE = "tts_audio_device_address"
        private const val KEY_CUES_AUDIO_DEVICE = "cues_audio_device_address"
        private const val KEY_PERSIST_ACTION_LOGS = "persist_action_logs"
        private const val KEY_ACTION_LOGS_STORAGE = "action_logs_storage"
        private const val KEY_SWITCH_ACTIVATION_KEY = "switch_activation_key"
        private const val KEY_VOLUME_KEYS_ACTIVATE = "volume_keys_activate"
        private const val KEY_SHOW_TEST_BUTTONS = "show_test_buttons"
        private const val KEY_DEFAULT_SCAN_PATTERN = "default_scan_pattern"
        private const val KEY_HOLDING_TIME_MILLIS = "holding_time_millis"
        private const val KEY_CLOUD_SYNC_ENABLED = "cloud_sync_enabled"
        private const val KEY_GEMINI_ENABLED = "gemini_enabled"
        private const val KEY_USE_LOCAL_GENERATIVE_AI = "use_local_generative_ai"
        private const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_SYNC_INTERVAL_MINUTES = "sync_interval_minutes"
        private const val KEY_SYNC_MODE = "sync_mode"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_INITIAL_TEMPLATES_CREATED = "initial_templates_created"
        private const val KEY_PAGE_SORT_ORDER = "page_sort_order"
        private const val KEY_TEMPLATE_SORT_ORDER = "template_sort_order"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        private const val KEY_SHOW_PAGE_ID_IN_LOG = "show_page_id_in_log"
        private const val KEY_EXPERIMENTAL_MANUAL_SORTING = "experimental_manual_sorting"
        private const val KEY_SMART_PREDICTION_DELAY = "smart_prediction_delay"
        private const val KEY_SMART_PREDICTION_ENABLED = "smart_prediction_enabled"
        private const val KEY_NOTIFICATION_READING_ENABLED = "notification_reading_enabled"
        private const val KEY_MONITORED_NOTIFICATION_APPS = "monitored_notification_apps"
        private const val KEY_BLUETOOTH_DELAY = "bluetooth_delay_ms"
        private const val KEY_TTS_VOLUME_MULTIPLIER = "tts_volume_multiplier"
        private const val KEY_CUES_VOLUME_MULTIPLIER = "cues_volume_multiplier"
        private const val KEY_ACTIVE_BOOK_ID = "active_book_id"
        private const val KEY_KEEP_SCREEN_ON_USER_MODE = "keep_screen_on_user_mode"
        private const val KEY_USER_MODE_SCREEN_BEHAVIOR = "user_mode_screen_behavior"
        private const val KEY_GEMINI_TIMEOUT = "gemini_timeout_ms"
        private const val KEY_GEMINI_REDO_PREDICTION = "gemini_redo_prediction"
        private const val KEY_WEATHER_CACHE_TIMEOUT = "weather_cache_timeout_minutes"
    }
}

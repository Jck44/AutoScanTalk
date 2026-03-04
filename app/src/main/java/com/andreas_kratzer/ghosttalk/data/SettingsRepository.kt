package com.andreas_kratzer.ghosttalk.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.annotation.SuppressLint

@SuppressLint("CommitPrefEdits", "ApplySharedPref", "UseKtx")
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var activeBookId: String = "book-default"
        set(value) {
            field = value
            refreshFlows()
        }

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

    private fun putStringScoped(key: String, value: String?) {
        prefs.edit().putString(getScopedKey(key), value).apply()
    }

    private fun putBooleanScoped(key: String, value: Boolean) {
        prefs.edit().putBoolean(getScopedKey(key), value).apply()
    }

    private fun putLongScoped(key: String, value: Long) {
        prefs.edit().putLong(getScopedKey(key), value).apply()
    }

    private fun refreshFlows() {
        _ttsLanguageFlow.value = ttsLanguage
        _scanDelayFlow.value = scanDelayMillis
        _defaultStartPageIdFlow.value = defaultStartPageId
        _ttsVoiceNameFlow.value = ttsVoiceName
        _persistActionLogsFlow.value = persistActionLogs
        _actionLogsStorageFlow.value = actionLogsStorage
        _switchActivationKeyFlow.value = switchActivationKey
        _volumeKeysActivateFlow.value = volumeKeysActivate
        _showTestButtonsFlow.value = showTestButtons
        _defaultScanPatternFlow.value = defaultScanPattern
        _themeModeFlow.value = themeMode
        _pageSortOrderFlow.value = pageSortOrder
        _templateSortOrderFlow.value = templateSortOrder
        _lastSuccessfulSyncTimeFlow.value = lastSuccessfulSyncTime
        _showPageIdInLogFlow.value = showPageIdInLog
        _experimentalManualSortingFlow.value = experimentalManualSorting
        _smartPredictionDelayMillisFlow.value = smartPredictionDelayMillis
    }

    private val _pageSortOrderFlow = MutableStateFlow(getStringScoped(KEY_PAGE_SORT_ORDER, "MANUAL") ?: "MANUAL")
    val pageSortOrderFlow: StateFlow<String> = _pageSortOrderFlow.asStateFlow()

    var pageSortOrder: String
        get() = getStringScoped(KEY_PAGE_SORT_ORDER, "MANUAL") ?: "MANUAL"
        set(value) {
            putStringScoped(KEY_PAGE_SORT_ORDER, value)
            _pageSortOrderFlow.value = value
        }

    private val _templateSortOrderFlow = MutableStateFlow(getStringScoped(KEY_TEMPLATE_SORT_ORDER, "MANUAL") ?: "MANUAL")
    val templateSortOrderFlow: StateFlow<String> = _templateSortOrderFlow.asStateFlow()

    var templateSortOrder: String
        get() = getStringScoped(KEY_TEMPLATE_SORT_ORDER, "MANUAL") ?: "MANUAL"
        set(value) {
            putStringScoped(KEY_TEMPLATE_SORT_ORDER, value)
            _templateSortOrderFlow.value = value
        }

    private val _lastSuccessfulSyncTimeFlow = MutableStateFlow(getLongScoped(KEY_LAST_SYNC_TIME, 0L))
    val lastSuccessfulSyncTimeFlow: StateFlow<Long> = _lastSuccessfulSyncTimeFlow.asStateFlow()

    var lastSuccessfulSyncTime: Long
        get() = getLongScoped(KEY_LAST_SYNC_TIME, 0L)
        set(value) {
            putLongScoped(KEY_LAST_SYNC_TIME, value)
            _lastSuccessfulSyncTimeFlow.value = value
        }

    private val _showPageIdInLogFlow = MutableStateFlow(getBooleanScoped(KEY_SHOW_PAGE_ID_IN_LOG, false))

    var showPageIdInLog: Boolean
        get() = getBooleanScoped(KEY_SHOW_PAGE_ID_IN_LOG, false)
        set(value) {
            putBooleanScoped(KEY_SHOW_PAGE_ID_IN_LOG, value)
            _showPageIdInLogFlow.value = value
        }
    
    private val _experimentalManualSortingFlow = MutableStateFlow(getBooleanScoped(KEY_EXPERIMENTAL_MANUAL_SORTING, false))
    val experimentalManualSortingFlow: StateFlow<Boolean> = _experimentalManualSortingFlow.asStateFlow()

    var experimentalManualSorting: Boolean
        get() = getBooleanScoped(KEY_EXPERIMENTAL_MANUAL_SORTING, false)
        set(value) {
            putBooleanScoped(KEY_EXPERIMENTAL_MANUAL_SORTING, value)
            _experimentalManualSortingFlow.value = value
        }

    private val _smartPredictionDelayMillisFlow = MutableStateFlow(getLongScoped(KEY_SMART_PREDICTION_DELAY, 2000L))
    val smartPredictionDelayMillisFlow: StateFlow<Long> = _smartPredictionDelayMillisFlow.asStateFlow()

    var smartPredictionDelayMillis: Long
        get() = getLongScoped(KEY_SMART_PREDICTION_DELAY, 2000L)
        set(value) {
            putLongScoped(KEY_SMART_PREDICTION_DELAY, value)
            _smartPredictionDelayMillisFlow.value = value
        }

    private val _ttsLanguageFlow = MutableStateFlow(getStringScoped(KEY_TTS_LANGUAGE))
    val ttsLanguageFlow: StateFlow<String?> = _ttsLanguageFlow.asStateFlow()

    private val _scanDelayFlow = MutableStateFlow(getLongScoped(KEY_SCAN_DELAY_MILLIS, 1000L))
    val scanDelayFlow: StateFlow<Long> = _scanDelayFlow.asStateFlow()

    var ttsLanguage: String?
        get() = getStringScoped(KEY_TTS_LANGUAGE)
        set(value) {
            putStringScoped(KEY_TTS_LANGUAGE, value)
            _ttsLanguageFlow.value = value
        }

    var autoStartScanning: Boolean
        get() = getBooleanScoped(KEY_AUTO_START_SCANNING, true)
        set(value) {
            putBooleanScoped(KEY_AUTO_START_SCANNING, value)
        }

    var scanDelayMillis: Long
        get() = getLongScoped(KEY_SCAN_DELAY_MILLIS, 3000L)
        set(value) {
            putLongScoped(KEY_SCAN_DELAY_MILLIS, value)
            _scanDelayFlow.value = value
        }

    var resumeScanningFromStart: Boolean
        get() = getBooleanScoped(KEY_RESUME_SCANNING_FROM_START, true)
        set(value) {
            putBooleanScoped(KEY_RESUME_SCANNING_FROM_START, value)
        }

    private val _defaultStartPageIdFlow = MutableStateFlow(getStringScoped(KEY_DEFAULT_START_PAGE_ID))
    val defaultStartPageIdFlow: StateFlow<String?> = _defaultStartPageIdFlow.asStateFlow()

    var defaultStartPageId: String?
        get() = getStringScoped(KEY_DEFAULT_START_PAGE_ID)
        set(value) {
            putStringScoped(KEY_DEFAULT_START_PAGE_ID, value)
            _defaultStartPageIdFlow.value = value
        }

    private val _defaultScanPatternFlow = MutableStateFlow(getStringScoped(KEY_DEFAULT_SCAN_PATTERN, "linear") ?: "linear")
    val defaultScanPatternFlow: StateFlow<String> = _defaultScanPatternFlow.asStateFlow()

    private val _themeModeFlow = MutableStateFlow(getStringScoped(KEY_THEME_MODE, "SYSTEM") ?: "SYSTEM")
    val themeModeFlow: StateFlow<String> = _themeModeFlow.asStateFlow()

    var themeMode: String
        get() = getStringScoped(KEY_THEME_MODE, "SYSTEM") ?: "SYSTEM"
        set(value) {
            putStringScoped(KEY_THEME_MODE, value)
            _themeModeFlow.value = value
        }

    var defaultScanPattern: String
        get() = getStringScoped(KEY_DEFAULT_SCAN_PATTERN, "linear") ?: "linear"
        set(value) {
            putStringScoped(KEY_DEFAULT_SCAN_PATTERN, value)
            _defaultScanPatternFlow.value = value
        }

    private val _ttsVoiceNameFlow = MutableStateFlow(getStringScoped(KEY_TTS_VOICE_NAME))
    val ttsVoiceNameFlow: StateFlow<String?> = _ttsVoiceNameFlow.asStateFlow()

    var ttsVoiceName: String?
        get() = getStringScoped(KEY_TTS_VOICE_NAME)
        set(value) {
            putStringScoped(KEY_TTS_VOICE_NAME, value)
            _ttsVoiceNameFlow.value = value
        }

    var ttsAudioDeviceAddress: String?
        get() = getStringScoped(KEY_TTS_AUDIO_DEVICE)
        set(value) {
            putStringScoped(KEY_TTS_AUDIO_DEVICE, value)
        }

    var cuesAudioDeviceAddress: String?
        get() = getStringScoped(KEY_CUES_AUDIO_DEVICE)
        set(value) {
            putStringScoped(KEY_CUES_AUDIO_DEVICE, value)
        }

    private val _persistActionLogsFlow = MutableStateFlow(getBooleanScoped(KEY_PERSIST_ACTION_LOGS, false))
    val persistActionLogsFlow: StateFlow<Boolean> = _persistActionLogsFlow.asStateFlow()

    var persistActionLogs: Boolean
        get() = getBooleanScoped(KEY_PERSIST_ACTION_LOGS, false)
        set(value) {
            putBooleanScoped(KEY_PERSIST_ACTION_LOGS, value)
            _persistActionLogsFlow.value = value
        }

    private val _actionLogsStorageFlow = MutableStateFlow(getStringScoped(KEY_ACTION_LOGS_STORAGE))
    val actionLogsStorageFlow: StateFlow<String?> = _actionLogsStorageFlow.asStateFlow()

    var actionLogsStorage: String?
        get() = getStringScoped(KEY_ACTION_LOGS_STORAGE)
        set(value) {
            putStringScoped(KEY_ACTION_LOGS_STORAGE, value)
            _actionLogsStorageFlow.value = value
        }

    private val _switchActivationKeyFlow = MutableStateFlow(getStringScoped(KEY_SWITCH_ACTIVATION_KEY, "Space") ?: "Space")
    val switchActivationKeyFlow: StateFlow<String> = _switchActivationKeyFlow.asStateFlow()

    var switchActivationKey: String
        get() = getStringScoped(KEY_SWITCH_ACTIVATION_KEY, "Space") ?: "Space"
        set(value) {
            putStringScoped(KEY_SWITCH_ACTIVATION_KEY, value)
            _switchActivationKeyFlow.value = value
        }

    private val _volumeKeysActivateFlow = MutableStateFlow(getBooleanScoped(KEY_VOLUME_KEYS_ACTIVATE, false))
    val volumeKeysActivateFlow: StateFlow<Boolean> = _volumeKeysActivateFlow.asStateFlow()

    var volumeKeysActivate: Boolean
        get() = getBooleanScoped(KEY_VOLUME_KEYS_ACTIVATE, false)
        set(value) {
            putBooleanScoped(KEY_VOLUME_KEYS_ACTIVATE, value)
            _volumeKeysActivateFlow.value = value
        }

    private val _showTestButtonsFlow = MutableStateFlow(getBooleanScoped(KEY_SHOW_TEST_BUTTONS, false))
    val showTestButtonsFlow: StateFlow<Boolean> = _showTestButtonsFlow.asStateFlow()

    var showTestButtons: Boolean
        get() = getBooleanScoped(KEY_SHOW_TEST_BUTTONS, false)
        set(value) {
            putBooleanScoped(KEY_SHOW_TEST_BUTTONS, value)
            _showTestButtonsFlow.value = value
        }

    var holdingTimeMillis: Long
        get() = getLongScoped(KEY_HOLDING_TIME_MILLIS, 0L)
        set(value) {
            putLongScoped(KEY_HOLDING_TIME_MILLIS, value)
        }

    var isCloudSyncEnabled: Boolean
        get() = getBooleanScoped(KEY_CLOUD_SYNC_ENABLED, false)
        set(value) {
            putBooleanScoped(KEY_CLOUD_SYNC_ENABLED, value)
        }

    var isGeminiEnabled: Boolean
        get() = getBooleanScoped(KEY_GEMINI_ENABLED, false)
        set(value) {
            putBooleanScoped(KEY_GEMINI_ENABLED, value)
        }

    var initialTemplatesCreated: Boolean
        get() = prefs.getBoolean(KEY_INITIAL_TEMPLATES_CREATED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_INITIAL_TEMPLATES_CREATED, value).apply()
        }

    var appLanguage: String?
        get() = getStringScoped(KEY_APP_LANGUAGE)
        set(value) {
            putStringScoped(KEY_APP_LANGUAGE, value)
        }

    var syncIntervalMinutes: Long
        get() = getLongScoped(KEY_SYNC_INTERVAL_MINUTES, 15L)
        set(value) {
            putLongScoped(KEY_SYNC_INTERVAL_MINUTES, value)
        }

    var syncMode: String
        get() = getStringScoped(KEY_SYNC_MODE, "TWO_WAY") ?: "TWO_WAY"
        set(value) {
            putStringScoped(KEY_SYNC_MODE, value)
        }

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
    }
}

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

    // ── ObservableSetting classes with optional scoping ───────────────────

    private inner class StringSetting(
        private val key: String,
        private val default: String? = null,
        private val isScoped: Boolean = true
    ) {
        private val _flow = MutableStateFlow(if (isScoped) getStringScoped(key, default) else prefs.getString(key, default))
        val flow: StateFlow<String?> = _flow.asStateFlow()

        var value: String?
            get() = if (isScoped) getStringScoped(key, default) else prefs.getString(key, default)
            set(v) {
                if (isScoped) putStringScoped(key, v) else prefs.edit().putString(key, v).apply()
                _flow.value = v
            }

        fun refresh() { if (isScoped) _flow.value = value }
    }

    private inner class NonNullStringSetting(
        private val key: String,
        private val default: String,
        private val isScoped: Boolean = true
    ) {
        private val _flow = MutableStateFlow((if (isScoped) getStringScoped(key, default) else prefs.getString(key, default)) ?: default)
        val flow: StateFlow<String> = _flow.asStateFlow()

        var value: String
            get() = (if (isScoped) getStringScoped(key, default) else prefs.getString(key, default)) ?: default
            set(v) {
                if (isScoped) putStringScoped(key, v) else prefs.edit().putString(key, v).apply()
                _flow.value = v
            }

        fun refresh() { if (isScoped) _flow.value = value }
    }

    private inner class BooleanSetting(
        private val key: String,
        private val default: Boolean,
        private val isScoped: Boolean = true
    ) {
        private val _flow = MutableStateFlow(if (isScoped) getBooleanScoped(key, default) else prefs.getBoolean(key, default))
        val flow: StateFlow<Boolean> = _flow.asStateFlow()

        var value: Boolean
            get() = if (isScoped) getBooleanScoped(key, default) else prefs.getBoolean(key, default)
            set(v) {
                if (isScoped) putBooleanScoped(key, v) else prefs.edit().putBoolean(key, v).apply()
                _flow.value = v
            }

        fun refresh() { if (isScoped) _flow.value = value }
    }

    private inner class LongSetting(
        private val key: String,
        private val default: Long,
        private val isScoped: Boolean = true
    ) {
        private val _flow = MutableStateFlow(if (isScoped) getLongScoped(key, default) else prefs.getLong(key, default))
        val flow: StateFlow<Long> = _flow.asStateFlow()

        var value: Long
            get() = if (isScoped) getLongScoped(key, default) else prefs.getLong(key, default)
            set(v) {
                if (isScoped) putLongScoped(key, v) else prefs.edit().putLong(key, v).apply()
                _flow.value = v
            }

        fun refresh() { if (isScoped) _flow.value = value }
    }

    private inner class FloatSetting(
        private val key: String,
        private val default: Float,
        private val isScoped: Boolean = true,
        private val coerce: ((Float) -> Float)? = null
    ) {
        private val _flow = MutableStateFlow(if (isScoped) getFloatScoped(key, default) else prefs.getFloat(key, default))
        val flow: StateFlow<Float> = _flow.asStateFlow()

        var value: Float
            get() = if (isScoped) getFloatScoped(key, default) else prefs.getFloat(key, default)
            set(v) {
                val coerced = coerce?.invoke(v) ?: v
                if (isScoped) putFloatScoped(key, coerced) else prefs.edit().putFloat(key, coerced).apply()
                _flow.value = coerced
            }

        fun refresh() { if (isScoped) _flow.value = value }
    }

    private inner class StringSetSetting(
        private val key: String,
        private val default: Set<String> = emptySet(),
        private val isScoped: Boolean = true
    ) {
        private val _flow = MutableStateFlow((if (isScoped) getStringSetScoped(key, default) else prefs.getStringSet(key, default)) ?: default)
        val flow: StateFlow<Set<String>> = _flow.asStateFlow()

        var value: Set<String>
            get() = (if (isScoped) getStringSetScoped(key, default) else prefs.getStringSet(key, default)) ?: default
            set(v) {
                if (isScoped) putStringSetScoped(key, v) else prefs.edit().putStringSet(key, v).apply()
                _flow.value = v
            }

        fun refresh() { if (isScoped) _flow.value = value }
    }

    // ── Observable settings ───────────────────────────────────────────────

    private val _ttsLanguage = StringSetting(KEY_TTS_LANGUAGE)
    private val _ttsVoiceName = StringSetting(KEY_TTS_VOICE_NAME)
    private val _autoStartScanning = BooleanSetting(KEY_AUTO_START_SCANNING, true)
    private val _scanDelay = LongSetting(KEY_SCAN_DELAY_MILLIS, 3000L)
    private val _resumeScanningFromStart = BooleanSetting(KEY_RESUME_SCANNING_FROM_START, true)
    private val _defaultStartPageId = StringSetting(KEY_DEFAULT_START_PAGE_ID)
    private val _ttsAudioDeviceAddress = StringSetting(KEY_TTS_AUDIO_DEVICE, isScoped = true)
    private val _cuesAudioDeviceAddress = StringSetting(KEY_CUES_AUDIO_DEVICE, isScoped = true)
    private val _holdingTimeMillis = LongSetting(KEY_HOLDING_TIME_MILLIS, 250L)
    private val _persistActionLogs = BooleanSetting(KEY_PERSIST_ACTION_LOGS, true, isScoped = false)
    private val _actionLogsStorage = StringSetting(KEY_ACTION_LOGS_STORAGE, isScoped = false)
    private val _switchActivationKey = NonNullStringSetting(KEY_SWITCH_ACTIVATION_KEY, "~3")
    private val _volumeKeysActivate = BooleanSetting(KEY_VOLUME_KEYS_ACTIVATE, false)
    private val _showTestButtons = BooleanSetting(KEY_SHOW_TEST_BUTTONS, false, isScoped = false)
    private val _defaultScanPattern = NonNullStringSetting(KEY_DEFAULT_SCAN_PATTERN, "linear")
    private val _themeMode = NonNullStringSetting(KEY_THEME_MODE, "LIGHT", isScoped = false)
    private val _pageSortOrder = NonNullStringSetting(KEY_PAGE_SORT_ORDER, "MANUAL")
    private val _templateSortOrder = NonNullStringSetting(KEY_TEMPLATE_SORT_ORDER, "MANUAL")
    private val _lastSuccessfulSyncTime = LongSetting(KEY_LAST_SYNC_TIME, 0L)
    private val _smartPredictionDelay = LongSetting(KEY_SMART_PREDICTION_DELAY, 2000L)
    private val _isSmartPredictionEnabled = BooleanSetting(KEY_SMART_PREDICTION_ENABLED, false)
    private val _geminiRedoPrediction = BooleanSetting(KEY_GEMINI_REDO_PREDICTION, false)
    private val _geminiTimeout = LongSetting(KEY_GEMINI_TIMEOUT, 6000L)
    private val _bluetoothDelay = LongSetting(KEY_BLUETOOTH_DELAY, 100L, isScoped = false)
    private val _isCloudSyncEnabled = BooleanSetting(KEY_CLOUD_SYNC_ENABLED, false)
    private val _syncIntervalMinutes = LongSetting(KEY_SYNC_INTERVAL_MINUTES, 15L)
    private val _syncMode = NonNullStringSetting(KEY_SYNC_MODE, "TWO_WAY")
    private val _isGeminiEnabled = BooleanSetting(KEY_GEMINI_ENABLED, false)
    private val _useLocalGenerativeAi = BooleanSetting(KEY_USE_LOCAL_GENERATIVE_AI, true)
    private val _showPageIdInLog = BooleanSetting(KEY_SHOW_PAGE_ID_IN_LOG, false, isScoped = false)
    private val _isNotificationReadingEnabled = BooleanSetting(KEY_NOTIFICATION_READING_ENABLED, false, isScoped = false)
    private val _monitoredNotificationApps = StringSetSetting(KEY_MONITORED_NOTIFICATION_APPS, isScoped = false)
    private val _appLanguage = StringSetting(KEY_APP_LANGUAGE, isScoped = false)
    private val _keepScreenOnUserMode = BooleanSetting(KEY_KEEP_SCREEN_ON_USER_MODE, true)
    private val _userModeScreenBehavior = NonNullStringSetting(KEY_USER_MODE_SCREEN_BEHAVIOR, "NORMAL")
    private val _weatherCacheTimeout = LongSetting(KEY_WEATHER_CACHE_TIMEOUT, 60L, isScoped = false)
    private val _securityPin = StringSetting(KEY_SECURITY_PIN, "", isScoped = false)
    private val _securityPinTimeoutMinutes = LongSetting(KEY_SECURITY_PIN_TIMEOUT_MINUTES, 30L, isScoped = false)
    private val _isPinRequiredForDeletion = BooleanSetting(KEY_IS_PIN_REQUIRED_FOR_DELETION, false, isScoped = false)

    private val _isBiometricEnabled = BooleanSetting(KEY_BIOMETRIC_ENABLED, false, isScoped = false)
    private val _isSecurityRequiredForEdit = BooleanSetting(KEY_SECURITY_REQUIRED_FOR_EDIT, false, isScoped = false)
    private val _isSecurityRequiredForSettings = BooleanSetting(KEY_SECURITY_REQUIRED_FOR_SETTINGS, false, isScoped = false)
    private val _startupBehavior = NonNullStringSetting(KEY_STARTUP_BEHAVIOR, "BOOK_SELECTION", isScoped = false)
    private val _favoriteBookId = StringSetting(KEY_FAVORITE_BOOK_ID, isScoped = false)

    init {
        cleanupLegacyBookPins()
    }

    private fun cleanupLegacyBookPins() {
        val allPrefs = prefs.all
        val editor = prefs.edit()
        var changed = false
        allPrefs.keys.forEach { key ->
            if (key.endsWith("_$KEY_SECURITY_PIN") && key != KEY_SECURITY_PIN) {
                editor.remove(key)
                changed = true
            }
        }
        if (changed) editor.apply()
    }

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
        _smartPredictionDelay.refresh()
        _isSmartPredictionEnabled.refresh()
        _geminiRedoPrediction.refresh()
        _geminiTimeout.refresh()
        _bluetoothDelay.refresh()
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
        _weatherCacheTimeout.refresh()
        _securityPin.refresh()
        _securityPinTimeoutMinutes.refresh()
        _isPinRequiredForDeletion.refresh()
        _isBiometricEnabled.refresh()
        _isSecurityRequiredForEdit.refresh()
        _isSecurityRequiredForSettings.refresh()
        _startupBehavior.refresh()
        _favoriteBookId.refresh()
    }

    private val _userModeCodeBehavior = NonNullStringSetting(KEY_USER_MODE_SCREEN_BEHAVIOR, "NORMAL")

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
    val isSmartPredictionEnabledFlow: StateFlow<Boolean> get() = _isSmartPredictionEnabled.flow
    val bluetoothDelayFlow: StateFlow<Long> get() = _bluetoothDelay.flow
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
    val userModeScreenBehaviorFlow: StateFlow<String> get() = _userModeCodeBehavior.flow
    val geminiTimeoutFlow: StateFlow<Long> get() = _geminiTimeout.flow
    val geminiRedoPredictionFlow: StateFlow<Boolean> get() = _geminiRedoPrediction.flow
    val weatherCacheTimeoutFlow: StateFlow<Long> get() = _weatherCacheTimeout.flow
    val securityPinFlow: StateFlow<String?> get() = _securityPin.flow
    val securityPinTimeoutMinutesFlow: StateFlow<Long> get() = _securityPinTimeoutMinutes.flow
    val isPinRequiredForDeletionFlow: StateFlow<Boolean> get() = _isPinRequiredForDeletion.flow
    val isBiometricEnabledFlow: StateFlow<Boolean> get() = _isBiometricEnabled.flow
    val isSecurityRequiredForEditFlow: StateFlow<Boolean> get() = _isSecurityRequiredForEdit.flow
    val isSecurityRequiredForSettingsFlow: StateFlow<Boolean> get() = _isSecurityRequiredForSettings.flow
    val startupBehaviorFlow: StateFlow<String> get() = _startupBehavior.flow
    val favoriteBookIdFlow: StateFlow<String?> get() = _favoriteBookId.flow

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


    var smartPredictionDelayMillis: Long
        get() = _smartPredictionDelay.value
        set(value) { _smartPredictionDelay.value = value }

    var isSmartPredictionEnabled: Boolean
        get() = _isSmartPredictionEnabled.value
        set(value) { _isSmartPredictionEnabled.value = value }

    var bluetoothDelay: Long
        get() = _bluetoothDelay.value
        set(value) { _bluetoothDelay.value = value }


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
        get() = _userModeCodeBehavior.value
        set(value) { _userModeCodeBehavior.value = value }

    var geminiTimeout: Long
        get() = _geminiTimeout.value
        set(value) { _geminiTimeout.value = value }

    var geminiRedoPrediction: Boolean
        get() = _geminiRedoPrediction.value
        set(value) { _geminiRedoPrediction.value = value }

    var weatherCacheTimeout: Long
        get() = _weatherCacheTimeout.value
        set(value) { _weatherCacheTimeout.value = value }

    var securityPin: String?
        get() = _securityPin.value
        set(value) { _securityPin.value = value }

    var securityPinTimeoutMinutes: Long
        get() = _securityPinTimeoutMinutes.value
        set(value) { _securityPinTimeoutMinutes.value = value }

    var isPinRequiredForDeletion: Boolean
        get() = _isPinRequiredForDeletion.value
        set(value) { _isPinRequiredForDeletion.value = value }

    var isBiometricEnabled: Boolean
        get() = _isBiometricEnabled.value
        set(value) { _isBiometricEnabled.value = value }

    var isSecurityRequiredForEdit: Boolean
        get() = _isSecurityRequiredForEdit.value
        set(value) { _isSecurityRequiredForEdit.value = value }

    var isSecurityRequiredForSettings: Boolean
        get() = _isSecurityRequiredForSettings.value
        set(value) { _isSecurityRequiredForSettings.value = value }

    var startupBehavior: String
        get() = _startupBehavior.value
        set(value) { _startupBehavior.value = value }

    var favoriteBookId: String?
        get() = _favoriteBookId.value
        set(value) { _favoriteBookId.value = value }

    // ── Book-specific helpers ─────────────────────────────────────────────

    fun getSecurityPinForBook(bookId: String): String? {
        val scopedKey = "${bookId}_$KEY_SECURITY_PIN"
        if (prefs.contains(scopedKey)) {
            return prefs.getString(scopedKey, "")
        }
        return prefs.getString(KEY_SECURITY_PIN, "")
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
        private const val KEY_SMART_PREDICTION_DELAY = "smart_prediction_delay"
        private const val KEY_SMART_PREDICTION_ENABLED = "smart_prediction_enabled"
        private const val KEY_NOTIFICATION_READING_ENABLED = "notification_reading_enabled"
        private const val KEY_MONITORED_NOTIFICATION_APPS = "monitored_notification_apps"
        private const val KEY_BLUETOOTH_DELAY = "bluetooth_delay_ms"
        private const val KEY_ACTIVE_BOOK_ID = "active_book_id"
        private const val KEY_KEEP_SCREEN_ON_USER_MODE = "keep_screen_on_user_mode"
        private const val KEY_USER_MODE_SCREEN_BEHAVIOR = "user_mode_screen_behavior"
        private const val KEY_GEMINI_TIMEOUT = "gemini_timeout_ms"
        private const val KEY_GEMINI_REDO_PREDICTION = "gemini_redo_prediction"
        private const val KEY_WEATHER_CACHE_TIMEOUT = "weather_cache_timeout_minutes"
        private const val KEY_SECURITY_PIN = "security_pin"
        private const val KEY_SECURITY_PIN_TIMEOUT_MINUTES = "security_pin_timeout_minutes"
        private const val KEY_IS_PIN_REQUIRED_FOR_DELETION = "is_pin_required_for_deletion"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_SECURITY_REQUIRED_FOR_EDIT = "security_required_for_edit"
        private const val KEY_SECURITY_REQUIRED_FOR_SETTINGS = "security_required_for_settings"
        private const val KEY_STARTUP_BEHAVIOR = "startup_behavior"
        private const val KEY_FAVORITE_BOOK_ID = "favorite_book_id"
    }
}

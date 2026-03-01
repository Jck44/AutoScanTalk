package com.example.gostalk.data

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
        get() = getLongScoped(KEY_SCAN_DELAY_MILLIS, 1000L)
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

    companion object {
        private const val PREFS_NAME = "gostalk_settings"
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
    }
}

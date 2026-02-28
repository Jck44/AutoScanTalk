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

    private val _ttsLanguageFlow = MutableStateFlow(prefs.getString(KEY_TTS_LANGUAGE, null))
    val ttsLanguageFlow: StateFlow<String?> = _ttsLanguageFlow.asStateFlow()

    private val _scanDelayFlow = MutableStateFlow(prefs.getLong(KEY_SCAN_DELAY_MILLIS, 1000L))
    val scanDelayFlow: StateFlow<Long> = _scanDelayFlow.asStateFlow()

    var ttsLanguage: String?
        get() = prefs.getString(KEY_TTS_LANGUAGE, null) // null bedeutet "System default"
        set(value) {
            prefs.edit().putString(KEY_TTS_LANGUAGE, value).apply()
            _ttsLanguageFlow.value = value
        }

    var autoStartScanning: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START_SCANNING, true) // Standardmäßig eingeschaltet
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_START_SCANNING, value).apply()
        }

    var scanDelayMillis: Long
        get() = prefs.getLong(KEY_SCAN_DELAY_MILLIS, 1000L) // Standardmäßig 1000ms
        set(value) {
            prefs.edit().putLong(KEY_SCAN_DELAY_MILLIS, value).apply()
            _scanDelayFlow.value = value
        }

    var resumeScanningFromStart: Boolean
        get() = prefs.getBoolean(KEY_RESUME_SCANNING_FROM_START, true) // Standardmäßig von vorn (true)
        set(value) {
            prefs.edit().putBoolean(KEY_RESUME_SCANNING_FROM_START, value).apply()
        }

    private val _defaultStartPageIdFlow = MutableStateFlow(prefs.getString(KEY_DEFAULT_START_PAGE_ID, null))
    val defaultStartPageIdFlow: StateFlow<String?> = _defaultStartPageIdFlow.asStateFlow()

    var defaultStartPageId: String?
        get() = prefs.getString(KEY_DEFAULT_START_PAGE_ID, null)
        set(value) {
            prefs.edit().putString(KEY_DEFAULT_START_PAGE_ID, value).apply()
            _defaultStartPageIdFlow.value = value
        }

    private val _ttsVoiceNameFlow = MutableStateFlow(prefs.getString(KEY_TTS_VOICE_NAME, null))
    val ttsVoiceNameFlow: StateFlow<String?> = _ttsVoiceNameFlow.asStateFlow()

    var ttsVoiceName: String?
        get() = prefs.getString(KEY_TTS_VOICE_NAME, null)
        set(value) {
            prefs.edit().putString(KEY_TTS_VOICE_NAME, value).apply()
            _ttsVoiceNameFlow.value = value
        }

    var ttsAudioDeviceAddress: String?
        get() = prefs.getString(KEY_TTS_AUDIO_DEVICE, null)
        set(value) {
            prefs.edit().putString(KEY_TTS_AUDIO_DEVICE, value).apply()
        }

    var cuesAudioDeviceAddress: String?
        get() = prefs.getString(KEY_CUES_AUDIO_DEVICE, null)
        set(value) {
            prefs.edit().putString(KEY_CUES_AUDIO_DEVICE, value).apply()
        }

    private val _persistActionLogsFlow = MutableStateFlow(prefs.getBoolean(KEY_PERSIST_ACTION_LOGS, false))
    val persistActionLogsFlow: StateFlow<Boolean> = _persistActionLogsFlow.asStateFlow()

    var persistActionLogs: Boolean
        get() = prefs.getBoolean(KEY_PERSIST_ACTION_LOGS, false)
        set(value) {
            prefs.edit().putBoolean(KEY_PERSIST_ACTION_LOGS, value).apply()
            _persistActionLogsFlow.value = value
        }

    private val _actionLogsStorageFlow = MutableStateFlow(prefs.getString(KEY_ACTION_LOGS_STORAGE, null))
    val actionLogsStorageFlow: StateFlow<String?> = _actionLogsStorageFlow.asStateFlow()

    var actionLogsStorage: String?
        get() = prefs.getString(KEY_ACTION_LOGS_STORAGE, null)
        set(value) {
            prefs.edit().putString(KEY_ACTION_LOGS_STORAGE, value).apply()
            _actionLogsStorageFlow.value = value
        }

    private val _switchActivationKeyFlow = MutableStateFlow(prefs.getString(KEY_SWITCH_ACTIVATION_KEY, "Space") ?: "Space")
    val switchActivationKeyFlow: StateFlow<String> = _switchActivationKeyFlow.asStateFlow()

    var switchActivationKey: String
        get() = prefs.getString(KEY_SWITCH_ACTIVATION_KEY, "Space") ?: "Space"
        set(value) {
            prefs.edit().putString(KEY_SWITCH_ACTIVATION_KEY, value).apply()
            _switchActivationKeyFlow.value = value
        }

    private val _volumeKeysActivateFlow = MutableStateFlow(prefs.getBoolean(KEY_VOLUME_KEYS_ACTIVATE, false))
    val volumeKeysActivateFlow: StateFlow<Boolean> = _volumeKeysActivateFlow.asStateFlow()

    var volumeKeysActivate: Boolean
        get() = prefs.getBoolean(KEY_VOLUME_KEYS_ACTIVATE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_VOLUME_KEYS_ACTIVATE, value).apply()
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

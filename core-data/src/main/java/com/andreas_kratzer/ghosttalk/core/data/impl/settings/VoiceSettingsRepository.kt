package com.andreas_kratzer.ghosttalk.core.data.impl.settings

import android.content.SharedPreferences
import android.media.MediaRecorder
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_CUES_AUDIO_DEVICE
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_RECORDING_AUDIO_SOURCE
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_TTS_AUDIO_DEVICE
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_TTS_LANGUAGE
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_TTS_VOICE_NAME
import kotlinx.coroutines.flow.StateFlow

class VoiceSettingsRepository(
    prefs: SharedPreferences,
    activeBookIdFlow: StateFlow<String?>
) : BaseSettingsRepository(prefs, activeBookIdFlow) {

    private val _ttsLanguage = StringSetting(KEY_TTS_LANGUAGE)
    private val _ttsVoiceName = StringSetting(KEY_TTS_VOICE_NAME)
    private val _ttsAudioDeviceAddress = StringSetting(KEY_TTS_AUDIO_DEVICE, isScoped = true)
    private val _cuesAudioDeviceAddress = StringSetting(KEY_CUES_AUDIO_DEVICE, isScoped = true)
    private val _ttsEngine = StringSetting(SettingsConstants.KEY_TTS_ENGINE)
    private val _googleTtsLanguage = StringSetting(SettingsConstants.KEY_GOOGLE_TTS_LANGUAGE)
    private val _googleTtsVoiceName = StringSetting(SettingsConstants.KEY_GOOGLE_TTS_VOICE_NAME)
    private val _elevenLabsTtsLanguage = StringSetting(SettingsConstants.KEY_ELEVENLABS_TTS_LANGUAGE)
    private val _elevenLabsTtsVoiceName = StringSetting(SettingsConstants.KEY_ELEVENLABS_TTS_VOICE_NAME)
    private val _recordingAudioSource = IntSetting(KEY_RECORDING_AUDIO_SOURCE, MediaRecorder.AudioSource.VOICE_COMMUNICATION, isScoped = false)
    private val _ttsPlaybackSpeed = FloatSetting(SettingsConstants.KEY_TTS_PLAYBACK_SPEED, 1.0f, isScoped = false)

    val ttsLanguageFlow = _ttsLanguage.flow
    val ttsVoiceNameFlow = _ttsVoiceName.flow
    val ttsAudioDeviceAddressFlow = _ttsAudioDeviceAddress.flow
    val cuesAudioDeviceAddressFlow = _cuesAudioDeviceAddress.flow
    val ttsEngineFlow = _ttsEngine.flow
    val googleTtsLanguageFlow = _googleTtsLanguage.flow
    val googleTtsVoiceNameFlow = _googleTtsVoiceName.flow
    val elevenLabsTtsLanguageFlow = _elevenLabsTtsLanguage.flow
    val elevenLabsTtsVoiceNameFlow = _elevenLabsTtsVoiceName.flow
    val recordingAudioSourceFlow = _recordingAudioSource.flow
    val ttsPlaybackSpeedFlow = _ttsPlaybackSpeed.flow

    var ttsLanguage: String? by _ttsLanguage
    var ttsVoiceName: String? by _ttsVoiceName
    var ttsAudioDeviceAddress: String? by _ttsAudioDeviceAddress
    var cuesAudioDeviceAddress: String? by _cuesAudioDeviceAddress
    var ttsEngine: String? by _ttsEngine
    var googleTtsLanguage: String? by _googleTtsLanguage
    var googleTtsVoiceName: String? by _googleTtsVoiceName
    var elevenLabsTtsLanguage: String? by _elevenLabsTtsLanguage
    var elevenLabsTtsVoiceName: String? by _elevenLabsTtsVoiceName
    var recordingAudioSource: Int by _recordingAudioSource
    var ttsPlaybackSpeed: Float by _ttsPlaybackSpeed


    override fun refresh() {
        _ttsLanguage.refresh()
        _ttsVoiceName.refresh()
        _ttsAudioDeviceAddress.refresh()
        _cuesAudioDeviceAddress.refresh()
        _ttsEngine.refresh()
        _googleTtsLanguage.refresh()
        _googleTtsVoiceName.refresh()
        _elevenLabsTtsLanguage.refresh()
        _elevenLabsTtsVoiceName.refresh()
        _recordingAudioSource.refresh()
        _ttsPlaybackSpeed.refresh()
    }
}

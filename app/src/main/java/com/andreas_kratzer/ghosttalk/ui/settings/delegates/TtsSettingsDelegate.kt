package com.andreas_kratzer.ghosttalk.ui.settings.delegates

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.andreas_kratzer.ghosttalk.core.audio.AudioDeviceManager
import com.andreas_kratzer.ghosttalk.core.model.AudioOutputDevice
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.domain.tts.GetAudioDevicesUseCase
import com.andreas_kratzer.ghosttalk.domain.tts.SetTtsLanguageUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

class TtsSettingsDelegate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val audioDeviceManager: AudioDeviceManager,
    private val getAudioDevicesUseCase: GetAudioDevicesUseCase,
    private val setTtsLanguageUseCase: SetTtsLanguageUseCase
) {
    private val _availableLanguages = MutableStateFlow<List<Locale>>(emptyList())
    val availableLanguages: StateFlow<List<Locale>> = _availableLanguages.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<Voice>>(emptyList())
    val availableVoices: StateFlow<List<Voice>> = _availableVoices.asStateFlow()

    private val _availableAudioDevices = MutableStateFlow<List<AudioOutputDevice>>(emptyList())
    val availableAudioDevices: StateFlow<List<AudioOutputDevice>> = _availableAudioDevices.asStateFlow()

    private var tts: TextToSpeech? = null
    private var scope: CoroutineScope? = null

    fun initialize(scope: CoroutineScope, onVoiceMissing: (String, String?) -> Unit) {
        this.scope = scope
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                _availableLanguages.value = tts?.availableLanguages?.toList() ?: emptyList()
                loadAvailableVoices()
            }
        }
        loadAvailableAudioDevices()
    }

    fun loadAvailableLanguages() {
        _availableLanguages.value = tts?.availableLanguages?.toList() ?: emptyList()
    }

    fun loadAvailableVoices() {
        _availableVoices.value = tts?.voices?.toList() ?: emptyList()
    }

    fun loadAvailableAudioDevices() {
        val launchScope = scope ?: CoroutineScope(Dispatchers.Main)
        launchScope.launch {
            _availableAudioDevices.value = getAudioDevicesUseCase.execute()
        }
    }

    fun setTtsLanguage(tag: String) {
        setTtsLanguageUseCase(tag)
    }

    fun setTtsVoice(name: String?) {
        settingsRepository.ttsVoiceName = name
    }

    fun setTtsAudioDevice(addr: String?) {
        settingsRepository.ttsAudioDeviceAddress = addr
    }

    fun setCuesAudioDevice(addr: String?) {
        settingsRepository.cuesAudioDeviceAddress = addr
    }

    fun getResolvedDeviceName(addr: String?): String {
        if (addr == null) return "Standard"
        val device = audioDeviceManager.getAudioDeviceInfo(addr)
        return device?.productName?.toString() ?: "Unbekannt"
    }
}

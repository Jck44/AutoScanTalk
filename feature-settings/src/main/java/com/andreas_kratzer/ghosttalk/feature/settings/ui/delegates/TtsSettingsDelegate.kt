package com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.andreas_kratzer.ghosttalk.core.audio.AudioDeviceManager
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.model.AudioOutputDevice
import com.andreas_kratzer.ghosttalk.core.tts.GetAudioDevicesUseCase
import com.andreas_kratzer.ghosttalk.core.tts.SetTtsLanguageUseCase
import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
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
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val audioDeviceManager: AudioDeviceManager,
    private val getAudioDevicesUseCase: GetAudioDevicesUseCase,
    private val setTtsLanguageUseCase: SetTtsLanguageUseCase,
    private val ttsHelper: TextToSpeechHelper
) {
    private val _availableLanguages = MutableStateFlow<List<Locale>>(emptyList())
    val availableLanguages: StateFlow<List<Locale>> = _availableLanguages.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<Voice>>(emptyList())
    val availableVoices: StateFlow<List<Voice>> = _availableVoices.asStateFlow()

    private val _availableAudioDevices = MutableStateFlow<List<AudioOutputDevice>>(emptyList())
    val availableAudioDevices: StateFlow<List<AudioOutputDevice>> = _availableAudioDevices.asStateFlow()

    private var scope: CoroutineScope? = null

    fun initialize(scope: CoroutineScope, onVoiceMissing: (String, String?) -> Unit) {
        this.scope = scope
        
        // Use the existing ttsHelper instead of creating a new TextToSpeech instance
        viewModelScopeLaunch {
            loadAvailableLanguages()
            loadAvailableVoices()
        }
        loadAvailableAudioDevices()
    }

    private fun viewModelScopeLaunch(block: suspend CoroutineScope.() -> Unit) {
        (scope ?: CoroutineScope(Dispatchers.Main)).launch {
            block()
        }
    }

    fun loadAvailableLanguages() {
        _availableLanguages.value = ttsHelper.getAvailableLanguages()
            .distinctBy { it.language }
            .sortedBy { it.displayName }
    }

    fun loadAvailableVoices() {
        _availableVoices.value = ttsHelper.getAvailableVoices(settingsRepository.ttsLanguage).toList()
    }

    fun loadAvailableAudioDevices() {
        viewModelScopeLaunch {
            audioDeviceManager.availableDevicesFlow.collect { devices ->
                _availableAudioDevices.value = devices
            }
        }
    }

    fun setTtsLanguage(tag: String) {
        setTtsLanguageUseCase(tag)
        // Ensure voice is applied immediately before feedback to avoid race condition
        ttsHelper.setLanguageAndVoice(tag, settingsRepository.ttsVoiceName)
        loadAvailableVoices()
        speakFeedback("Sprache ausgewählt")
    }

    fun setTtsVoice(name: String?) {
        settingsRepository.ttsVoiceName = name
        // Ensure voice is applied immediately before feedback to avoid race condition
        ttsHelper.setLanguageAndVoice(settingsRepository.ttsLanguage, name)
        speakFeedback("Stimme ausgewählt")
    }

    fun setTtsAudioDevice(addr: String?) {
        settingsRepository.ttsAudioDeviceAddress = addr
        speakFeedback("Ausgabegerät für Sprechen ausgewählt", addr)
    }

    fun setCuesAudioDevice(addr: String?) {
        settingsRepository.cuesAudioDeviceAddress = addr
        speakFeedback("Ausgabegerät für Feedback ausgewählt", addr)
    }

    private fun speakFeedback(text: String, deviceAddress: String? = null) {
        val targetAddr = deviceAddress ?: settingsRepository.ttsAudioDeviceAddress
        ttsHelper.speakRouted(text, targetAddr)
    }

    fun getResolvedDeviceName(addr: String?): String {
        if (addr == null) return "System-Standard"
        val device = audioDeviceManager.getAudioDeviceInfo(addr)
        return if (device != null) {
            audioDeviceManager.getReadableDeviceName(device)
        } else {
            addr.split("|").getOrNull(1) ?: addr.split("|").firstOrNull() ?: "Unbekannt"
        }
    }
}

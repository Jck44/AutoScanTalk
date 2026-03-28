package com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates

import android.content.Context
import com.andreas_kratzer.ghosttalk.core.audio.AudioDeviceManager
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.model.AudioOutputDevice
import com.andreas_kratzer.ghosttalk.core.tts.GetAudioDevicesUseCase
import com.andreas_kratzer.ghosttalk.core.tts.SetTtsLanguageUseCase
import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
import com.andreas_kratzer.ghosttalk.core.tts.TtsVoice
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
    private val ttsHelper: TextToSpeechHelper,
    private val authManager: com.andreas_kratzer.ghosttalk.core.cloud.AuthManager
) {
    private val _availableLanguages = MutableStateFlow<List<Locale>>(emptyList())
    val availableLanguages: StateFlow<List<Locale>> = _availableLanguages.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<TtsVoice>>(emptyList())
    val availableVoices: StateFlow<List<TtsVoice>> = _availableVoices.asStateFlow()

    private val _availableAudioDevices = MutableStateFlow<List<AudioOutputDevice>>(emptyList())
    val availableAudioDevices: StateFlow<List<AudioOutputDevice>> = _availableAudioDevices.asStateFlow()

    private val _cachedAudioDevices = MutableStateFlow<Map<String, String>>(emptyMap())
    val cachedAudioDevices: StateFlow<Map<String, String>> = _cachedAudioDevices.asStateFlow()

    private var scope: CoroutineScope? = null

    fun initialize(scope: CoroutineScope, onVoiceMissing: (String, String?) -> Unit) {
        this.scope = scope
        
        // Reactively update available voices when the provider or its voices change
        viewModelScopeLaunch {
            ttsHelper.availableVoicesFlow.collect { voices ->
                _availableVoices.value = voices
            }
        }
        
        // Use the existing ttsHelper instead of creating a new TextToSpeech instance
        viewModelScopeLaunch {
            loadAvailableLanguages()
        }
        loadAvailableAudioDevices()
        loadCachedAudioDevices()
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

    fun loadAvailableAudioDevices() {
        viewModelScopeLaunch {
            audioDeviceManager.availableDevicesFlow.collect { devices ->
                _availableAudioDevices.value = devices
                
                // Also update cache when devices list changes
                devices.forEach { device ->
                    val parts = device.address.split("|", limit = 2)
                    if (parts.size > 1) {
                        settingsRepository.saveDeviceName(parts[1], device.name)
                    }
                }
                loadCachedAudioDevices()
            }
        }
    }

    private fun loadCachedAudioDevices() {
        _cachedAudioDevices.value = settingsRepository.getCachedDevices()
    }

    fun setTtsLanguage(tag: String) {
        setTtsLanguageUseCase(tag)
        // Save to engine-specific setting
        if (settingsRepository.ttsEngine == "elevenlabs") {
            settingsRepository.elevenLabsTtsLanguage = tag
        } else {
            settingsRepository.googleTtsLanguage = tag
        }
        // Ensure voice is applied immediately before feedback to avoid race condition
        ttsHelper.setLanguageAndVoice(tag, settingsRepository.ttsVoiceName)
        speakFeedback("Sprache ausgewählt")
    }

    fun setTtsVoice(name: String?) {
        settingsRepository.ttsVoiceName = name
        // Save to engine-specific setting
        if (settingsRepository.ttsEngine == "elevenlabs") {
            settingsRepository.elevenLabsTtsVoiceName = name
        } else {
            settingsRepository.googleTtsVoiceName = name
        }
        // Ensure voice is applied immediately before feedback to avoid race condition
        ttsHelper.setLanguageAndVoice(settingsRepository.ttsLanguage, name)
        speakFeedback("Stimme ausgewählt")
    }

    fun setTtsAudioDevice(addr: String?) {
        settingsRepository.ttsAudioDeviceAddress = addr
        saveDeviceNameToCacheIfPresent(addr)
        speakFeedback("Ausgabegerät für Sprechen ausgewählt", addr)
    }

    fun setCuesAudioDevice(addr: String?) {
        settingsRepository.cuesAudioDeviceAddress = addr
        saveDeviceNameToCacheIfPresent(addr)
        speakFeedback("Ausgabegerät für Feedback ausgewählt", addr)
    }

    private fun saveDeviceNameToCacheIfPresent(addr: String?) {
        if (addr == null) return
        val device = audioDeviceManager.getAudioDeviceInfo(addr)
        if (device != null) {
            val parts = addr.split("|", limit = 2)
            if (parts.size > 1) {
                val readableName = audioDeviceManager.getReadableDeviceName(device)
                settingsRepository.saveDeviceName(parts[1], readableName)
                loadCachedAudioDevices()
            }
        }
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
            val parts = addr.split("|")
            val persistentId = parts.getOrNull(1) ?: parts.firstOrNull() ?: return "Unbekannt"
            settingsRepository.getDeviceName(persistentId) ?: persistentId
        }
    }

    fun setElevenLabsApiKey(key: String) {
        settingsRepository.elevenLabsApiKey = key
    }

    fun setTtsEngine(engine: String?) {
        settingsRepository.ttsEngine = engine
        // Ensure switch is immediate for upcoming feedback
        ttsHelper.switchProvider(engine)
        
        // Restore engine-specific settings
        val (savedLang, savedVoice) = if (engine == "elevenlabs") {
            settingsRepository.elevenLabsTtsLanguage to settingsRepository.elevenLabsTtsVoiceName
        } else {
            settingsRepository.googleTtsLanguage to settingsRepository.googleTtsVoiceName
        }
        
        settingsRepository.ttsLanguage = savedLang
        settingsRepository.ttsVoiceName = savedVoice
        
        // Apply immediately
        ttsHelper.setLanguageAndVoice(savedLang, savedVoice)

        val engineName = when (engine) {
            "elevenlabs" -> "ElevenLabs"
            else -> "Google"
        }
        speakFeedback("$engineName Sprach-Engine ausgewählt")
    }

    fun setElevenLabsModel(model: String) {
        settingsRepository.elevenLabsModel = model
        val modelName = when (model) {
            "eleven_multilingual_v2" -> "Multilingual v2"
            "eleven_v3" -> "Multilingual v3"
            "eleven_turbo_v2_5" -> "Turbo v2.5"
            "eleven_flash_v2_5" -> "Flash v2.5"
            else -> "Modell"
        }
        speakFeedback("ElevenLabs $modelName ausgewählt")
    }

    fun setElevenLabsStability(value: Float) {
        settingsRepository.elevenLabsStability = value
        // No audio feedback for sliders to avoid noise during dragging
    }

    fun setElevenLabsSimilarityBoost(value: Float) {
        settingsRepository.elevenLabsSimilarityBoost = value
        // No audio feedback for sliders to avoid noise during dragging
    }

    suspend fun saveApiKeyToGoogle(activity: android.app.Activity): PasswordManagerResult {
        val key = settingsRepository.elevenLabsApiKey
        if (key.isNullOrEmpty()) return PasswordManagerResult.Error("API Key is empty")
        
        return authManager.saveApiKeyToPasswordManager(activity, key).fold(
            onSuccess = { PasswordManagerResult.Success },
            onFailure = { e -> e.toPasswordManagerResult() }
        )
    }

    suspend fun importApiKeyFromGoogle(activity: android.app.Activity): PasswordManagerResult {
        return authManager.getApiKeyFromPasswordManager(activity).fold(
            onSuccess = { key ->
                if (!key.isNullOrEmpty()) {
                    settingsRepository.elevenLabsApiKey = key
                    PasswordManagerResult.Success
                } else {
                    PasswordManagerResult.NoKeyFound
                }
            },
            onFailure = { e -> e.toPasswordManagerResult() }
        )
    }

    private fun Throwable.toPasswordManagerResult(): PasswordManagerResult {
        return when (this) {
            is androidx.credentials.exceptions.GetCredentialCancellationException,
            is androidx.credentials.exceptions.CreateCredentialCancellationException -> {
                PasswordManagerResult.Cancelled
            }
            is androidx.credentials.exceptions.NoCredentialException -> {
                PasswordManagerResult.NoKeyFound
            }
            is androidx.credentials.exceptions.GetCredentialProviderConfigurationException,
            is androidx.credentials.exceptions.CreateCredentialProviderConfigurationException -> {
                PasswordManagerResult.NoManager
            }
            else -> {
                PasswordManagerResult.Error(this.message ?: "Unknown error")
            }
        }
    }
}

sealed class PasswordManagerResult {
    object Success : PasswordManagerResult()
    object NoKeyFound : PasswordManagerResult()
    object NoManager : PasswordManagerResult()
    object Cancelled : PasswordManagerResult()
    data class Error(val message: String) : PasswordManagerResult()
}

package com.andreas_kratzer.ghosttalk.core.tts

import com.andreas_kratzer.ghosttalk.core.tts.TtsVoice

import android.content.Context
import android.util.Log
import com.andreas_kratzer.ghosttalk.core.audio.RoutedAudioPlayer
import com.andreas_kratzer.ghosttalk.core.di.ApplicationScope
import com.andreas_kratzer.ghosttalk.core.settings.TtsSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
open class TextToSpeechHelper @Inject constructor(
    @param:ApplicationContext val context: Context,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val settingsRepository: TtsSettings,
    private val androidTtsProvider: Provider<AndroidTtsProvider>,
    private val elevenLabsTtsProvider: Provider<ElevenLabsTtsProvider>
) {

    private val currentProviderFlow = MutableStateFlow<TtsProvider>(androidTtsProvider.get())
    private var currentProvider: TtsProvider 
        get() = currentProviderFlow.value
        set(value) { currentProviderFlow.value = value }
    
    @OptIn(ExperimentalCoroutinesApi::class)
    val availableVoicesFlow: StateFlow<List<TtsVoice>> = currentProviderFlow
        .flatMapLatest { it.availableVoicesFlow }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    open val isReady: Boolean get() = currentProvider.isReady

    // Support for interrupting ONLY notifications
    var isReadingNotification: Boolean = false

    init {
        // Observe engine changes
        scope.launch {
            settingsRepository.ttsEngineFlow.collect { engine ->
                Log.d("TextToSpeechHelper", "TTS Engine changed to: $engine")
                switchProvider(engine)
            }
        }

        // Observe language/voice changes
        scope.launch {
            combine(
                settingsRepository.ttsLanguageFlow,
                settingsRepository.ttsVoiceNameFlow
            ) { lang, voice -> lang to voice }
                .collect { (newLanguage, newVoice) ->
                    Log.d("TextToSpeechHelper", "Settings updated: lang=$newLanguage, voice=$newVoice")
                    currentProvider.setLanguageAndVoice(newLanguage, newVoice)
                }
        }
    }

    private fun switchProvider(engineId: String?) {
        val nextProvider = when (engineId) {
            "elevenlabs" -> elevenLabsTtsProvider.get()
            else -> androidTtsProvider.get()
        }
        
        if (nextProvider != currentProvider) {
            currentProvider.stopAll()
            currentProvider = nextProvider
            // Apply current settings to new provider
            currentProvider.setLanguageAndVoice(
                settingsRepository.ttsLanguage,
                settingsRepository.ttsVoiceName
            )
        }
    }

    open fun speak(text: String, queueMode: Int = 0, onDone: (() -> Unit)? = null, onError: ((String) -> Unit)? = null) {
        currentProvider.speak(text, queueMode, onDone, onError)
    }

    open fun speakRouted(
        text: String, 
        deviceAddress: String?, 
        queueMode: Int = 0,
        isForCues: Boolean = false,
        onDone: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        currentProvider.speakRouted(text, deviceAddress, queueMode, isForCues, onDone, onError)
    }

    fun getAvailableLanguages(): List<Locale> {
        return currentProvider.getAvailableLanguages()
    }

    fun getAvailableVoices(languageTag: String?): List<TtsVoice> {
        return currentProvider.getAvailableVoices(languageTag)
    }

    fun setVoice(voiceName: String?) {
        currentProvider.setVoice(voiceName)
    }

    fun setLanguageAndVoice(languageTag: String?, voiceName: String? = null) {
        currentProvider.setLanguageAndVoice(languageTag, voiceName)
    }

    interface OnVoiceFallbackListener {
        fun onVoiceFallback(originalVoice: String, fallbackVoice: String?, reason: String)
    }
    
    var fallbackListener: OnVoiceFallbackListener? = null
        set(value) {
            field = value
            // Delegate to providers if they support it
            (androidTtsProvider.get() as? AndroidTtsProvider)?.fallbackListener = value
        }

    fun stopNotificationTTS() {
        if (isReadingNotification) {
            Log.d("TextToSpeechHelper", "Interrupting notification reading.")
            stopAll()
            isReadingNotification = false
        }
    }

    fun prefetch(text: String) {
        currentProvider.prefetch(text)
    }

    fun stopAll() {
        currentProvider.stopAll()
    }

    fun shutdown() {
        currentProvider.shutdown()
    }
}

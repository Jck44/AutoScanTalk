package com.andreas_kratzer.ghosttalk.core.tts

import android.content.Context
import android.util.Log
import com.andreas_kratzer.ghosttalk.core.di.ApplicationScope
import com.andreas_kratzer.ghosttalk.core.settings.TtsSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
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

    private val lock = Any()

    private val _isFallbackActiveFlow = MutableStateFlow(false)
    val isFallbackActiveFlow: StateFlow<Boolean> = _isFallbackActiveFlow.asStateFlow()

    private val currentProviderFlow = MutableStateFlow<TtsProvider>(androidTtsProvider.get())
    private var currentProvider: TtsProvider 
        get() = synchronized(lock) { currentProviderFlow.value }
        set(value) { synchronized(lock) { currentProviderFlow.value = value } }
    
    @OptIn(ExperimentalCoroutinesApi::class)
    val availableVoicesFlow: StateFlow<List<TtsVoice>> = currentProviderFlow
        .flatMapLatest { it.availableVoicesFlow }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    open val isReady: Boolean 
        get() = synchronized(lock) {
            currentProvider.isReady
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    val isReadyFlow: StateFlow<Boolean> = currentProviderFlow
        .flatMapLatest { provider -> provider.isReadyFlow }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, false)

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

        // Observe Google TTS language/voice changes for Android Provider
        scope.launch {
            combine(
                settingsRepository.googleTtsLanguageFlow,
                settingsRepository.googleTtsVoiceNameFlow,
                settingsRepository.appLanguageFlow
            ) { lang, voice, _ -> lang to voice }
                .collect { (lang, voice) ->
                    Log.d("TextToSpeechHelper", "Google TTS Settings updated: lang=$lang, voice=$voice")
                    androidTtsProvider.get().setLanguageAndVoice(lang, voice)
                }
        }

        // Observe ElevenLabs TTS language/voice changes for ElevenLabs Provider
        scope.launch {
            combine(
                settingsRepository.elevenLabsTtsLanguageFlow,
                settingsRepository.elevenLabsTtsVoiceNameFlow,
                settingsRepository.appLanguageFlow
            ) { lang, voice, _ -> lang to voice }
                .collect { (lang, voice) ->
                    Log.d("TextToSpeechHelper", "ElevenLabs TTS Settings updated: lang=$lang, voice=$voice")
                    elevenLabsTtsProvider.get().setLanguageAndVoice(lang, voice)
                }
        }
    }

    fun switchProvider(engineId: String?) {
        synchronized(lock) {
            val baseProvider = when (engineId) {
                "elevenlabs" -> elevenLabsTtsProvider.get()
                else -> androidTtsProvider.get()
            }
            
            val nextProvider = if (engineId == "elevenlabs") {
                FallbackTtsProvider(
                    primary = baseProvider,
                    fallback = androidTtsProvider.get(),
                    scope = scope,
                    onFallbackTriggered = { error ->
                        _isFallbackActiveFlow.value = true
                    }
                )
            } else {
                _isFallbackActiveFlow.value = false
                baseProvider
            }
            
            val currentBase = when (val curr = currentProvider) {
                is FallbackTtsProvider -> elevenLabsTtsProvider.get()
                else -> curr
            }
            
            if (baseProvider != currentBase) {
                currentProvider.stopAll()
                currentProvider = nextProvider
            }
        }
    }

    open fun speak(text: String, queueMode: Int = 0, onDone: (() -> Unit)? = null, onError: ((String) -> Unit)? = null) {
        synchronized(lock) {
            if (currentProvider is FallbackTtsProvider) {
                _isFallbackActiveFlow.value = false
            }
            val resolvedOnError: (String) -> Unit = onError ?: { error ->
                Log.w("TextToSpeechHelper", "No onError provided, completing via onDone: $error")
                onDone?.invoke()
            }
            currentProvider.speak(text, queueMode, onDone, resolvedOnError)
        }
    }

    open fun speakRouted(
        text: String, 
        deviceAddress: String?, 
        queueMode: Int = 0,
        isForCues: Boolean = false,
        onDone: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        synchronized(lock) {
            val provider = currentProvider
            val engineType = if (provider is FallbackTtsProvider) "elevenlabs" else "android"
            Log.i("TextToSpeechHelper", "speakRouted: engine=$engineType, text='${text.take(20)}...', isReady=${provider.isReady}")
            if (provider is FallbackTtsProvider) {
                _isFallbackActiveFlow.value = false
            }
            val resolvedOnError: (String) -> Unit = onError ?: { error ->
                Log.w("TextToSpeechHelper", "No onError provided, completing via onDone: $error")
                onDone?.invoke()
            }
            provider.speakRouted(text, deviceAddress, queueMode, isForCues, onDone, resolvedOnError)
        }
    }

    fun getAvailableLanguages(): List<Locale> {
        return synchronized(lock) {
            currentProvider.getAvailableLanguages()
        }
    }

    fun setVoice(voiceName: String?) {
        synchronized(lock) {
            currentProvider.setVoice(voiceName)
        }
    }

    fun setLanguageAndVoice(languageTag: String?, voiceName: String? = null) {
        synchronized(lock) {
            currentProvider.setLanguageAndVoice(languageTag, voiceName)
        }
    }

    interface OnVoiceFallbackListener {
        fun onVoiceFallback(originalVoice: String, fallbackVoice: String?, reason: String)
    }
    
    var fallbackListener: OnVoiceFallbackListener? = null
        set(value) {
            synchronized(lock) {
                field = value
                // Delegate to providers if they support it
                androidTtsProvider.get().fallbackListener = value
            }
        }

    fun stopNotificationTTS() {
        if (isReadingNotification) {
            Log.d("TextToSpeechHelper", "Interrupting notification reading.")
            stopAll()
            isReadingNotification = false
        }
    }

    suspend fun prefetch(text: String) {
        val provider = synchronized(lock) { currentProvider }
        (provider as? CacheableTtsProvider)?.prefetch(text)
    }

    fun isCached(text: String): Boolean {
        return synchronized(lock) {
            (currentProvider as? CacheableTtsProvider)?.isCached(text) == true
        }
    }

    fun stopAll() {
        synchronized(lock) {
            currentProvider.stopAll()
        }
    }

    open fun isSpeaking(): Boolean {
        return synchronized(lock) {
            currentProvider.isSpeaking()
        }
    }
}

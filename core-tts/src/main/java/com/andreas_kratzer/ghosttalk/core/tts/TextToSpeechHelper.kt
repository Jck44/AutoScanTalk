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
import kotlinx.coroutines.flow.combine
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

        // Observe Google TTS language/voice changes for Android Provider
        scope.launch {
            combine(
                settingsRepository.googleTtsLanguageFlow,
                settingsRepository.googleTtsVoiceNameFlow
            ) { lang, voice -> lang to voice }
                .collect { (lang, voice) ->
                    Log.d("TextToSpeechHelper", "Google TTS Settings updated: lang=$lang, voice=$voice")
                    androidTtsProvider.get().setLanguageAndVoice(lang, voice)
                }
        }

        // Observe ElevenLabs TTS language/voice changes for ElevenLabs Provider
        scope.launch {
            combine(
                settingsRepository.elevenLabsTtsLanguageFlow,
                settingsRepository.elevenLabsTtsVoiceNameFlow
            ) { lang, voice -> lang to voice }
                .collect { (lang, voice) ->
                    Log.d("TextToSpeechHelper", "ElevenLabs TTS Settings updated: lang=$lang, voice=$voice")
                    elevenLabsTtsProvider.get().setLanguageAndVoice(lang, voice)
                }
        }
    }

    fun switchProvider(engineId: String?) {
        val nextProvider = when (engineId) {
            "elevenlabs" -> elevenLabsTtsProvider.get()
            else -> androidTtsProvider.get()
        }
        
        if (nextProvider != currentProvider) {
            currentProvider.stopAll()
            currentProvider = nextProvider
            // Providers maintain their own settings via independent flows, no need to force sync here
        }
    }

    open fun speak(text: String, queueMode: Int = 0, onDone: (() -> Unit)? = null, onError: ((String) -> Unit)? = null) {
        val provider = currentProvider
        if (provider is ElevenLabsTtsProvider) {
            var fallbackTriggered = false
            provider.speak(
                text = text,
                queueMode = queueMode,
                onDone = {
                    if (!fallbackTriggered) onDone?.invoke()
                },
                onError = { error ->
                    Log.w("TextToSpeechHelper", "ElevenLabs speak failed, falling back to Android TTS: $error")
                    fallbackTriggered = true
                    androidTtsProvider.get().speak(text, queueMode, onDone, onError)
                }
            )
        } else {
            provider.speak(text, queueMode, onDone, onError)
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
        val provider = currentProvider
        val engineType = if (provider is ElevenLabsTtsProvider) "elevenlabs" else "android"
        Log.i("TextToSpeechHelper", "speakRouted: engine=$engineType, text='${text.take(20)}...', isReady=${provider.isReady}")
        
        if (provider is ElevenLabsTtsProvider) {
            var fallbackTriggered = false
            provider.speakRouted(
                text = text,
                deviceAddress = deviceAddress,
                queueMode = queueMode,
                isForCues = isForCues,
                onDone = {
                    if (!fallbackTriggered) onDone?.invoke()
                },
                onError = { error ->
                    Log.w("TextToSpeechHelper", "ElevenLabs speakRouted failed, falling back to Android TTS: $error")
                    fallbackTriggered = true
                    androidTtsProvider.get().speakRouted(text, deviceAddress, queueMode, isForCues, onDone, onError)
                }
            )
        } else {
            provider.speakRouted(text, deviceAddress, queueMode, isForCues, onDone, onError)
        }
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
            androidTtsProvider.get().fallbackListener = value
        }

    fun stopNotificationTTS() {
        if (isReadingNotification) {
            Log.d("TextToSpeechHelper", "Interrupting notification reading.")
            stopAll()
            isReadingNotification = false
        }
    }

    suspend fun prefetch(text: String) {
        currentProvider.prefetch(text)
    }

    fun isCached(text: String): Boolean {
        return currentProvider.isCached(text)
    }

    fun stopAll() {
        currentProvider.stopAll()
    }

    fun shutdown() {
        currentProvider.shutdown()
    }
}

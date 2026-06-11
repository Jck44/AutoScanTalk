package com.andreas_kratzer.ghosttalk.core.tts

import android.util.Log
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

class FallbackTtsProvider(
    private val primary: TtsProvider,
    private val fallback: TtsProvider,
    private val onFallbackTriggered: (String) -> Unit
) : CacheableTtsProvider {

    override val isReady: Boolean
        get() = primary.isReady || fallback.isReady

    override val availableVoicesFlow: StateFlow<List<TtsVoice>>
        get() = primary.availableVoicesFlow

    override fun speak(text: String, queueMode: Int, onDone: (() -> Unit)?, onError: ((String) -> Unit)?) {
        primary.speak(
            text = text,
            queueMode = queueMode,
            onDone = onDone,
            onError = { error ->
                Log.w("FallbackTtsProvider", "Primary TTS failed: $error. Falling back to secondary provider.")
                onFallbackTriggered(error)
                fallback.speak(text, queueMode, onDone, onError)
            }
        )
    }

    override fun speakRouted(
        text: String,
        deviceAddress: String?,
        queueMode: Int,
        isForCues: Boolean,
        onDone: (() -> Unit)?,
        onError: ((String) -> Unit)?
    ) {
        primary.speakRouted(
            text = text,
            deviceAddress = deviceAddress,
            queueMode = queueMode,
            isForCues = isForCues,
            onDone = onDone,
            onError = { error ->
                Log.w("FallbackTtsProvider", "Primary routed TTS failed: $error. Falling back to secondary provider.")
                onFallbackTriggered(error)
                fallback.speakRouted(text, deviceAddress, queueMode, isForCues, onDone, onError)
            }
        )
    }

    override suspend fun prefetch(text: String) {
        (primary as? CacheableTtsProvider)?.prefetch(text)
    }

    override fun stopAll() {
        primary.stopAll()
        fallback.stopAll()
    }

    override fun shutdown() {
        primary.shutdown()
        fallback.shutdown()
    }

    override fun isSpeaking(): Boolean {
        return primary.isSpeaking() || fallback.isSpeaking()
    }

    override fun setLanguageAndVoice(languageTag: String?, voiceName: String?) {
        primary.setLanguageAndVoice(languageTag, voiceName)
    }

    override fun setVoice(voiceName: String?) {
        primary.setVoice(voiceName)
    }

    override fun getAvailableLanguages(): List<Locale> {
        return primary.getAvailableLanguages()
    }

    override fun getAvailableVoices(languageTag: String?): List<TtsVoice> {
        return primary.getAvailableVoices(languageTag)
    }

    override fun isCached(text: String): Boolean {
        return (primary as? CacheableTtsProvider)?.isCached(text) == true ||
               (fallback as? CacheableTtsProvider)?.isCached(text) == true
    }
}

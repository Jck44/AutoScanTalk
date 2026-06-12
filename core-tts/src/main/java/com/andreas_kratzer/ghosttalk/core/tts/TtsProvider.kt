package com.andreas_kratzer.ghosttalk.core.tts

import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

interface TtsProvider {
    val isReady: Boolean
    val isReadyFlow: StateFlow<Boolean>
    val availableVoicesFlow: StateFlow<List<TtsVoice>>
    
    /**
     * Speaks the given [text].
     * Exactly one terminal callback must be called per call:
     * - [onDone] on success
     * - [onError] on failure
     * - [onDone] on cancel
     * Never invoke both [onDone] and [onError].
     */
    fun speak(text: String, queueMode: Int, onDone: (() -> Unit)?, onError: ((String) -> Unit)? = null)

    /**
     * Speaks the given [text] routed to a specific Bluetooth audio receiver.
     * Exactly one terminal callback must be called per call:
     * - [onDone] on success
     * - [onError] on failure
     * - [onDone] on cancel
     * Never invoke both [onDone] and [onError].
     */
    fun speakRouted(text: String, deviceAddress: String?, queueMode: Int, isForCues: Boolean, onDone: (() -> Unit)?, onError: ((String) -> Unit)? = null)
    fun stopAll()
    fun shutdown()
    fun isSpeaking(): Boolean
    
    fun setLanguageAndVoice(languageTag: String?, voiceName: String?)

    fun setVoice(voiceName: String?)
    fun getAvailableLanguages(): List<Locale>
    fun getAvailableVoices(languageTag: String?): List<TtsVoice>
}

interface CacheableTtsProvider : TtsProvider {
    suspend fun prefetch(text: String)
    fun isCached(text: String): Boolean
}

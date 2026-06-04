package com.andreas_kratzer.ghosttalk.core.tts

import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

interface TtsProvider {
    val isReady: Boolean
    val availableVoicesFlow: StateFlow<List<TtsVoice>>
    
    fun speak(text: String, queueMode: Int, onDone: (() -> Unit)?, onError: ((String) -> Unit)? = null)
    fun speakRouted(text: String, deviceAddress: String?, queueMode: Int, isForCues: Boolean, onDone: (() -> Unit)?, onError: ((String) -> Unit)? = null)
    suspend fun prefetch(text: String)
    fun stopAll()
    fun shutdown()
    fun isSpeaking(): Boolean
    
    fun setLanguageAndVoice(languageTag: String?, voiceName: String?)

    fun setVoice(voiceName: String?)
    fun getAvailableLanguages(): List<Locale>
    fun getAvailableVoices(languageTag: String?): List<TtsVoice>
    fun isCached(text: String): Boolean
}

package com.andreas_kratzer.ghosttalk.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import com.andreas_kratzer.ghosttalk.core.audio.RoutedAudioPlayer
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TextToSpeechHelper @Inject constructor(
    @param:ApplicationContext val context: Context,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val routedAudioPlayer: RoutedAudioPlayer,
    private val voiceManager: TtsVoiceManager
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var initialized = false
    val isReady: Boolean get() = initialized
    private val handler = Handler(Looper.getMainLooper())
    private var pendingLanguageTag: String? = null
    private var pendingVoiceName: String? = null

    // Support for interrupting ONLY notifications
    var isReadingNotification: Boolean = false

    private data class PlaybackRequest(val file: File, val deviceAddress: String?, val onDoneCallback: (() -> Unit)?)
    private val playRequests = ConcurrentHashMap<String, PlaybackRequest>()

    init {
        initializeInternal()

        // Centralized configuration observer
        scope.launch {
            combine(
                settingsRepository.ttsLanguageFlow,
                settingsRepository.ttsVoiceNameFlow
            ) { lang, voice -> lang to voice }
                .collect { (newLanguage, newVoice) ->
                    Log.d("TextToSpeechHelper", "Settings updated: lang=$newLanguage, voice=$newVoice")
                    setLanguageAndVoice(newLanguage, newVoice)
                }
        }
    }

    private fun initializeInternal() {
        if (tts != null) return
        try {
            tts = TextToSpeech(context, this)
        } catch (e: Exception) {
            showToast("Error initializing TTS: ${e.message}")
        }
    }

    private fun ensureReady() {
        if (tts == null) {
            Log.d("TextToSpeechHelper", "TTS instance was null, re-initializing...")
            initializeInternal()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            initialized = true
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    val request = playRequests.remove(utteranceId)
                    if (request != null) {
                        handler.postDelayed({
                            if (request.file.exists() && request.file.length() > 0) {
                                routedAudioPlayer.playAudioFile(request.file, request.deviceAddress) {
                                    request.file.delete()
                                    // Invoke the callback if provided, on the main thread
                                    request.onDoneCallback?.let { callback ->
                                        handler.post { callback() }
                                    }
                                }
                            } else {
                                Log.e("TextToSpeechHelper", "Generated TTS file is empty or missing")
                                // Even if file generation fails, we should invoke callback to not block UI flows
                                request.onDoneCallback?.let { callback ->
                                    handler.post { callback() }
                                }
                            }
                        }, 50)
                    } else if (utteranceId != null && utteranceId.startsWith("direct_")) {
                        // This was a non-routed default TTS speak call
                        val callback = directCallbacks.remove(utteranceId)
                        callback?.let { handler.post { it() } }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    val request = playRequests.remove(utteranceId)
                    request?.file?.delete()
                    request?.onDoneCallback?.let { handler.post { it() } }
                    
                    if (utteranceId != null && utteranceId.startsWith("direct_")) {
                        directCallbacks.remove(utteranceId)?.let { handler.post { it() } }
                    }
                }
            })
            // Verzögern, damit die TTS Engine Zeit hat, das Voice-Array zu befüllen (asynchrones Android Verhalten)
            handler.postDelayed({
                applyPendingLanguageAndVoice()
            }, 300)
        } else {
            showToast("TTS init failed! Status code: $status")
            initialized = false
            tts = null // Reset so ensurReady can retry
        }
    }

    private fun applyPendingLanguageAndVoice() {
        if (!initialized) return
        setLanguageAndVoice(pendingLanguageTag, pendingVoiceName)
    }

    private fun showToast(message: String) {
        Log.e("TextToSpeechHelper", message)
        handler.post {
            Toast.makeText(context, "TTS Debug: $message", Toast.LENGTH_LONG).show()
        }
    }

    // Support for direct callbacks
    private val directCallbacks = ConcurrentHashMap<String, () -> Unit>()

    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH, onDone: (() -> Unit)? = null) {
        speakRouted(text, null, queueMode, false, onDone)
    }

    fun speakRouted(
        text: String, 
        deviceAddress: String?, 
        queueMode: Int = TextToSpeech.QUEUE_FLUSH,
        isForCues: Boolean = false,
        onDone: (() -> Unit)? = null
    ) {
        ensureReady()
        if (!initialized || tts == null) {
            showToast("TTS not initialized, cannot speak.")
            onDone?.invoke()
            return
        }

        if (queueMode == TextToSpeech.QUEUE_FLUSH) {
            // Cancel generating TTS and clear old player queues
            routedAudioPlayer.stopAll()
            
            // Invoke all pending callbacks before clearing so UI flows (like scanner) don't hang forever
            playRequests.values.forEach { it.onDoneCallback?.let { cb -> handler.post { cb() } } }
            directCallbacks.values.forEach { handler.post { it() } }
            
            playRequests.clear()
            directCallbacks.clear()
        }
        
        // Volume modifiers removed
        
        val finalSpeakText = text

        if (deviceAddress == null) {
            val utteranceId = "direct_${System.currentTimeMillis()}_${text.hashCode()}"
            if (onDone != null) {
                directCallbacks[utteranceId] = onDone
            }
            tts?.speak(finalSpeakText, queueMode, null, utteranceId)
            return
        }

        val utteranceId = "routed_${System.currentTimeMillis()}_${text.hashCode()}"
        val cacheFile = File(context.cacheDir, "$utteranceId.wav")
        // Pass volumeMultiplier to the player
        playRequests[utteranceId] = PlaybackRequest(cacheFile, deviceAddress, onDone)

        val params = android.os.Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        tts?.synthesizeToFile(finalSpeakText, params, cacheFile, utteranceId)
    }

    interface OnVoiceFallbackListener {
        fun onVoiceFallback(originalVoice: String, fallbackVoice: String?, reason: String)
    }
    
    var fallbackListener: OnVoiceFallbackListener? = null

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val activeNetwork = cm?.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Setzt die aktive TTS-Sprache und optional eine spezifische Stimme (Voice).
     * @param languageTag z.B. "de-DE", "en-US". Wird null oder "default" übergeben, wird die Systemsprache genutzt.
     * @param voiceName Der exakte Bezeichner der TTS Voice, oder null für den Standard.
     */
    fun setLanguageAndVoice(languageTag: String?, voiceName: String? = null) {
        pendingLanguageTag = languageTag
        pendingVoiceName = voiceName
        
        if (!initialized || tts == null) {
            return
        }

        val locale = if (languageTag.isNullOrEmpty() || languageTag == "default") {
            settingsRepository.appLanguage?.let { Locale.forLanguageTag(it) } ?: Locale.getDefault()
        } else {
            Locale.forLanguageTag(languageTag)
        }

        val langResult = tts?.setLanguage(locale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.e("TextToSpeechHelper", "Language $languageTag not supported by system.")
        }
        
        if (!voiceName.isNullOrEmpty()) {
            val targetVoice = voiceManager.findVoice(tts, voiceName)
            if (targetVoice != null) {
                if (targetVoice.isNetworkConnectionRequired && !isNetworkAvailable()) {
                    Log.w("TextToSpeechHelper", "Voice $voiceName requires network but system is offline. Finding local fallback...")
                    
                    val allVoices = tts?.voices ?: emptySet()
                    val localFallback = allVoices.filter { 
                        it.locale.language == targetVoice.locale.language && 
                        it.locale.country == targetVoice.locale.country &&
                        !it.isNetworkConnectionRequired
                    }.firstOrNull()
                    
                    if (localFallback != null) {
                        tts?.voice = localFallback
                        fallbackListener?.onVoiceFallback(voiceName, localFallback.name, "No Network")
                    } else {
                        tts?.language = locale
                        fallbackListener?.onVoiceFallback(voiceName, null, "No Network, No Local Voice")
                    }
                } else {
                    tts?.voice = targetVoice
                }
            } else {
                Log.w("TextToSpeechHelper", "Requested voice $voiceName not found. Falling back to default voice for ${locale.displayName}.")
                tts?.language = locale
                fallbackListener?.onVoiceFallback(voiceName, null, "Voice Not Found")
            }
        }
    }

    /**
     * Erlaubt das direkte Setzen einer Stimme unabhängig von der Sprache (interner Helper)
     */
    fun setVoice(voiceName: String?) {
        voiceManager.findVoice(tts, voiceName)?.let { tts?.voice = it }
    }

    /**
     * Gibt eine Liste aller verfügbaren Sprachen zurück, die das installierte TTS-System spricht.
     */
    fun getAvailableLanguages(): List<Locale> {
        return voiceManager.getAvailableLanguages(tts)
    }

    /**
     * Gibt eine Liste aller verfügbaren Stimmen für eine spezifizierte Sprache zurück.
     */
    fun getAvailableVoices(languageTag: String?): List<android.speech.tts.Voice> {
        val resolvedTag = if (languageTag.isNullOrEmpty() || languageTag == "default") {
            settingsRepository.appLanguage ?: "default"
        } else {
            languageTag
        }
        return voiceManager.getAvailableVoices(tts, resolvedTag)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        initialized = false
    }

    /**
     * Stoppt die aktuelle Sprachausgabe NUR, wenn gerade eine Benachrichtigung vorgelesen wird.
     * Dies verhindert, dass normale Button-Klicks ("Sprich Text") durch versehentliches 
     * doppeltes Drücken abgebrochen werden.
     */
    fun stopNotificationTTS() {
        if (isReadingNotification) {
            Log.d("TextToSpeechHelper", "Unterbreche Benachrichtigungs-Vorlesen.")
            tts?.stop()
            routedAudioPlayer.stopAll()
            
            // Pending callbacks for the notification also need to be invoked to free up scanners
            playRequests.values.forEach { it.onDoneCallback?.let { cb -> handler.post { cb() } } }
            directCallbacks.values.forEach { handler.post { it() } }
            
            playRequests.clear()
            directCallbacks.clear()
            
            isReadingNotification = false
        } else {
            Log.d("TextToSpeechHelper", "stopNotificationTTS aufgerufen, aber isReadingNotification ist false. Ignoriere.")
        }
    }
}

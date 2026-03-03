package com.andreas_kratzer.ghosttalk.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import com.andreas_kratzer.ghosttalk.core.AudioDeviceManager
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext

class TextToSpeechHelper @Inject constructor(
    @param:ApplicationContext val context: Context
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var initialized = false
    val isReady: Boolean get() = initialized
    private val handler = Handler(Looper.getMainLooper())
    private var pendingLanguageTag: String? = null
    private var pendingVoiceName: String? = null

    private val audioDeviceManager = AudioDeviceManager(context)
    private val routedAudioPlayer = RoutedAudioPlayer(context, audioDeviceManager)
    
    private data class PlaybackRequest(val file: File, val deviceAddress: String?, val onDoneCallback: (() -> Unit)?)
    private val playRequests = ConcurrentHashMap<String, PlaybackRequest>()

    init {
        try {
            tts = TextToSpeech(context, this)
        } catch (e: Exception) {
            showToast("Error initializing TTS: ${e.message}")
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
        speakRouted(text, null, queueMode, onDone)
    }

    fun speakRouted(text: String, deviceAddress: String?, queueMode: Int = TextToSpeech.QUEUE_FLUSH, onDone: (() -> Unit)? = null) {
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

        if (deviceAddress == null) {
            val utteranceId = "direct_${System.currentTimeMillis()}_${text.hashCode()}"
            if (onDone != null) {
                directCallbacks[utteranceId] = onDone
            }
            tts?.speak(text, queueMode, null, utteranceId)
            return
        }

        val utteranceId = "routed_${System.currentTimeMillis()}_${text.hashCode()}"
        val cacheFile = File(context.cacheDir, "$utteranceId.wav")
        playRequests[utteranceId] = PlaybackRequest(cacheFile, deviceAddress, onDone)

        val params = android.os.Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        tts?.synthesizeToFile(text, params, cacheFile, utteranceId)
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
            Locale.getDefault()
        } else {
            Locale.forLanguageTag(languageTag)
        }

        val langResult = tts?.setLanguage(locale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.e("TextToSpeechHelper", "Language $languageTag not supported by system.")
            // Even if language fails, we try to proceed with voices if possible
        }
        
        // Wenn eine spezifische Stimme gewünscht ist, versuche sie zu setzen
        if (!voiceName.isNullOrEmpty()) {
            val allVoices = tts?.voices
            if (allVoices.isNullOrEmpty()) {
                Log.d("TextToSpeechHelper", "Voices not yet loaded. Retrying voice application in 500ms...")
                handler.postDelayed({ applyPendingLanguageAndVoice() }, 500)
                return
            }

            val targetVoice = allVoices.find { it.name == voiceName }
            if (targetVoice != null) {
                if (targetVoice.isNetworkConnectionRequired && !isNetworkAvailable()) {
                    Log.w("TextToSpeechHelper", "Voice $voiceName requires network but system is offline. Finding local fallback...")
                    
                    // Fallback level 1: Find a local voice with same locale
                    val localFallback = allVoices.filter { 
                        it.locale.language == targetVoice.locale.language && 
                        it.locale.country == targetVoice.locale.country &&
                        !it.isNetworkConnectionRequired
                    }.firstOrNull()
                    
                    if (localFallback != null) {
                        tts?.voice = localFallback
                        fallbackListener?.onVoiceFallback(voiceName, localFallback.name, "No Network")
                        Log.i("TextToSpeechHelper", "Falling back from $voiceName to local voice ${localFallback.name}")
                    } else {
                        // Fallback level 2: Use system default for that language
                        tts?.language = locale
                        fallbackListener?.onVoiceFallback(voiceName, null, "No Network, No Local Voice")
                        Log.i("TextToSpeechHelper", "Falling back from $voiceName to system default for ${locale.displayName}")
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
        if (!initialized || tts == null || voiceName.isNullOrEmpty()) return
        val voice = tts?.voices?.find { it.name == voiceName }
        if (voice != null) {
            tts?.voice = voice
        }
    }

    /**
     * Gibt eine Liste aller verfügbaren Sprachen zurück, die das installierte TTS-System spricht.
     */
    fun getAvailableLanguages(): List<Locale> {
        return try {
            tts?.availableLanguages?.toList()?.sortedBy { it.displayName } ?: emptyList()
        } catch (e: Exception) {
            Log.e("TextToSpeechHelper", "Error getting languages", e)
            emptyList()
        }
    }

    /**
     * Gibt eine Liste aller verfügbaren Stimmen für eine spezifizierte Sprache zurück.
     */
    fun getAvailableVoices(languageTag: String?): List<android.speech.tts.Voice> {
        if (!initialized || tts == null) return emptyList()
        
        val targetLocale = if (languageTag.isNullOrEmpty() || languageTag == "default") {
            Locale.getDefault()
        } else {
            Locale.forLanguageTag(languageTag)
        }
        
        return try {
            tts?.voices?.filter { voice ->
                voice.locale.language == targetLocale.language && voice.locale.country == targetLocale.country
            }?.sortedBy { it.name } ?: emptyList()
        } catch (e: Exception) {
            Log.e("TextToSpeechHelper", "Error getting voices", e)
            emptyList()
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        initialized = false
    }
}

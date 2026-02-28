package com.example.gostalk.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import java.io.File
import java.util.Locale
import com.example.gostalk.core.AudioDeviceManager
import com.example.gostalk.tts.RoutedAudioPlayer
import java.util.concurrent.ConcurrentHashMap

class TextToSpeechHelper(
    private val context: Context
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var initialized = false
    val isReady: Boolean get() = initialized
    private val handler = Handler(Looper.getMainLooper())
    private var pendingLanguageTag: String? = null
    private var pendingVoiceName: String? = null

    private val audioDeviceManager = AudioDeviceManager(context)
    private val routedAudioPlayer = RoutedAudioPlayer(context, audioDeviceManager)
    
    private data class PlaybackRequest(val file: File, val deviceAddress: String?)
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
                                }
                            } else {
                                Log.e("TextToSpeechHelper", "Generated TTS file is empty or missing")
                            }
                        }, 50)
                    }
                }

                override fun onError(utteranceId: String?) {
                    playRequests.remove(utteranceId)?.file?.delete()
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

    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        speakRouted(text, null, queueMode)
    }

    fun speakRouted(text: String, deviceAddress: String?, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        if (!initialized || tts == null) {
            showToast("TTS not initialized, cannot speak.")
            return
        }

        if (deviceAddress == null) {
            tts?.speak(text, queueMode, null, null)
            return
        }

        val utteranceId = "routed_${System.currentTimeMillis()}_${text.hashCode()}"
        val cacheFile = File(context.cacheDir, "$utteranceId.wav")
        playRequests[utteranceId] = PlaybackRequest(cacheFile, deviceAddress)

        val params = android.os.Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        tts?.synthesizeToFile(text, params, cacheFile, utteranceId)
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
            showToast("Language $languageTag not supported.")
            // Weiter ausführen, auch wenn Sprache fehlt, falls eine Custom-Voice das überschreibt
        }
        
        // Wenn eine spezifische Stimme gewünscht ist, versuche sie zu setzen
        if (!voiceName.isNullOrEmpty()) {
            if (tts?.voices.isNullOrEmpty()) {
                Log.d("TextToSpeechHelper", "Voices not yet loaded. Retrying voice application in 500ms...")
                handler.postDelayed({ applyPendingLanguageAndVoice() }, 500)
                return
            }

            val voice = tts?.voices?.find { it.name == voiceName }
            if (voice != null) {
                tts?.voice = voice
            } else {
                Log.w("TextToSpeechHelper", "Requested voice $voiceName not found in ${tts?.voices?.size} voices, falling back to default.")
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

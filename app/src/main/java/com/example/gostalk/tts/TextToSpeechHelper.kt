package com.example.gostalk.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import java.util.Locale

class TextToSpeechHelper(
    private val context: Context
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var initialized = false
    val isReady: Boolean get() = initialized
    private val handler = Handler(Looper.getMainLooper())
    private var pendingLanguageTag: String? = null

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
            setLanguageAndVoice(pendingLanguageTag)
        } else {
            showToast("TTS init failed! Status code: $status")
            initialized = false
        }
    }

    private fun showToast(message: String) {
        Log.e("TextToSpeechHelper", message)
        handler.post {
            Toast.makeText(context, "TTS Debug: $message", Toast.LENGTH_LONG).show()
        }
    }

    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        if (initialized && tts != null) {
            tts?.speak(text, queueMode, null, null)
        } else {
            showToast("TTS not initialized, cannot speak.")
        }
    }

    /**
     * Setzt die aktive TTS-Sprache und optional eine spezifische Stimme (Voice).
     * @param languageTag z.B. "de-DE", "en-US". Wird null oder "default" übergeben, wird die Systemsprache genutzt.
     * @param voiceName Der exakte Bezeichner der TTS Voice, oder null für den Standard.
     */
    fun setLanguageAndVoice(languageTag: String?, voiceName: String? = null) {
        if (!initialized || tts == null) {
            pendingLanguageTag = languageTag // Puffern bis onInit feuert (Voice buffering omitting for simplicity unless requested)
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
            return
        }
        
        // Wenn eine spezifische Stimme gewünscht ist, versuche sie zu setzen
        if (!voiceName.isNullOrEmpty()) {
            val voice = tts?.voices?.find { it.name == voiceName }
            if (voice != null) {
                tts?.voice = voice
            } else {
                Log.w("TextToSpeechHelper", "Requested voice $voiceName not found, falling back to default.")
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

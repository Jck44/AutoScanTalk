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
            setLanguage(pendingLanguageTag)
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
     * Setzt die aktive TTS-Sprache.
     * @param languageTag z.B. "de-DE", "en-US". Wird null oder "default" übergeben, wird die Systemsprache genutzt.
     */
    fun setLanguage(languageTag: String?) {
        if (!initialized || tts == null) {
            pendingLanguageTag = languageTag // Puffern bis onInit feuert
            return
        }

        val locale = if (languageTag.isNullOrEmpty() || languageTag == "default") {
            Locale.getDefault()
        } else {
            Locale.forLanguageTag(languageTag)
        }

        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            showToast("Language $languageTag not supported.")
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

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        initialized = false
    }
}

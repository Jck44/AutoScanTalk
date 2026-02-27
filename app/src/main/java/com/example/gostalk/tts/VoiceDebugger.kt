package com.example.gostalk.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class VoiceDebugger(private val context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null

    fun start() {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val germanLocale = Locale.forLanguageTag("de-DE")
            val voices = tts?.voices?.filter { it.locale.language == germanLocale.language }
            
            Log.d("VoiceDebugger", "--- DUMPING GERMAN VOICES ---")
            voices?.forEach { voice ->
                Log.d("VoiceDebugger", "Name: ${voice.name}")
                Log.d("VoiceDebugger", "Quality: ${voice.quality}")
                Log.d("VoiceDebugger", "Latency: ${voice.latency}")
                Log.d("VoiceDebugger", "Network Required: ${voice.isNetworkConnectionRequired}")
                Log.d("VoiceDebugger", "Features: ${voice.features.joinToString()}")
                Log.d("VoiceDebugger", "----------------------------")
            }
            Log.d("VoiceDebugger", "--- END DUMP ---")
        }
    }
}

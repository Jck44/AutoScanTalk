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
            
            val logBuilder = StringBuilder()
            logBuilder.appendLine("--- DUMPING GERMAN VOICES ---")
            voices?.forEach { voice ->
                logBuilder.appendLine("Name: ${voice.name}")
                logBuilder.appendLine("Quality: ${voice.quality}")
                logBuilder.appendLine("Latency: ${voice.latency}")
                logBuilder.appendLine("Network Required: ${voice.isNetworkConnectionRequired}")
                logBuilder.appendLine("Features: ${voice.features?.joinToString()}")
                logBuilder.appendLine("----------------------------")
            }
            logBuilder.appendLine("--- END DUMP ---")
            
            val logOutput = logBuilder.toString()
            Log.e("VoiceDebugger", logOutput)
            
            try {
                val file = java.io.File(context.filesDir, "voice_dump.txt")
                file.writeText(logOutput)
            } catch (e: Exception) {
                Log.e("VoiceDebugger", "Failed to write voice dump file: \${e.message}")
            }
        }
    }
}

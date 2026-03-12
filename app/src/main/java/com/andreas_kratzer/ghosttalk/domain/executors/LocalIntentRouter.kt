package com.andreas_kratzer.ghosttalk.domain.executors

import com.andreas_kratzer.ghosttalk.core.ai.LocalIntentRouter
import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.TextPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class LocalIntent(val id: String, val description: String)

class LocalIntentRouter @Inject constructor(
    private val androidClockExecutor: AndroidClockExecutor,
    private val logger: Logger
) : LocalIntentRouter {
    // Note: JSON Schema constraint parsing in ML Kit Prompt API is still highly experimental.
    // For this Phase 1 integration, we instruct the model to return plain JSON via system prompt.
    private fun getSystemInstruction(intent: String, context: String): String {
        return """
            Du bist ein intelligenter Assistent für eine AAC-App (Unterstützte Kommunikation).
            Deine Aufgabe ist es, für den Intent "$intent" eine SEHR KURZE, FREUNDLICHE und NATÜRLICHE Antwort in deutscher Sprache zu generieren.
            Verwende dabei die bereitgestellten Daten.
            WICHTIG: Die Antwort muss die Informationen EXAKT und VOLLSTÄNDIG enthalten (z.B. die genaue Uhrzeit).
            Antworte NUR mit dem Text der Sprachausgabe, ohne Erklärungen oder JSON.
            
            Kontext-Daten:
            $context
        """.trimIndent()
    }

    override suspend fun generateRawResponse(prompt: String, maxTokens: Int): String = withContext(Dispatchers.IO) {
        try {
            val model = Generation.getClient()
            val textPart = TextPart(prompt)
            val builder = GenerateContentRequest.builder(textPart)
            builder.maxOutputTokens = maxTokens
            builder.temperature = 0.0f
            val request = builder.build()
            
            val response = model.generateContent(request)
            return@withContext response.candidates.firstOrNull()?.text ?: ""
        } catch (e: Exception) {
            logger.e("LocalIntentRouter", "Raw generation failed", e)
            ""
        }
    }

    fun findIntent(response: String): LocalIntent? {
        // Simple heuristic for now: check if response contains intent keywords
        return if (response.contains("ALARM_STATUS", ignoreCase = true)) {
            LocalIntent("alarm", "Nächsten Alarm abrufen")
        } else null
    }

    suspend fun executeIntent(intent: LocalIntent, onSpeak: (String) -> Unit) = executeIntent(intent.id, onSpeak)

    override suspend fun executeIntent(intentId: String, onSpeak: (String) -> Unit) = withContext(Dispatchers.IO) {
        try {
            val context = when (intentId) {
                "alarm" -> "Nächster Alarm: ${androidClockExecutor.getNextAlarm()}"
                else -> ""
            }

            val systemPrompt = getSystemInstruction(intentId, context)
            val naturalResponse = generateRawResponse(systemPrompt)
            
            if (naturalResponse.isNotBlank()) {
                onSpeak(naturalResponse)
            } else {
                // Fallback if AI fails
                val fallback = when (intentId) {
                    "alarm" -> androidClockExecutor.getNextAlarm()
                    else -> "Ich kann diesen Befehl gerade nicht ausführen."
                }
                onSpeak(fallback)
            }
            
        } catch (e: Exception) {
            logger.e("LocalIntentRouter", "Intent execution failed", e)
            onSpeak("Fehler bei der lokalen Verarbeitung.")
        }
    }

    override suspend fun routeIntent(onSpeak: (String) -> Unit) = withContext(Dispatchers.IO) {
        // Obsolete, replaced by executeIntent
        onSpeak("Befehl konnte nicht verarbeitet werden.")
    }
}

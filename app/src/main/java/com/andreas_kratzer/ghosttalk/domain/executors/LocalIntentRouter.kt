package com.andreas_kratzer.ghosttalk.domain.executors

import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.google.gson.JsonParser
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.TextPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class LocalIntentRouter @Inject constructor(
    private val systemTimeExecutor: SystemTimeExecutor,
    private val androidClockExecutor: AndroidClockExecutor,
    private val logger: Logger
) {
    // Note: JSON Schema constraint parsing in ML Kit Prompt API is still highly experimental.
    // For this Phase 1 integration, we instruct the model to return plain JSON via system prompt.
    private fun getSystemInstruction(): String {
        return """
            Du bist ein intelligenter Assistent für eine unterstützende Kommunikations-App (AAC). 
            Deine Aufgabe ist es, den Text des Nutzers in einen strukturierten Intent im JSON Format zu übersetzen.
            Antworte NUR mit validem JSON, ohne Markdown, ohne Erklärung.
            
            Der aktuelle Zeitstempel ist: ${systemTimeExecutor.getRawTimestampContext()}
            
            Mögliche Intents:
            1. Zeitabfrage: {"intent": "time", "query": "time", "response": "<natürliche Antwort zur Uhrzeit>"}
            2. Datumsabfrage: {"intent": "time", "query": "date", "response": "<natürliche Antwort zum Datum, wobei der Tag als z.B.: 'Heute ist der vierte Jänner 2025' zu formatieren sind>"}
            3. Wecker stellen: {"intent": "alarm", "action": "set", "hour": <0-23>, "minute": <0-59>}
            4. Unbekannt: {"intent": "unknown"}
        """.trimIndent()
    }

    suspend fun generateRawResponse(prompt: String, maxTokens: Int = 100): String = withContext(Dispatchers.IO) {
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

    suspend fun routeIntent(prompt: String, onSpeak: (String) -> Unit) = withContext(Dispatchers.IO) {
        try {
            val promptText = "${getSystemInstruction()}\n\nNutzer: $prompt"
            val text = generateRawResponse(promptText)
            
            // Log raw response for debugging
            logger.d("LocalIntentRouter", "Raw Gemini Nano response: $text")

            // Clean up backticks if model generated markdown
            val cleanJson = text.replace("```json", "").replace("```", "").trim()
            handleJsonIntent(cleanJson, onSpeak)
            
        } catch (e: Exception) {
            logger.e("LocalIntentRouter", "Intent routing failed", e)
            onSpeak("Fehler bei der lokalen Verarbeitung: ${e.message}")
        }
    }

    private fun handleJsonIntent(jsonString: String, onSpeak: (String) -> Unit) {
        try {
            val json = JsonParser.parseString(jsonString).asJsonObject
            val intentStr = if (json.has("intent")) json.get("intent").asString.lowercase() else ""
            when (intentStr) {
                "time", "date" -> {
                    val responseText = if (json.has("response")) json.get("response").asString else ""
                    if (responseText.isNotBlank()) {
                        onSpeak(responseText)
                    } else {
                        val query = if (json.has("query")) json.get("query").asString else ""
                        // Support both "date" intent and query="date"
                        if (query == "date" || intentStr == "date") {
                            onSpeak(systemTimeExecutor.getCurrentDateOutput())
                        } else {
                            onSpeak(systemTimeExecutor.getCurrentTimeOutput())
                        }
                    }
                }
                "alarm" -> {
                    val action = if (json.has("action")) json.get("action").asString else ""
                    if (action == "set") {
                        val hour = if (json.has("hour")) json.get("hour").asInt else -1
                        val minute = if (json.has("minute")) json.get("minute").asInt else 0
                        if (hour in 0..23) {
                            val success = androidClockExecutor.setAlarm(hour, minute, "GoSTalk Wecker")
                            if (success) {
                                onSpeak("Wecker wurde gestellt.")
                            } else {
                                onSpeak("Wecker konnte nicht gestellt werden.")
                            }
                        } else {
                            onSpeak("Ungültige Uhrzeit für den Wecker.")
                        }
                    } else {
                        onSpeak("Wecker löschen wird noch nicht unterstützt.")
                    }
                }
                "unknown" -> {
                    onSpeak("Ich habe den Befehl nicht verstanden.")
                }
                else -> {
                    onSpeak("Unbekannter Intent empfangen.")
                }
            }
        } catch (_: Exception) {
            logger.e("LocalIntentRouter", "JSON handling failed: $jsonString")
            onSpeak("Konnte das JSON nicht verarbeiten.")
        }
    }
}

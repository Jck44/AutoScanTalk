package com.andreas_kratzer.ghosttalk.domain.executors

import android.content.Context
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerationConfig
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.gson.JsonParser
import javax.inject.Inject

class LocalIntentRouter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val systemTimeExecutor: SystemTimeExecutor,
    private val androidClockExecutor: AndroidClockExecutor
) {
    // Note: JSON Schema constraint parsing in ML Kit Prompt API is still highly experimental.
    // For this Phase 1 integration, we instruct the model to return plain JSON via system prompt.
    private val systemInstruction = """
        Du bist ein intelligenter Assistent für eine unterstützende Kommunikations-App (AAC). 
        Deine Aufgabe ist es, den Text des Nutzers in einen strukturierten Intent im JSON Format zu übersetzen.
        Antworte NUR mit validem JSON, ohne Markdown, ohne Erklärung.
        
        Der aktuelle Zeitstempel ist: ${systemTimeExecutor.getRawTimestampContext()}
        
        Mögliche Intents:
        1. Zeitabfrage: {"intent": "time", "query": "time", "response": "<natürliche Antwort zur Uhrzeit, z.B. 'Es ist jetzt kurz nach elf Uhr'>"}
        2. Datumsabfrage: {"intent": "time", "query": "date", "response": "<natürliche Antwort zum Datum, z.B. 'Heute ist Donnerstag, der fünfte März'>"}
        3. Wecker stellen: {"intent": "alarm", "action": "set", "hour": <0-23>, "minute": <0-59>}
        4. Unbekannt: {"intent": "unknown"}
    """.trimIndent()

    suspend fun routeIntent(prompt: String, onSpeak: (String) -> Unit) = withContext(Dispatchers.IO) {
        try {
            val model = Generation.getClient()
            
            // Note: downloading the model can take time if not already on device.
            // model.download().collect { } // Optional explicit download

            val response = model.generateContent("$systemInstruction\n\nNutzer: $prompt")
            val text = response.candidates.firstOrNull()?.text ?: ""
            
            // Clean up backticks if model generated markdown
            val cleanJson = text.replace("```json", "").replace("```", "").trim()
            handleJsonIntent(cleanJson, onSpeak)
            
        } catch (e: Exception) {
            e.printStackTrace()
            onSpeak("Fehler bei der lokalen Verarbeitung: ${e.message}")
        }
    }

    private fun handleJsonIntent(jsonString: String, onSpeak: (String) -> Unit) {
        try {
            val json = JsonParser.parseString(jsonString).asJsonObject
            val intentStr = if (json.has("intent")) json.get("intent").asString else ""
            when (intentStr) {
                "time" -> {
                    val responseText = if (json.has("response")) json.get("response").asString else ""
                    if (responseText.isNotBlank()) {
                        onSpeak(responseText)
                    } else {
                        val query = if (json.has("query")) json.get("query").asString else ""
                        if (query == "date") {
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
        } catch (e: Exception) {
            e.printStackTrace()
            onSpeak("Konnte das JSON nicht verarbeiten.")
        }
    }
}

package com.andreas_kratzer.ghosttalk.domain

import android.util.Log
import com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper
import com.google.api.services.drive.Drive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * UseCase for interacting with Gemini AI.
 * Reuses the app's OAuth token for authentication.
 */
class GeminiUseCase(
    private val oauthTokenProvider: suspend () -> String?,
    private val driveProvider: suspend () -> Drive?
) {
    enum class ToolStatus {
        AVAILABLE,
        REQUIRES_AUTH,
        FAILED,
        PENDING
    }
    
    companion object {
        private const val TAG = "GeminiUseCase"
        private var activeModelName = "gemini-2.0-flash" 
        private const val BASE_URL_TEMPLATE = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent"
        private const val LIST_MODELS_URL = "https://generativelanguage.googleapis.com/v1beta/models"

        internal var modelInitialized = false
        internal var lastSuccess: Boolean? = null // null: unknown, true: success, false: failed

        internal fun resetHealthStateForTesting() {
            modelInitialized = false
            lastSuccess = null
        }
    }
    private var appCommandHandler: ((String, Map<String, String>) -> Unit)? = null

    fun setAppCommandHandler(handler: (String, Map<String, String>) -> Unit) {
        this.appCommandHandler = handler
    }

    suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        val token = oauthTokenProvider() ?: return@withContext "Fehler: Nicht angemeldet (OAuth Token fehlt)."
        
        Log.d(TAG, "Generating response for prompt: $prompt")
        
        if (!modelInitialized) {
            tryToSelectBestModel()
            modelInitialized = true
        }
        
        try {
            val result = performGeneration(token, prompt)
            lastSuccess = true
            return@withContext result
        } catch (e: Exception) {
            lastSuccess = false
            val errorMsg = e.message ?: ""
            if (errorMsg.contains("404") || errorMsg.contains("429")) {
                Log.w(TAG, "Model $activeModelName failed (Error: $errorMsg), attempting to find alternative...")
                val failedModel = activeModelName
                if (tryToSelectBestModel(excludeName = failedModel)) {
                    try {
                        val result = performGeneration(token, prompt)
                        lastSuccess = true
                        return@withContext result
                    } catch (retryEx: Exception) {
                        lastSuccess = false
                        Log.e(TAG, "Retry with fallback model $activeModelName failed: ${retryEx.message}")
                    }
                }
            }
            Log.e(TAG, "Gemini call failed: ${e.message}", e)
            throw e
        }
    }

    private suspend fun tryToSelectBestModel(excludeName: String? = null): Boolean {
        try {
            val modelsJson = listModels()
            val modelsRoot = JSONObject(modelsJson)
            val modelsArray = modelsRoot.getJSONArray("models")
            val candidates = mutableListOf<String>()
            
            for (i in 0 until modelsArray.length()) {
                val model = modelsArray.getJSONObject(i)
                val name = model.getString("name").removePrefix("models/")
                if (name == excludeName) continue
                
                val methods = model.getJSONArray("supportedGenerationMethods")
                var canGenerate = false
                for (j in 0 until methods.length()) {
                    if (methods.getString(j) == "generateContent") canGenerate = true
                }
                
                if (canGenerate && !name.contains("vision") && !name.contains("embedding") && !name.contains("aqa")) {
                    candidates.add(name)
                }
            }
            
            val bestModel = candidates
                .filter { it.contains("flash") }
                .sortedDescending()
                .firstOrNull() 
                ?: candidates.sortedDescending().firstOrNull()

            if (bestModel != null && bestModel != activeModelName) {
                Log.i(TAG, "Selected model: $bestModel (failed/prev was $activeModelName)")
                activeModelName = bestModel
                return true
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to find best model", ex)
        }
        return false
    }

    private suspend fun performGeneration(token: String, prompt: String): String {
        var currentJson = createInitialRequest(prompt)
        var responseJson: String
        
        for (_turn in 1..5) { // Increased turns for more tool interaction
            responseJson = callGeminiRest(token, currentJson)
            val root = JSONObject(responseJson)
            val candidate = root.getJSONArray("candidates").getJSONObject(0)
            val content = candidate.getJSONObject("content")
            val parts = content.getJSONArray("parts")
            
            var hasFunctionCall = false
            val functionResponses = mutableListOf<JSONObject>()
            
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (part.has("functionCall")) {
                    hasFunctionCall = true
                    val call = part.getJSONObject("functionCall")
                    val result = handleFunctionCall(token, call)
                    
                    functionResponses.add(JSONObject().apply {
                        put("functionResponse", JSONObject().apply {
                            put("name", call.getString("name"))
                            put("response", JSONObject().apply {
                                put("content", result)
                            })
                        })
                    })
                }
            }
            
            if (hasFunctionCall) {
                currentJson = createFunctionResponseRequest(content, functionResponses)
            } else {
                // Check if it's text or grounded search result
                return if (parts.getJSONObject(0).has("text")) {
                    parts.getJSONObject(0).getString("text")
                } else {
                    "Keine Antwort erhalten."
                }
            }
        }
        return "Fehler: Zu viele Interaktionsschritte."
    }

    suspend fun listModels(): String = withContext(Dispatchers.IO) {
        val token = oauthTokenProvider() ?: return@withContext "Fehler: Kein Token."
        val url = URL(LIST_MODELS_URL)
        val connection = url.openConnection() as HttpsURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        
        if (connection.responseCode == 200) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
            "Fehler beim Auflisten der Modelle (${connection.responseCode}): $error"
        }
    }

    private fun callGeminiRest(token: String, requestJson: JSONObject): String {
        val url = URL(BASE_URL_TEMPLATE.format(activeModelName))
        val connection = url.openConnection() as HttpsURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true

        connection.outputStream.use { it.write(requestJson.toString().toByteArray()) }

        return if (connection.responseCode == 200) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
            throw Exception("HTTP ${connection.responseCode}: $error")
        }
    }

    private fun createInitialRequest(prompt: String): JSONObject {
        return JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().apply {
                    put("text", prompt)
                }))
            }))
            val toolsArray = JSONArray()
            // NOTE: Built-in tools (google_search) and custom functions cannot be combined as of now.
            // Prioritizing custom functions for GhostTalk.
            // 2. Custom Functions
            toolsArray.put(JSONObject().apply {
                put("function_declarations", JSONArray().apply {
                    put(JSONObject().apply {
                        put("name", "search_drive")
                        put("description", "Sucht Dateien in Google Drive.")
                        put("parameters", JSONObject().apply {
                            put("type", "OBJECT")
                            put("properties", JSONObject().apply {
                                put("query", JSONObject().apply {
                                    put("type", "STRING")
                                    put("description", "Suchbegriff")
                                })
                            })
                            put("required", JSONArray().put("query"))
                        })
                    })
                    put(JSONObject().apply {
                        put("name", "get_weather")
                        put("description", "Holt aktuelle Wetterdaten für einen Ort.")
                        put("parameters", JSONObject().apply {
                            put("type", "OBJECT")
                            put("properties", JSONObject().apply {
                                put("location", JSONObject().apply { 
                                    put("type", "STRING") 
                                    put("description", "Der Name der Stadt oder des Ortes.")
                                })
                            })
                            put("required", JSONArray().put("location"))
                        })
                    })
                    put(JSONObject().apply {
                        put("name", "wikipedia_search")
                        put("description", "Sucht eine Zusammenfassung zu einem Thema auf Wikipedia.")
                        put("parameters", JSONObject().apply {
                            put("type", "OBJECT")
                            put("properties", JSONObject().apply {
                                put("topic", JSONObject().apply { put("type", "STRING") })
                            })
                            put("required", JSONArray().put("topic"))
                        })
                    })
                    put(JSONObject().apply {
                        put("name", "list_calendar_events")
                        put("description", "Listet die nächsten Termine aus dem Google Kalender auf.")
                        put("parameters", JSONObject().apply {
                            put("type", "OBJECT")
                            put("properties", JSONObject())
                        })
                    })
                    put(JSONObject().apply {
                        put("name", "list_tasks")
                        put("description", "Listet offene Aufgaben aus Google Tasks auf.")
                        put("parameters", JSONObject().apply {
                            put("type", "OBJECT")
                            put("properties", JSONObject())
                        })
                    })
                    put(JSONObject().apply {
                        put("name", "play_on_spotify")
                        put("description", "Spielt ein Lied, Album oder Künstler auf Spotify ab.")
                        put("parameters", JSONObject().apply {
                            put("type", "OBJECT")
                            put("properties", JSONObject().apply {
                                put("query", JSONObject().apply { 
                                    put("type", "STRING")
                                    put("description", "Titel, Künstler oder Playlist.")
                                })
                            })
                            put("required", JSONArray().put("query"))
                        })
                    })
                })
            })
            put("tools", toolsArray)
        }
    }

    private fun createFunctionResponseRequest(previousContent: JSONObject, responses: List<JSONObject>): JSONObject {
        return JSONObject().apply {
            val contents = JSONArray()
            contents.put(previousContent)
            contents.put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().apply {
                    responses.forEach { put(it) }
                })
            })
            put("contents", contents)
            // Tools are not strictly required for follow-up in some v1beta versions but good to have
        }
    }

    private suspend fun handleFunctionCall(token: String, call: JSONObject): String {
        val name = call.getString("name")
        val args = call.optJSONObject("args")
        Log.d(TAG, "Executing tool: $name with args: $args")
        
        return try {
            when (name) {
                "search_drive" -> executeDriveSearch(args?.optString("query") ?: "")
                "get_weather" -> executeWeatherFetch(args?.optString("location") ?: "Berlin")
                "wikipedia_search" -> executeWikipediaSearch(args?.optString("topic") ?: "")
                "list_calendar_events" -> executeCalendarFetch(token)
                "list_tasks" -> executeTasksFetch(token)
                "play_on_spotify" -> {
                    val q = args?.optString("query") ?: ""
                    appCommandHandler?.invoke("SPOTIFY_PLAY", mapOf("query" to q))
                    "Spotify wurde mit der Suche '$q' gestartet."
                }
                "control_home" -> executeHomeControl(args?.optString("device") ?: "", args?.optString("action") ?: "")
                else -> "Funktion nicht gefunden."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Tool $name execution failed", e)
            "[Fehler im Tool $name: ${e.message}. Fahre fort, falls möglich.]"
        }
    }

    private suspend fun executeDriveSearch(query: String): String {
        return try {
            val drive = driveProvider() ?: return "Fehler: Drive nicht verfügbar."
            val helper = DriveServiceHelper(drive)
            val files = if (query.isEmpty()) {
                helper.listFiles("root")
            } else {
                helper.searchFiles(query)
            }
            if (files.isEmpty()) return "Keine Dateien gefunden."
            "Treffer in Drive: " + files.take(3).joinToString { it.name }
        } catch (e: Exception) {
            "Fehler bei Drive Suche: ${e.message}"
        }
    }

    private fun executeHomeControl(device: String, action: String): String {
        Log.d(TAG, "Home Control: $device -> $action")
        return "Erfolg: $device wurde auf '$action' gesetzt."
    }

    private suspend fun executeWeatherFetch(location: String): String = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://wttr.in/${java.net.URLEncoder.encode(location, "UTF-8")}?format=3")
            val connection = url.openConnection() as HttpsURLConnection
            connection.requestMethod = "GET"
            connection.readTimeout = 5000
            connection.connectTimeout = 5000
            
            if (connection.responseCode == 200) {
                connection.inputStream.bufferedReader().use { it.readText().trim() }
            } else {
                "Fehler beim Wetter-Abruf für $location."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Weather fetch failed", e)
            "Wetter-Dienst aktuell nicht erreichbar."
        }
    }

    private suspend fun executeWikipediaSearch(topic: String): String = withContext(Dispatchers.IO) {
        try {
            val encodedTopic = java.net.URLEncoder.encode(topic.replace(" ", "_"), "UTF-8")
            val url = URL("https://de.wikipedia.org/api/rest_v1/page/summary/$encodedTopic")
            val connection = url.openConnection() as HttpsURLConnection
            connection.requestMethod = "GET"
            
            if (connection.responseCode == 200) {
                val json = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(json)
                root.optString("extract", "Keine Zusammenfassung gefunden.")
            } else {
                "Wikipedia-Artikel zu '$topic' nicht gefunden."
            }
        } catch (e: Exception) {
            "Fehler bei Wikipedia-Suche: ${e.message}"
        }
    }

    private suspend fun executeCalendarFetch(token: String): String = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://www.googleapis.com/calendar/v3/calendars/primary/events?maxResults=5&orderBy=startTime&singleEvents=true&timeMin=" + 
                java.net.URLEncoder.encode(java.time.OffsetDateTime.now().toString(), "UTF-8"))
            val connection = url.openConnection() as HttpsURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")
            
            if (connection.responseCode == 200) {
                val json = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(json)
                val items = root.optJSONArray("items")
                if (items == null || items.length() == 0) return@withContext "Keine anstehenden Termine gefunden."
                
                val builder = StringBuilder("Anstehende Termine:\n")
                for (i in 0 until items.length()) {
                    val event = items.getJSONObject(i)
                    val summary = event.optString("summary", "(Kein Titel)")
                    val start = event.optJSONObject("start")?.optString("dateTime") ?: event.optJSONObject("start")?.optString("date") ?: ""
                    builder.append("- $summary am $start\n")
                }
                builder.toString()
            } else {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                "Fehler beim Kalender-Zugriff ($error)."
            }
        } catch (e: Exception) {
            "Kalender-Fehler: ${e.message}"
        }
    }

    private suspend fun executeTasksFetch(token: String): String = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://www.googleapis.com/tasks/v1/lists/@default/tasks?maxResults=5")
            val connection = url.openConnection() as HttpsURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")
            
            if (connection.responseCode == 200) {
                val json = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(json)
                val items = root.optJSONArray("items")
                if (items == null || items.length() == 0) return@withContext "Keine offenen Aufgaben gefunden."
                
                val builder = StringBuilder("Offene Aufgaben:\n")
                for (i in 0 until items.length()) {
                    val task = items.getJSONObject(i)
                    val title = task.optString("title", "(Kein Titel)")
                    builder.append("- $title\n")
                }
                builder.toString()
            } else {
                "Fehler beim Aufgaben-Abruf."
            }
        } catch (e: Exception) {
            "Tasks-Fehler: ${e.message}"
        }
    }

    fun getToolStatus(isUserSignedIn: Boolean): Map<String, ToolStatus> {
        val statusMap = mutableMapOf<String, ToolStatus>()
        
        val baseStatus = when (lastSuccess) {
            true -> ToolStatus.AVAILABLE
            false -> ToolStatus.FAILED
            null -> ToolStatus.PENDING
        }

        // Static tools
        statusMap["wikipedia_search"] = baseStatus
        statusMap["get_weather"] = baseStatus
        statusMap["play_on_spotify"] = baseStatus
        
        // Auth-dependent tools
        val authStatus = if (!isUserSignedIn) {
            ToolStatus.REQUIRES_AUTH
        } else {
            baseStatus
        }
        
        statusMap["search_drive"] = authStatus
        statusMap["list_calendar_events"] = authStatus
        statusMap["list_tasks"] = authStatus
        
        return statusMap
    }
}

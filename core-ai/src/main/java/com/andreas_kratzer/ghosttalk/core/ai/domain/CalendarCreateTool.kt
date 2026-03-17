package com.andreas_kratzer.ghosttalk.core.ai.domain

import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import com.andreas_kratzer.ghosttalk.core.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import javax.inject.Inject
import javax.net.ssl.HttpsURLConnection

class CalendarCreateTool @Inject constructor(
    private val googleAuthManager: GoogleAuthManager,
    private val logger: Logger
) : AiTool {
    override val name: String = "create_calendar_event"
    override val requiresAuth: Boolean = true
    override val description: String = "Erstellt einen neuen Termin im Google Kalender."
    override val parameters: JSONObject = JSONObject().apply {
        put("type", "OBJECT")
        put("properties", JSONObject().apply {
            put("summary", JSONObject().apply {
                put("type", "STRING")
                put("description", "Titel des Termins.")
            })
            put("startTime", JSONObject().apply {
                put("type", "STRING")
                put("description", "Startzeitpunkt im ISO-Format (z.B. 2024-03-20T10:00:00Z).")
            })
            put("endTime", JSONObject().apply {
                put("type", "STRING")
                put("description", "Endzeitpunkt im ISO-Format.")
            })
        })
        put("required", JSONArray().put("summary").put("startTime").put("endTime"))
    }

    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        val token = googleAuthManager.getGoogleCredential()?.getToken() ?: return@withContext "Fehler: Nicht angemeldet (OAuth Token fehlt)."
        val summary = args["summary"] as? String ?: return@withContext "Fehler: Kein Titel angegeben."
        val startTime = args["startTime"] as? String ?: return@withContext "Fehler: Keine Startzeit angegeben."
        val endTime = args["endTime"] as? String ?: return@withContext "Fehler: Keine Endzeit angegeben."

        try {
            val eventJson = JSONObject().apply {
                put("summary", summary)
                put("start", JSONObject().put("dateTime", startTime))
                put("end", JSONObject().put("dateTime", endTime))
            }

            val url = URL("https://www.googleapis.com/calendar/v3/calendars/primary/events")
            val connection = url.openConnection() as HttpsURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            
            connection.outputStream.use { it.write(eventJson.toString().toByteArray()) }
            
            if (connection.responseCode == 200 || connection.responseCode == 201) {
                "Termin '$summary' wurde erfolgreich für $startTime erstellt."
            } else {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                "Fehler beim Erstellen des Termins ($error)."
            }
        } catch (e: Exception) {
            logger.e("CalendarCreateTool", "Calendar event creation failed", e)
            "Kalender-Fehler: ${e.message}"
        }
    }
}

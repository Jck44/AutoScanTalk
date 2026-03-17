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

class CalendarListTool @Inject constructor(
    private val googleAuthManager: GoogleAuthManager,
    private val logger: Logger
) : AiTool {
    override val name: String = "list_calendar_events"
    override val requiresAuth: Boolean = true
    override val description: String = "Listet die nächsten Termine aus dem Google Kalender auf."
    override val parameters: JSONObject = JSONObject().apply {
        put("type", "OBJECT")
        put("properties", JSONObject())
    }

    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        val token = googleAuthManager.getGoogleCredential()?.getToken() ?: return@withContext "Fehler: Nicht angemeldet (OAuth Token fehlt)."
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
            logger.e("CalendarListTool", "Calendar fetch failed", e)
            "Kalender-Fehler: ${e.message}"
        }
    }
}

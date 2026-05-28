package com.andreas_kratzer.ghosttalk.core.ai.domain

import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import com.andreas_kratzer.ghosttalk.core.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.inject.Inject
import javax.net.ssl.HttpsURLConnection

class TasksListTool @Inject constructor(
    private val googleAuthManager: GoogleAuthManager,
    private val logger: Logger
) : AiTool {
    override val name: String = "list_tasks"
    override val requiresAuth: Boolean = true
    override val description: String = "Listet die Aufgaben aus Google Tasks auf."
    override val parameters: JSONObject = JSONObject().apply {
        put("type", "OBJECT")
        put("properties", JSONObject())
    }

    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        val token = googleAuthManager.getGoogleCredential()?.getToken() ?: return@withContext "Fehler: Nicht angemeldet (OAuth Token fehlt)."
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
            logger.e("TasksListTool", "Tasks fetch failed", e)
            "Tasks-Fehler: ${e.message}"
        }
    }
}

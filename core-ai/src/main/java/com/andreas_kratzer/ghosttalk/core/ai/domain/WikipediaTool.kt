package com.andreas_kratzer.ghosttalk.core.ai.domain

import com.andreas_kratzer.ghosttalk.core.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import javax.inject.Inject
import javax.net.ssl.HttpsURLConnection

class WikipediaTool @Inject constructor(
    private val logger: Logger
) : AiTool {
    override val name: String = "wikipedia_search"
    override val requiresAuth: Boolean = false
    override val description: String = "Sucht eine Zusammenfassung zu einem Thema auf Wikipedia."
    override val parameters: JSONObject = JSONObject().apply {
        put("type", "OBJECT")
        put("properties", JSONObject().apply {
            put("topic", JSONObject().apply { 
                put("type", "STRING") 
                put("description", "Das Thema nach dem gesucht werden soll.")
            })
        })
        put("required", JSONArray().put("topic"))
    }

    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        val topic = args["topic"] as? String ?: return@withContext "Fehler: Kein Thema angegeben."
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
            logger.e("WikipediaTool", "Wikipedia search failed", e)
            "Fehler bei Wikipedia-Suche: ${e.message}"
        }
    }
}

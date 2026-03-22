package com.andreas_kratzer.ghosttalk.core.ai.domain

import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import com.andreas_kratzer.ghosttalk.core.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.inject.Inject
import javax.net.ssl.HttpsURLConnection

class GmailReadTool @Inject constructor(
    private val googleAuthManager: GoogleAuthManager,
    private val logger: Logger
) : AiTool {
    override val name: String = "read_gmail"
    override val requiresAuth: Boolean = true
    override val description: String = "Liest Zusammenfassungen der neuesten E-Mails aus dem Posteingang."
    override val parameters: JSONObject = JSONObject().apply {
        put("type", "OBJECT")
        put("properties", JSONObject().apply {
            put("maxResults", JSONObject().apply {
                put("type", "INTEGER")
                put("description", "Anzahl der zu lesenden E-Mails (Standard 5).")
            })
        })
    }

    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        val token = googleAuthManager.getGoogleCredential()?.getToken() ?: return@withContext "Fehler: Nicht angemeldet (OAuth Token fehlt)."
        val maxResults = (args["maxResults"] as? Number)?.toInt() ?: 5
        
        try {
            // 1. List messages
            val listUrl = URL("https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=$maxResults&q=label:INBOX")
            val listConn = listUrl.openConnection() as HttpsURLConnection
            listConn.requestMethod = "GET"
            listConn.setRequestProperty("Authorization", "Bearer $token")
            
            if (listConn.responseCode != 200) {
                return@withContext "Fehler beim Abruf der E-Mail Liste (${listConn.responseCode})."
            }
            
            val listJson = listConn.inputStream.bufferedReader().use { it.readText() }
            val listRoot = JSONObject(listJson)
            val messages = listRoot.optJSONArray("messages")
            if (messages == null || messages.length() == 0) return@withContext "Keine neuen E-Mails gefunden."
            
            val builder = StringBuilder("Deine neuesten E-Mails:\n")
            
            // 2. Fetch each message summary
            for (i in 0 until messages.length()) {
                val msgId = messages.getJSONObject(i).getString("id")
                val msgUrl = URL("https://gmail.googleapis.com/gmail/v1/users/me/messages/$msgId?format=minimal")
                val msgConn = msgUrl.openConnection() as HttpsURLConnection
                msgConn.requestMethod = "GET"
                msgConn.setRequestProperty("Authorization", "Bearer $token")
                
                if (msgConn.responseCode == 200) {
                    val msgJson = msgConn.inputStream.bufferedReader().use { it.readText() }
                    val msgRoot = JSONObject(msgJson)
                    val snippet = msgRoot.optString("snippet", "(Kein Inhalt)")
                    builder.append("- $snippet\n")
                }
            }
            builder.toString()
        } catch (e: Exception) {
            logger.e("GmailReadTool", "Gmail fetch failed", e)
            "Gmail-Fehler: ${e.message}"
        }
    }
}

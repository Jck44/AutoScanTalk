package com.andreas_kratzer.ghosttalk.core.ai.domain

import com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper
import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class DriveSearchTool @Inject constructor(
    private val googleAuthManager: GoogleAuthManager
) : AiTool {
    override val name: String = "search_drive"
    override val requiresAuth: Boolean = true
    override val description: String = "Sucht in den Google Drive Dateien des Nutzers nach Inhalten."
    override val parameters: JSONObject = JSONObject().apply {
        put("type", "OBJECT")
        put("properties", JSONObject().apply {
            put("query", JSONObject().apply {
                put("type", "STRING")
                put("description", "Suchbegriff")
            })
        })
        put("required", JSONArray().put("query"))
    }

    private suspend fun getDrive(): Drive? {
        val credential = googleAuthManager.getGoogleCredential()
        return if (credential == null) {
            null
        } else {
            Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance()
            ) { request ->
                credential.initialize(request)
                request.connectTimeout = 3 * 60 * 1000 // 3 minutes
                request.readTimeout = 3 * 60 * 1000    // 3 minutes
            }.setApplicationName("GhosTTalk").build()
        }
    }

    override suspend fun execute(args: Map<String, Any?>): String {
        val query = args["query"] as? String ?: ""
        return try {
            val drive = getDrive() ?: return "Fehler: Drive nicht verfügbar (Anmeldung erforderlich)."
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
}

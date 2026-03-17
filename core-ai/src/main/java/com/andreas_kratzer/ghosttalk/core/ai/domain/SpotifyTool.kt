package com.andreas_kratzer.ghosttalk.core.ai.domain

import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class SpotifyTool @Inject constructor() : AiTool {
    override val name: String = "play_on_spotify"
    override val requiresAuth: Boolean = true
    override val description: String = "Spielt ein Lied, Album oder Künstler auf Spotify ab."
    override val parameters: JSONObject = JSONObject().apply {
        put("type", "OBJECT")
        put("properties", JSONObject().apply {
            put("query", JSONObject().apply { 
                put("type", "STRING")
                put("description", "Titel, Künstler oder Playlist.")
            })
        })
        put("required", JSONArray().put("query"))
    }

    private var appCommandHandler: ((String, Map<String, String>) -> Unit)? = null

    fun setAppCommandHandler(handler: (String, Map<String, String>) -> Unit) {
        this.appCommandHandler = handler
    }

    override suspend fun execute(args: Map<String, Any?>): String {
        val q = args["query"] as? String ?: ""
        if (q.isEmpty()) return "Fehler: Keine Suchanfrage für Spotify angegeben."
        
        appCommandHandler?.invoke("SPOTIFY_PLAY", mapOf("query" to q)) ?: return "Spotify konnte nicht gestartet werden (Kommando-Handler fehlt)."
        
        return "Spotify wurde mit der Suche '$q' gestartet."
    }
}

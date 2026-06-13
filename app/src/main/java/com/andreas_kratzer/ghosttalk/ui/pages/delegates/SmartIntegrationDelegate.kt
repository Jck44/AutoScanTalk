package com.andreas_kratzer.ghosttalk.ui.pages.delegates

import android.app.Application
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.ai.domain.GeminiUseCase
import com.andreas_kratzer.ghosttalk.core.cloud.PhilipsHueManager
import com.andreas_kratzer.ghosttalk.core.cloud.SpotifyManager
import com.andreas_kratzer.ghosttalk.core.cloud.SpotifyPlaylist
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartIntegrationDelegate @Inject constructor(
    private val application: Application,
    private val settingsRepository: SettingsRepository,
    private val spotifyManager: SpotifyManager,
    val philipsHueManager: PhilipsHueManager,
    private val geminiUseCase: GeminiUseCase
) {
    private lateinit var scope: CoroutineScope

    val spotifyUserDisplayName = settingsRepository.spotifyUserDisplayNameFlow

    private val _spotifyPlaylists = MutableStateFlow<List<SpotifyPlaylist>>(emptyList())
    val spotifyPlaylists: StateFlow<List<SpotifyPlaylist>> = _spotifyPlaylists.asStateFlow()

    private val _isLoadingPlaylists = MutableStateFlow(false)
    val isLoadingPlaylists: StateFlow<Boolean> = _isLoadingPlaylists.asStateFlow()

    fun init(coroutineScope: CoroutineScope) {
        this.scope = coroutineScope

        geminiUseCase.setAppCommandHandler { command, args ->
            when (command) {
                "SPOTIFY_PLAY" -> {
                    val query = args["query"] ?: return@setAppCommandHandler
                    scope.launch {
                        try {
                            val intent = Intent(android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                                putExtra("android.intent.extra.focus", "vnd.android.cursor.item/*")
                                putExtra("query", query)
                                putExtra(android.app.SearchManager.QUERY, query)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            application.startActivity(intent)
                        } catch (e: Exception) {
                            Log.e("SmartIntegrationDelegate", "Failed to launch Spotify", e)
                        }
                    }
                }
            }
        }
    }

    fun connectSpotify(context: android.content.Context) {
        val authUrl = spotifyManager.getAuthorizationUrl()
        val intent = Intent(Intent.ACTION_VIEW, authUrl.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun disconnectSpotify() {
        spotifyManager.disconnect()
        _spotifyPlaylists.value = emptyList()
    }

    fun loadSpotifyPlaylists() {
        scope.launch {
            if (settingsRepository.spotifyAccessToken.isNullOrBlank()) {
                _spotifyPlaylists.value = emptyList()
                return@launch
            }
            _isLoadingPlaylists.value = true
            try {
                val playlists = spotifyManager.getPlaylists()
                _spotifyPlaylists.value = playlists
            } catch (e: Exception) {
                Log.e("SmartIntegrationDelegate", "Failed to load Spotify playlists", e)
            } finally {
                _isLoadingPlaylists.value = false
            }
        }
    }

    fun refreshHueDevicesCache(silentOnFailure: Boolean = false, onResult: ((Boolean) -> Unit)? = null) {
        val ip = settingsRepository.hueBridgeIp
        val username = settingsRepository.hueUsername
        if (ip.isBlank() || username.isBlank()) {
            if (!silentOnFailure) {
                Toast.makeText(application, application.getString(R.string.smart_integration_please_pair), Toast.LENGTH_LONG).show()
            }
            onResult?.invoke(false)
            return
        }
        scope.launch {
            val fetchedDevices = philipsHueManager.getLocalLights(ip, username)
            if (fetchedDevices.isNotEmpty()) {
                val array = org.json.JSONArray()
                fetchedDevices.forEach { device ->
                    val obj = org.json.JSONObject().apply {
                        put("id", device.id)
                        put("name", device.name)
                        put("type", device.type)
                    }
                    array.put(obj)
                }
                settingsRepository.hueCachedDevices = array.toString()
                Toast.makeText(application, "${fetchedDevices.size} Lampen geladen und im Cache gespeichert.", Toast.LENGTH_LONG).show()
                onResult?.invoke(true)
            } else {
                if (!silentOnFailure) {
                    Toast.makeText(application, "Konnte Bridge nicht erreichen. Alter Cache wird beibehalten.", Toast.LENGTH_LONG).show()
                }
                onResult?.invoke(false)
            }
        }
    }
}

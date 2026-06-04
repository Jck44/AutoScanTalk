package com.andreas_kratzer.ghosttalk.core.cloud

import android.net.Uri
import android.util.Log
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpotifyManager @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    private var pendingCodeVerifier: String? = null

    companion object {
        private const val TAG = "SpotifyManager"
        const val CLIENT_ID = "31732aa75fdf4a1bbfb338734c16e68c"
        const val REDIRECT_URI = "com.andreas.kratzer.ghosttalk://spotify-callback"
    }

    fun getAuthorizationUrl(): String {
        val verifier = PkceGenerator.generateCodeVerifier()
        pendingCodeVerifier = verifier
        val challenge = PkceGenerator.generateCodeChallenge(verifier)
        
        val url = Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("scope", "playlist-read-private playlist-read-collaborative user-library-read")
            .build().toString()
        
        Log.d(TAG, "Generated Authorization URL: $url")
        return url
    }

    suspend fun handleAuthRedirect(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "Handling auth redirect: $uri")
        val code = uri.getQueryParameter("code") ?: return@withContext false
        val verifier = pendingCodeVerifier ?: return@withContext false
        
        try {
            val url = URL("https://accounts.spotify.com/api/token")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.doOutput = true
            
            val body = "client_id=$CLIENT_ID" +
                    "&grant_type=authorization_code" +
                    "&code=$code" +
                    "&redirect_uri=${URLEncoder.encode(REDIRECT_URI, "UTF-8")}" +
                    "&code_verifier=$verifier"
            
            connection.outputStream.use { it.write(body.toByteArray()) }
            
            val responseCode = connection.responseCode
            Log.d(TAG, "Token exchange response code: $responseCode")
            
            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val accessToken = json.getString("access_token")
                val refreshToken = json.getString("refresh_token")
                val expiresIn = json.getLong("expires_in")
                
                settingsRepository.spotifyAccessToken = accessToken
                settingsRepository.spotifyRefreshToken = refreshToken
                settingsRepository.spotifyTokenExpiresAt = System.currentTimeMillis() + (expiresIn * 1000)
                
                fetchAndStoreUserDisplayName(accessToken)
                
                Log.i(TAG, "Spotify OAuth authentication successful.")
                return@withContext true
            } else {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.e(TAG, "Failed to exchange token: $error")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during token exchange", e)
        }
        return@withContext false
    }

    private suspend fun fetchAndStoreUserDisplayName(token: String) {
        try {
            val url = URL("https://api.spotify.com/v1/me")
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("Authorization", "Bearer $token")
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val displayName = json.optString("display_name", "")
                settingsRepository.spotifyUserDisplayName = displayName
                Log.d(TAG, "Stored Spotify user display name: $displayName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch user profile info", e)
        }
    }

    suspend fun getOrRefreshToken(): String? = withContext(Dispatchers.IO) {
        val expiresAt = settingsRepository.spotifyTokenExpiresAt
        val currentToken = settingsRepository.spotifyAccessToken
        val refreshToken = settingsRepository.spotifyRefreshToken
        
        if (currentToken.isNullOrBlank() || refreshToken.isNullOrBlank()) {
            Log.w(TAG, "getOrRefreshToken: No tokens stored.")
            return@withContext null
        }
        
        if (System.currentTimeMillis() < expiresAt - 60000) {
            return@withContext currentToken
        }
        
        Log.d(TAG, "Access token expired. Refreshing...")
        try {
            val url = URL("https://accounts.spotify.com/api/token")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.doOutput = true
            
            val body = "client_id=$CLIENT_ID" +
                    "&grant_type=refresh_token" +
                    "&refresh_token=$refreshToken"
            
            connection.outputStream.use { it.write(body.toByteArray()) }
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val accessToken = json.getString("access_token")
                val newRefreshToken = json.optString("refresh_token", refreshToken)
                val expiresIn = json.getLong("expires_in")
                
                settingsRepository.spotifyAccessToken = accessToken
                settingsRepository.spotifyRefreshToken = newRefreshToken
                settingsRepository.spotifyTokenExpiresAt = System.currentTimeMillis() + (expiresIn * 1000)
                
                Log.d(TAG, "Spotify token refreshed successfully.")
                return@withContext accessToken
            } else {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.e(TAG, "Token refresh failed: $error")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during token refresh", e)
        }
        return@withContext null
    }

    suspend fun getPlaylists(): List<SpotifyPlaylist> = withContext(Dispatchers.IO) {
        val token = getOrRefreshToken() ?: return@withContext emptyList()
        val list = mutableListOf<SpotifyPlaylist>()
        // NOTE: Liked Songs / Lieblingssongs temporarily disabled because
        // spotify:collection:tracks does not support the :play suffix,
        // so auto-play via intent does not work reliably.
        // Re-enable once Spotify App Remote SDK is integrated.
        try {
            var nextUrl: String? = "https://api.spotify.com/v1/me/playlists?limit=50"
            while (nextUrl != null) {
                val url = URL(nextUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("Authorization", "Bearer $token")
                
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val items = json.getJSONArray("items")
                    for (i in 0 until items.length()) {
                        val item = items.getJSONObject(i)
                        val name = item.getString("name")
                        val uri = item.getString("uri")
                        val id = item.getString("id")
                        val images = item.optJSONArray("images")
                        val imageUrl = if (images != null && images.length() > 0) {
                            images.getJSONObject(0).getString("url")
                        } else ""
                        list.add(SpotifyPlaylist(id = id, name = name, uri = uri, imageUrl = imageUrl))
                    }
                    nextUrl = if (json.isNull("next")) null else json.optString("next", null)
                } else {
                    Log.e(TAG, "Failed to load playlists, response: ${connection.responseCode}")
                    nextUrl = null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception loading playlists", e)
        }
        return@withContext list
    }

    fun disconnect() {
        settingsRepository.spotifyAccessToken = null
        settingsRepository.spotifyRefreshToken = null
        settingsRepository.spotifyTokenExpiresAt = 0
        settingsRepository.spotifyUserDisplayName = null
        Log.i(TAG, "Disconnected Spotify account.")
    }
}

data class SpotifyPlaylist(
    val id: String,
    val name: String,
    val uri: String,
    val imageUrl: String
)

package com.andreas_kratzer.ghosttalk.core.cloud

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
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
class GoogleWebAuthManager @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    private var pendingCodeVerifier: String? = null

    companion object {
        private const val TAG = "GoogleWebAuthManager"
        val CLIENT_ID = BuildConfig.GOOGLE_CLIENT_ID
        const val REDIRECT_URI = "com.googleusercontent.apps.974414517482-2fo3sfu8ij49gotcduivt6dsu9e7cleu:/oauth2redirect"
    }

    fun startWebAuthFlow(context: Context) {
        val verifier = PkceGenerator.generateCodeVerifier()
        pendingCodeVerifier = verifier
        val challenge = PkceGenerator.generateCodeChallenge(verifier)

        val url = Uri.parse("https://accounts.google.com/o/oauth2/v2/auth").buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", "https://www.googleapis.com/auth/drive.file email openid")
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("prompt", "consent")
            .build().toString()

        Log.d(TAG, "Starting Web Auth Flow with URL: $url")
        val customTabsIntent = CustomTabsIntent.Builder().build()
        customTabsIntent.launchUrl(context, Uri.parse(url))
    }

    suspend fun handleAuthRedirect(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "Handling auth redirect: $uri")
        val code = uri.getQueryParameter("code") ?: return@withContext false
        val verifier = pendingCodeVerifier ?: return@withContext false

        try {
            val url = URL("https://oauth2.googleapis.com/token")
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
                val refreshToken = json.optString("refresh_token", "")
                val expiresIn = json.getLong("expires_in")

                settingsRepository.googleAccessToken = accessToken
                if (refreshToken.isNotEmpty()) {
                    settingsRepository.googleRefreshToken = refreshToken
                }
                settingsRepository.googleTokenExpiresAt = System.currentTimeMillis() + (expiresIn * 1000)

                fetchAndStoreUserEmail(accessToken)

                Log.i(TAG, "Google Web Flow authentication successful.")
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

    private suspend fun fetchAndStoreUserEmail(token: String) {
        try {
            val url = URL("https://www.googleapis.com/oauth2/v3/userinfo")
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("Authorization", "Bearer $token")

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val email = json.optString("email", "")
                settingsRepository.googleUserEmail = email
                Log.d(TAG, "Stored Google user email: $email")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch user email info", e)
        }
    }

    suspend fun getOrRefreshToken(): String? = withContext(Dispatchers.IO) {
        val expiresAt = settingsRepository.googleTokenExpiresAt
        val currentToken = settingsRepository.googleAccessToken
        val refreshToken = settingsRepository.googleRefreshToken

        if (currentToken.isNullOrBlank()) {
            Log.w(TAG, "getOrRefreshToken: No access token stored.")
            return@withContext null
        }

        if (System.currentTimeMillis() < expiresAt - 60000) {
            return@withContext currentToken
        }

        if (refreshToken.isNullOrBlank()) {
            Log.w(TAG, "getOrRefreshToken: Access token expired and no refresh token available.")
            return@withContext null
        }

        Log.d(TAG, "Access token expired. Refreshing...")
        try {
            val url = URL("https://oauth2.googleapis.com/token")
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

                settingsRepository.googleAccessToken = accessToken
                settingsRepository.googleRefreshToken = newRefreshToken
                settingsRepository.googleTokenExpiresAt = System.currentTimeMillis() + (expiresIn * 1000)

                Log.d(TAG, "Google token refreshed successfully.")
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

    fun disconnect() {
        settingsRepository.googleAccessToken = null
        settingsRepository.googleRefreshToken = null
        settingsRepository.googleTokenExpiresAt = 0
        settingsRepository.googleUserEmail = null
        Log.i(TAG, "Disconnected Google Web Flow account.")
    }
}

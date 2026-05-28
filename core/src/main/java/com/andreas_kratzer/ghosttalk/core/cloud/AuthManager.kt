package com.andreas_kratzer.ghosttalk.core.cloud

import kotlinx.coroutines.flow.StateFlow

interface AuthManager {
    val userEmail: StateFlow<String?>
    /**
     * Returns a credential with the specified scopes.
     * If scopes is null, returns a credential with default basic scopes (Drive, Gemini).
     */
    fun getGoogleCredential(scopes: List<String>? = null): com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential?
    suspend fun signIn(activity: android.app.Activity): Boolean
    suspend fun signOut()

    /**
     * Saves the ElevenLabs API Key to the Google Password Manager.
     */
    suspend fun saveApiKeyToPasswordManager(activity: android.app.Activity, apiKey: String): Result<Unit>

    /**
     * Retrieves the ElevenLabs API Key from the Google Password Manager.
     */
    suspend fun getApiKeyFromPasswordManager(activity: android.app.Activity): Result<String?>
}

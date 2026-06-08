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
     * Saves the API Key to the Google Password Manager.
     */
    suspend fun saveApiKeyToPasswordManager(activity: android.app.Activity, apiKey: String, serviceName: String = "elevenlabs"): Result<Unit>

    /**
     * Retrieves the API Key from the Google Password Manager.
     */
    suspend fun getApiKeyFromPasswordManager(activity: android.app.Activity, serviceName: String = "elevenlabs"): Result<String?>

    /**
     * Retrieves the username and password/API key pair from the Google Password Manager.
     */
    suspend fun getCredentialFromPasswordManager(activity: android.app.Activity): Result<Pair<String, String>?>
}

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
}

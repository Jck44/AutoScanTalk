package com.andreas_kratzer.ghosttalk.core.cloud

import kotlinx.coroutines.flow.StateFlow

interface AuthManager {
    val userEmail: StateFlow<String?>
    fun getGoogleCredential(): com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential?
    suspend fun signIn(activity: android.app.Activity): Boolean
    suspend fun signOut()
}

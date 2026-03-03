package com.andreas_kratzer.ghosttalk.core.cloud

import android.util.Log
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.core.content.edit
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.drive.DriveScopes
import java.util.Collections

@javax.inject.Singleton
class DriveAuthManager @javax.inject.Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext context: Context
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val credentialManager = CredentialManager.create(appContext)
    
    private val _userEmail = MutableStateFlow<String?>(prefs.getString(KEY_USER_EMAIL, null))
    val userEmail: StateFlow<String?> = _userEmail.asStateFlow()

    companion object {
        private const val TAG = "DriveAuthManager"
        private const val PREFS_NAME = "drive_auth_prefs"
        private const val KEY_USER_EMAIL = "user_email"
    }

    suspend fun signIn(activityContext: Context): Boolean {
        Log.d(TAG, "Starting signIn process...")
        return try {
            val serverClientId = "974414517482-m4ibjmnj0js4j6tpm3a78r18og3jksdq.apps.googleusercontent.com"
            @Suppress("KotlinConstantConditions")
            if (serverClientId == "YOUR_SERVER_CLIENT_ID_PLACEHOLDER") {
                Log.w(TAG, "Using placeholder Server Client ID! This will likely fail.")
            }

            val googleIdOption: GetGoogleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            Log.d(TAG, "Calling getCredential...")
            val result = credentialManager.getCredential(activityContext, request)
            Log.d(TAG, "getCredential result received")
            handleSignInResult(result)
        } catch (e: NoCredentialException) {
            Log.w(TAG, "NoCredentialException: No accounts found or user cancelled. ${e.message}")
            false
        } catch (e: GetCredentialException) {
            Log.e(TAG, "GetCredentialException: ${e.message}", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Exception during sign-in: ${e.message}", e)
            false
        }
    }

    private fun handleSignInResult(result: GetCredentialResponse): Boolean {
        val credential = result.credential
        Log.e(TAG, "handleSignInResult: Received credential type: ${credential::class.java.simpleName}")
        
        var email: String? = null
        
        if (credential is GoogleIdTokenCredential) {
            Log.e(TAG, "Credential is GoogleIdTokenCredential")
            email = credential.id
            Log.e(TAG, "Email from ID: $email")
        } else if (credential is androidx.credentials.CustomCredential && 
                   credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            Log.e(TAG, "Credential is CustomCredential of type TYPE_GOOGLE_ID_TOKEN_CREDENTIAL")
            try {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                email = googleIdTokenCredential.id
                Log.e(TAG, "Email from Custom ID: $email")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create GoogleIdTokenCredential from data", e)
            }
        }
        
        if (email.isNullOrEmpty()) {
            Log.e(TAG, "Sign-in successful but email is null or empty!")
            return false
        }

        // NEW: Check if account exists in system
        try {
            val am = android.accounts.AccountManager.get(appContext)
            val accounts = am.getAccountsByType("com.google")
            val exists = accounts.any { it.name.equals(email, ignoreCase = true) }
            Log.e(TAG, "System account check for '$email': Found = $exists")
            if (!exists) {
                Log.e(TAG, "Other system accounts found: ${accounts.map { it.name }}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking system accounts", e)
        }

        _userEmail.value = email
        prefs.edit { putString(KEY_USER_EMAIL, email) }
        Log.e(TAG, "Sign-in verified. User email stored: $email")
        
        return true
    }

    suspend fun signOut() {
        Log.e(TAG, "Signing out...")
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
        _userEmail.value = null
        prefs.edit { remove(KEY_USER_EMAIL) }
    }

    fun getDriveCredential(): GoogleAccountCredential? {
        val email = _userEmail.value
        Log.e(TAG, "getDriveCredential: stored email is '$email'")
        
        if (email.isNullOrEmpty()) {
            Log.e(TAG, "getDriveCredential: email is null or empty, returning null")
            return null
        }

        val scopes = listOf(
            DriveScopes.DRIVE_FILE,
            "https://www.googleapis.com/auth/generative-language.retriever",
            "https://www.googleapis.com/auth/calendar.events.readonly",
            "https://www.googleapis.com/auth/tasks.readonly"
        )
        val credential = GoogleAccountCredential.usingOAuth2(
            appContext, scopes
        )
        
        // NEW: Assign actual Account object to avoid null name issues in internal GMS code
        try {
            val account = android.accounts.Account(email, "com.google")
            credential.selectedAccount = account
            Log.e(TAG, "Created fresh GoogleAccountCredential with Account object for ${account.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create Account object for $email", e)
            credential.selectedAccountName = email
        }
        
        return credential
    }
}

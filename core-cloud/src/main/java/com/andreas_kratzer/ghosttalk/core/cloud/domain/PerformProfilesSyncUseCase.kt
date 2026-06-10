package com.andreas_kratzer.ghosttalk.core.cloud.domain

import android.content.Intent
import android.util.Log
import com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper
import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import com.andreas_kratzer.ghosttalk.core.settings.CloudSettings
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class PerformProfilesSyncUseCase @Inject constructor(
    private val googleAuthManager: GoogleAuthManager,
    private val cloudSyncUseCase: CloudSyncUseCase,
    private val settingsRepository: CloudSettings
) {
    private val TAG = "PerformProfilesSyncUseCase"

    sealed class Result {
        object Success : Result()
        data class RecoverableAuth(val intent: Intent) : Result()
        data class Error(val message: String) : Result()
    }

    suspend fun execute(
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result = withContext(Dispatchers.IO) {
        val authType = settingsRepository.googleAuthType
        Log.d(TAG, "Starting profiles sync. googleAuthType=$authType")

        val drive = try {
            val client = DriveServiceHelper.buildDriveClient(
                googleAuthManager = googleAuthManager
            )
            if (client == null) {
                Log.e(TAG, "buildDriveClient returned null! authType=$authType")
                return@withContext Result.Error("Keine Google-Anmeldedaten oder Verbindung fehlgeschlagen.")
            }
            client
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build Drive client for profiles sync", e)
            return@withContext Result.Error("Verbindung zum Google-Konto fehlgeschlagen.")
        }

        return@withContext try {
            val success = cloudSyncUseCase.syncProfilesOnly(drive, onProgress)
            if (success) {
                Result.Success
            } else {
                Result.Error("Profil-Synchronisation fehlgeschlagen.")
            }
        } catch (e: UserRecoverableAuthIOException) {
            Log.w(TAG, "UserRecoverableAuthIOException during profiles sync", e)
            Result.RecoverableAuth(e.intent)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected exception in PerformProfilesSyncUseCase: ${e.message}", e)
            Result.Error(e.message ?: "Unbekannter Fehler beim Profil-Sync")
        }
    }
}

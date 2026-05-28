package com.andreas_kratzer.ghosttalk.core.cloud.domain

import android.content.Intent
import android.util.Log
import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import com.andreas_kratzer.ghosttalk.core.settings.CloudSettings
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class PerformManualSyncUseCase @Inject constructor(
    private val googleAuthManager: GoogleAuthManager,
    private val cloudSyncUseCase: CloudSyncUseCase,
    private val settingsRepository: CloudSettings
) {
    private val TAG = "PerformManualSyncUseCase"

    sealed class Result {
        object Success : Result()
        data class RecoverableAuth(val intent: Intent) : Result()
        data class Error(val message: String) : Result()
    }

    suspend fun execute(
        mode: SyncMode,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting manual sync execution. Mode: $mode")
        val credential = googleAuthManager.getGoogleCredential()
        if (credential == null) {
            Log.e(TAG, "No Google credentials found.")
            return@withContext Result.Error("Keine Google-Anmeldedaten gefunden.")
        }

        return@withContext try {
            val drive = Drive.Builder(
                NetHttpTransport(), 
                GsonFactory.getDefaultInstance(), 
                credential
            ).setApplicationName("GhosTTalk").build()
            
            Log.d(TAG, "Calling cloudSyncUseCase.syncBook...")
            val success = cloudSyncUseCase.syncBook(drive, settingsRepository.activeBookId, mode, onProgress)
            Log.d(TAG, "syncBook result: $success")
            
            if (success) {
                if (mode != SyncMode.RESTORE_ONLY) {
                    settingsRepository.lastSuccessfulSyncTime = System.currentTimeMillis()
                }
                Result.Success
            } else if (mode == SyncMode.RESTORE_ONLY) {
                // Remove fallback to selection dialog for book-scoped restore
                Log.d(TAG, "Restore failed. No matching backup found for this book ID.")
                Result.Error("Kein passendes Backup für dieses Buch in der Cloud gefunden.")
            } else {
                Log.e(TAG, "Sync failed for unknown reasons in cloudSyncUseCase.")
                Result.Error("Sync fehlgeschlagen.")
            }
        } catch (e: UserRecoverableAuthIOException) {
            Log.w(TAG, "UserRecoverableAuthIOException during sync", e)
            Result.RecoverableAuth(e.intent)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected exception in PerformManualSyncUseCase: ${e.message}", e)
            Result.Error(e.message ?: "Unbekannter Fehler beim Sync")
        }
    }
}

package com.andreas_kratzer.ghosttalk.core.cloud.domain

import android.content.Context
import android.content.Intent
import android.util.Log
import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import com.andreas_kratzer.ghosttalk.core.cloud.GoogleWebAuthManager
import com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper
import com.andreas_kratzer.ghosttalk.core.settings.CloudSettings
import com.google.api.services.drive.Drive
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class PerformManualSyncUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val googleAuthManager: GoogleAuthManager,
    private val googleWebAuthManager: GoogleWebAuthManager,
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
        val isSaf = settingsRepository.syncTargetType == "LOCAL_FOLDER_SAF"

        val drive = if (isSaf) {
            null
        } else {
            DriveServiceHelper.buildDriveClient(
                context = context,
                authType = settingsRepository.googleAuthType,
                googleAuthManager = googleAuthManager,
                googleWebAuthManager = googleWebAuthManager
            ) ?: return@withContext Result.Error("Keine Google-Anmeldedaten oder Verbindung fehlgeschlagen.")
        }

        return@withContext try {
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

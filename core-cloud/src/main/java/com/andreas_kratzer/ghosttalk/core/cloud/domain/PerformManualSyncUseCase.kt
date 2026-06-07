package com.andreas_kratzer.ghosttalk.core.cloud.domain

import android.content.Context
import android.content.Intent
import android.util.Log
import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import com.andreas_kratzer.ghosttalk.core.cloud.GoogleWebAuthManager
import com.andreas_kratzer.ghosttalk.core.cloud.DriveServiceHelper
import com.andreas_kratzer.ghosttalk.core.settings.CloudSettings
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class PerformManualSyncUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
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
        val targetType = settingsRepository.syncTargetType
        val authType = settingsRepository.googleAuthType
        val folderId = settingsRepository.googleDriveFolderId
        val folderName = settingsRepository.googleDriveFolderName
        val safUri = settingsRepository.localFolderSafUri
        var isSaf = targetType == "LOCAL_FOLDER_SAF"
        Log.d(TAG, "Starting manual sync execution. Mode: $mode, syncTargetType='$targetType', isSaf=$isSaf, googleAuthType=$authType, googleDriveFolderId=$folderId, googleDriveFolderName=$folderName, localFolderSafUri=$safUri")

        // Auto-correct: syncTargetType is LOCAL_FOLDER_SAF but no valid SAF URI exists
        // and a Google Drive folder ID is present → switch to DRIVE_API
        if (isSaf && (safUri == null || !safUri.startsWith("content://")) && folderId != null && !folderId.startsWith("content://")) {
            Log.w(TAG, "Auto-correcting syncTargetType: was LOCAL_FOLDER_SAF but no valid SAF URI found (safUri=$safUri). Google Drive folder '$folderName' ($folderId) is configured. Switching to DRIVE_API.")
            settingsRepository.syncTargetType = "DRIVE_API"
            isSaf = false
        }

        val drive = if (isSaf) {
            Log.d(TAG, "SAF mode detected. Setting drive=null.")
            null
        } else {
            Log.d(TAG, "Drive API mode detected. Building drive client with authType=$authType...")
            val client = DriveServiceHelper.buildDriveClient(
                authType = authType,
                googleAuthManager = googleAuthManager,
                googleWebAuthManager = googleWebAuthManager
            )
            if (client == null) {
                Log.e(TAG, "buildDriveClient returned null! authType=$authType")
                return@withContext Result.Error("Keine Google-Anmeldedaten oder Verbindung fehlgeschlagen.")
            }
            Log.d(TAG, "Drive client built successfully.")
            client
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

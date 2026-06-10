package com.andreas_kratzer.ghosttalk.core.cloud

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.andreas_kratzer.ghosttalk.core.cloud.domain.CloudSyncUseCase
import com.andreas_kratzer.ghosttalk.core.cloud.domain.SyncMode
import com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class CloudSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val googleAuthManager: GoogleAuthManager,
    private val settingsRepository: SettingsRepository,
    private val buttonUsageRepository: ButtonUsageRepository,
    private val cloudSyncUseCase: CloudSyncUseCase
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Run rolling stats cleanup before uploading/syncing to Google Drive
        try {
            val retentionDays = settingsRepository.statsRetentionDays
            Log.d("CloudSyncWorker", "Running stats retention cleanup ($retentionDays days) before sync starts")
            buttonUsageRepository.cleanupOldStats(retentionDays)
        } catch (e: Exception) {
            Log.e("CloudSyncWorker", "Failed to run stats cleanup: ${e.message}", e)
        }

        val targetType = settingsRepository.syncTargetType
        val folderId = settingsRepository.googleDriveFolderId
        val safUri = settingsRepository.localFolderSafUri
        var isSaf = targetType == "LOCAL_FOLDER_SAF"

        // Auto-correct: syncTargetType is LOCAL_FOLDER_SAF but no valid SAF URI exists
        // and a Google Drive folder ID is present → switch to DRIVE_API
        if (isSaf && (safUri == null || !safUri.startsWith("content://")) && folderId != null && !folderId.startsWith("content://")) {
            Log.w("CloudSyncWorker", "Auto-correcting syncTargetType: was LOCAL_FOLDER_SAF but no valid SAF URI. Drive folder $folderId configured. Switching to DRIVE_API.")
            settingsRepository.syncTargetType = "DRIVE_API"
            isSaf = false
        }

        val drive = if (isSaf) {
            null
        } else {
            val client = DriveServiceHelper.buildDriveClient(
                googleAuthManager = googleAuthManager
            )
            if (client == null) {
                Log.w("CloudSyncWorker", "No credential available or failed to build Drive client. Failing sync.")
                return@withContext Result.failure()
            }
            client
        }

        val bookId = settingsRepository.activeBookId
        try {
            Log.d("CloudSyncWorker",
                "Starting background sync for book: $bookId (SAF: $isSaf)"
            )
            cloudSyncUseCase.syncBook(drive, bookId, SyncMode.TWO_WAY)
            Log.d("CloudSyncWorker", "Background sync completed successfully")
            settingsRepository.lastSuccessfulSyncTime = System.currentTimeMillis()
            Result.success()
        } catch (_: UserRecoverableAuthIOException) {
            Log.w("CloudSyncWorker", "UserRecoverableAuthIOException in background sync. Setup required.")
            Result.failure()
        } catch (e: Exception) {
            Log.e("CloudSyncWorker", $$"Background sync failed: ${e.message}", e)
            Result.retry()
        }
    }
}

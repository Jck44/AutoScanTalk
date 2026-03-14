package com.andreas_kratzer.ghosttalk.core.cloud

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.andreas_kratzer.ghosttalk.core.cloud.domain.CloudSyncUseCase
import com.andreas_kratzer.ghosttalk.core.cloud.domain.SyncMode
import com.andreas_kratzer.ghosttalk.core.settings.CloudSettings
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class CloudSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val googleAuthManager: GoogleAuthManager,
    private val settingsRepository: CloudSettings,
    private val cloudSyncUseCase: CloudSyncUseCase
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!settingsRepository.isCloudSyncEnabled) {
            return@withContext Result.success() // Sync was disabled while scheduled
        }

        val credential = googleAuthManager.getGoogleCredential()
        if (credential == null) {
            Log.w("CloudSyncWorker", "No credential available. Failing sync.")
            return@withContext Result.failure()
        }

        val bookId = settingsRepository.activeBookId
        val syncModeStr = settingsRepository.syncMode
        val mode = try {
            SyncMode.valueOf(syncModeStr)
        } catch (_: Exception) {
            SyncMode.TWO_WAY
        }

        try {
            val drive = Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("GhosTTalk").build()

            Log.d("CloudSyncWorker",
                $$"Starting background sync for book: $bookId with mode: $mode"
            )
            cloudSyncUseCase.syncBook(drive, bookId, mode)
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

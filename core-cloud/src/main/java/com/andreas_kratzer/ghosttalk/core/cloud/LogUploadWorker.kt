package com.andreas_kratzer.ghosttalk.core.cloud

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.andreas_kratzer.ghosttalk.core.cloud.domain.ExportLogsUseCase
import com.andreas_kratzer.ghosttalk.core.cloud.domain.LogUploadResult
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class LogUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val exportLogsUseCase: ExportLogsUseCase
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (settingsRepository.syncModeLogs == "OFF") {
            Log.d("LogUploadWorker", "Log upload is OFF. Skipping background task.")
            return@withContext Result.success()
        }

        try {
            Log.d("LogUploadWorker", "Starting background log upload...")
            when (val result = exportLogsUseCase.performAutoUpload()) {
                is LogUploadResult.Success -> {
                    Log.d("LogUploadWorker", "Background log upload succeeded.")
                    Result.success()
                }
                is LogUploadResult.Skipped -> {
                    Log.d("LogUploadWorker", "Background log upload skipped: ${result.reason}")
                    Result.success()
                }
                is LogUploadResult.Error -> {
                    Log.e("LogUploadWorker", "Background log upload failed: ${result.message}")
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            Log.e("LogUploadWorker", "Unexpected exception during background log upload: ${e.message}", e)
            Result.retry()
        }
    }
}

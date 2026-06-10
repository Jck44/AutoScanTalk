package com.andreas_kratzer.ghosttalk.core.cloud

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.andreas_kratzer.ghosttalk.core.cloud.domain.PerformProfilesSyncUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class ProfileSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val performProfilesSyncUseCase: PerformProfilesSyncUseCase
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d("ProfileSyncWorker", "Starting background profile sync...")
            when (val result = performProfilesSyncUseCase.execute()) {
                is PerformProfilesSyncUseCase.Result.Success -> {
                    Log.d("ProfileSyncWorker", "Background profile sync succeeded.")
                    Result.success()
                }
                is PerformProfilesSyncUseCase.Result.RecoverableAuth -> {
                    Log.w("ProfileSyncWorker", "Background profile sync requires recoverable auth.")
                    Result.failure()
                }
                is PerformProfilesSyncUseCase.Result.Error -> {
                    Log.e("ProfileSyncWorker", "Background profile sync failed: ${result.message}")
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            Log.e("ProfileSyncWorker", "Unexpected exception during background profile sync: ${e.message}", e)
            Result.retry()
        }
    }
}

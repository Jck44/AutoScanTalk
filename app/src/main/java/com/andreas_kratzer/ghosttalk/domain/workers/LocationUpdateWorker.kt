package com.andreas_kratzer.ghosttalk.domain.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.andreas_kratzer.ghosttalk.domain.executors.LocationExecutor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class LocationUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val locationExecutor: LocationExecutor
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d("LocationUpdateWorker", "Starting background location update")
        return@withContext try {
            val location = locationExecutor.getCurrentLocation(refresh = true)
            if (location != null) {
                Log.d("LocationUpdateWorker", "Successfully fetched background location: ${location.latitude}, ${location.longitude}")
                Result.success()
            } else {
                Log.w("LocationUpdateWorker", "Failed to fetch background location (location is null)")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("LocationUpdateWorker", "Error in background location update", e)
            Result.failure()
        }
    }
}

package com.andreas_kratzer.ghosttalk.domain.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.andreas_kratzer.ghosttalk.core.domain.WeatherUseCase
import com.andreas_kratzer.ghosttalk.domain.executors.LocationExecutor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class WeatherUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val locationExecutor: LocationExecutor,
    private val weatherUseCase: WeatherUseCase
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d("WeatherUpdateWorker", "Starting background weather update")
        return@withContext try {
            val location = locationExecutor.getCurrentLocation(refresh = false)
            if (location != null) {
                val result = weatherUseCase.getWeatherInfo(location.latitude, location.longitude, forceRefresh = true)
                if (result is WeatherUseCase.WeatherResult.Success) {
                    Log.d("WeatherUpdateWorker", "Successfully updated weather in background: ${result.condition}, ${result.temperature}")
                    Result.success()
                } else {
                    Log.w("WeatherUpdateWorker", "Failed to update weather: ${(result as? WeatherUseCase.WeatherResult.Error)?.message}")
                    Result.retry()
                }
            } else {
                Log.w("WeatherUpdateWorker", "Cannot update weather because location is unavailable")
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e("WeatherUpdateWorker", "Error in background weather update", e)
            Result.failure()
        }
    }
}

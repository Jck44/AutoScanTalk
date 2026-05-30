package com.andreas_kratzer.ghosttalk.domain.workers

import android.content.Context
import androidx.work.*
import com.andreas_kratzer.ghosttalk.core.domain.BackgroundScheduler
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackgroundSchedulerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val workManager: WorkManager
) : BackgroundScheduler {

    override fun scheduleLocationUpdate() {
        val enabled = settingsRepository.backgroundLocationEnabled
        val intervalHours = settingsRepository.backgroundLocationInterval
        
        if (enabled && intervalHours in 1..6) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val workRequest = PeriodicWorkRequestBuilder<LocationUpdateWorker>(intervalHours, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            
            workManager.enqueueUniquePeriodicWork(
                "LocationUpdateWorker",
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        } else {
            workManager.cancelUniqueWork("LocationUpdateWorker")
        }
    }

    override fun scheduleWeatherUpdate() {
        val enabled = settingsRepository.backgroundWeatherEnabled
        val intervalHours = settingsRepository.backgroundWeatherInterval
        
        if (enabled && intervalHours in 1..6) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val workRequest = PeriodicWorkRequestBuilder<WeatherUpdateWorker>(intervalHours, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            
            workManager.enqueueUniquePeriodicWork(
                "WeatherUpdateWorker",
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        } else {
            workManager.cancelUniqueWork("WeatherUpdateWorker")
        }
    }
}

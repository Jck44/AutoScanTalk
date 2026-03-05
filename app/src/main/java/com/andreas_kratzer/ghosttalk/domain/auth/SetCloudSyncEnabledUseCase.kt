package com.andreas_kratzer.ghosttalk.domain.auth

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.andreas_kratzer.ghosttalk.core.cloud.CloudSyncWorker
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class SetCloudSyncEnabledUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val workManager: WorkManager
) {
    operator fun invoke(enabled: Boolean) {
        settingsRepository.isCloudSyncEnabled = enabled
        if (enabled) {
            scheduleCloudSync()
        } else {
            workManager.cancelUniqueWork("CloudSyncWorker")
        }
    }

    private fun scheduleCloudSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val intervalMin = settingsRepository.syncIntervalMinutes
        val workRequest = PeriodicWorkRequestBuilder<CloudSyncWorker>(intervalMin, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            "CloudSyncWorker",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }
}

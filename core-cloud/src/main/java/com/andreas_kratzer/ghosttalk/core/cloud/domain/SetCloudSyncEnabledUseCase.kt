package com.andreas_kratzer.ghosttalk.core.cloud.domain

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.andreas_kratzer.ghosttalk.core.cloud.CloudSyncWorker
import com.andreas_kratzer.ghosttalk.core.settings.CloudSettings
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class SetCloudSyncEnabledUseCase @Inject constructor(
    private val settingsRepository: CloudSettings,
    private val workManager: WorkManager
) {
    operator fun invoke(enabled: Boolean) {
        settingsRepository.isDataCloudSyncEnabled = enabled
        if (enabled) {
            scheduleCloudSync()
        } else {
            workManager.cancelUniqueWork("CloudSyncWorker")
        }
    }

    fun reschedule() {
        if (settingsRepository.isDataCloudSyncEnabled) {
            scheduleCloudSync()
        }
    }

    private fun scheduleCloudSync() {
        val targetType = settingsRepository.syncTargetType
        val isSaf = targetType == "LOCAL_FOLDER_SAF"

        val constraintsBuilder = Constraints.Builder()
        if (!isSaf) {
            constraintsBuilder.setRequiredNetworkType(NetworkType.CONNECTED)
        }
        val constraints = constraintsBuilder.build()

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

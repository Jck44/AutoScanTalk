package com.andreas_kratzer.ghosttalk.core.cloud.domain

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.andreas_kratzer.ghosttalk.core.cloud.LogUploadWorker
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class RescheduleLogUploadUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val workManager: WorkManager
) {
    operator fun invoke() {
        val mode = settingsRepository.syncModeLogs
        if (mode == "OFF") {
            workManager.cancelUniqueWork("LogUploadWorker")
            return
        }

        val targetType = settingsRepository.syncTargetType
        val isSaf = targetType == "LOCAL_FOLDER_SAF"

        val constraintsBuilder = Constraints.Builder()
        if (!isSaf) {
            constraintsBuilder.setRequiredNetworkType(NetworkType.CONNECTED)
        }
        val constraints = constraintsBuilder.build()

        val intervalHours = settingsRepository.syncLogsIntervalHours
        val workRequest = PeriodicWorkRequestBuilder<LogUploadWorker>(intervalHours, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "LogUploadWorker",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }
}

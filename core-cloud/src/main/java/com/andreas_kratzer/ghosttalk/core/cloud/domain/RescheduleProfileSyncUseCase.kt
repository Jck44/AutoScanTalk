package com.andreas_kratzer.ghosttalk.core.cloud.domain

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.andreas_kratzer.ghosttalk.core.cloud.AuthManager
import com.andreas_kratzer.ghosttalk.core.cloud.ProfileSyncWorker
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class RescheduleProfileSyncUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val authManager: AuthManager,
    private val workManager: WorkManager
) {
    fun reschedule() {
        val isDriveConnected = authManager.userEmail.value != null
        if (!isDriveConnected) {
            workManager.cancelUniqueWork("ProfileSyncWorker")
            return
        }

        val targetType = settingsRepository.syncTargetType
        val isSaf = targetType == "LOCAL_FOLDER_SAF"

        val constraintsBuilder = Constraints.Builder()
        if (!isSaf) {
            constraintsBuilder.setRequiredNetworkType(NetworkType.CONNECTED)
        }
        val constraints = constraintsBuilder.build()

        val workRequest = PeriodicWorkRequestBuilder<ProfileSyncWorker>(4, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            "ProfileSyncWorker",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    fun runOnceImmediately() {
        val isDriveConnected = authManager.userEmail.value != null
        if (!isDriveConnected) return

        val targetType = settingsRepository.syncTargetType
        val isSaf = targetType == "LOCAL_FOLDER_SAF"

        val constraintsBuilder = Constraints.Builder()
        if (!isSaf) {
            constraintsBuilder.setRequiredNetworkType(NetworkType.CONNECTED)
        }
        val constraints = constraintsBuilder.build()

        val workRequest = OneTimeWorkRequestBuilder<ProfileSyncWorker>()
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniqueWork(
            "ProfileSyncWorker_OneTime",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }
}

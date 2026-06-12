package com.andreas_kratzer.ghosttalk.core.cloud

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncWorkRequester @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    fun enqueueOneTimeSync(policy: ExistingWorkPolicy) {
        try {
            val targetType = settingsRepository.syncTargetType
            val constraints = buildConstraints(targetType)

            val workRequest = OneTimeWorkRequest.Builder(CloudSyncWorker::class.java)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "CloudSyncWorker_OneTime",
                policy,
                workRequest
            )
            Log.d("SyncWorkRequester", "Enqueued OneTimeSync with policy $policy (targetType: $targetType)")
        } catch (e: Exception) {
            Log.e("SyncWorkRequester", "Failed to enqueue one-time sync: ${e.message}", e)
        }
    }

    companion object {
        fun buildConstraints(targetType: String?): Constraints {
            val isSaf = targetType == "LOCAL_FOLDER_SAF"
            val constraintsBuilder = Constraints.Builder()
            if (!isSaf) {
                constraintsBuilder.setRequiredNetworkType(NetworkType.CONNECTED)
            }
            return constraintsBuilder.build()
        }
    }
}

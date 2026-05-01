package com.andreas_kratzer.ghosttalk.core

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Handles Google Play In-App Updates.
 */
class UpdateManager(private val context: Context) {

    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(context)
    private val TAG = "UpdateManager"

    /**
     * Checks for available updates and starts an immediate update flow if found.
     * This is typically used for automatic background checks on startup.
     */
    fun checkForUpdates(updateLauncher: ActivityResultLauncher<IntentSenderRequest>) {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            ) {
                startUpdate(appUpdateInfo, updateLauncher)
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to check for updates", e)
        }
    }

    /**
     * Performs a manual update check and provides feedback via callbacks.
     */
    fun checkManualUpdate(
        updateLauncher: ActivityResultLauncher<IntentSenderRequest>,
        onUpdateFound: () -> Unit,
        onUpToDate: () -> Unit,
        onError: (String) -> Unit
    ) {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            when (appUpdateInfo.updateAvailability()) {
                UpdateAvailability.UPDATE_AVAILABLE -> {
                    if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                        onUpdateFound()
                        startUpdate(appUpdateInfo, updateLauncher)
                    } else {
                        onUpToDate()
                    }
                }
                UpdateAvailability.UPDATE_NOT_AVAILABLE -> {
                    onUpToDate()
                }
                else -> {
                    onUpToDate()
                }
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Manual update check failed", e)
            onError(e.message ?: "Unknown error")
        }
    }

    private fun startUpdate(
        appUpdateInfo: AppUpdateInfo,
        updateLauncher: ActivityResultLauncher<IntentSenderRequest>
    ) {
        try {
            appUpdateManager.startUpdateFlowForResult(
                appUpdateInfo,
                updateLauncher,
                AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start update flow", e)
        }
    }

    /**
     * Resumes an update that is already in progress (e.g., after app was backgrounded).
     */
    fun resumeUpdateIfInProgress(activity: Activity) {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                try {
                    appUpdateManager.startUpdateFlow(
                        appUpdateInfo,
                        activity,
                        AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to resume update flow", e)
                }
            }
        }
    }
}

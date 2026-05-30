package com.andreas_kratzer.ghosttalk.core

import android.content.Context
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallState
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface UpdateState {
    object Idle : UpdateState
    object Checking : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    object ReadyToInstall : UpdateState
    object NoUpdateAvailable : UpdateState
    data class Error(val message: String) : UpdateState
}

/**
 * Handles Google Play In-App Updates.
 */
@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(context)
    private val TAG = "UpdateManager"

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val installListener = InstallStateUpdatedListener { state ->
        handleInstallState(state)
    }

    init {
        appUpdateManager.registerListener(installListener)
        checkInitialState()
    }

    private fun handleInstallState(state: InstallState) {
        when (state.installStatus()) {
            InstallStatus.DOWNLOADING -> {
                val bytesDownloaded = state.bytesDownloaded()
                val totalBytesToDownload = state.totalBytesToDownload()
                val progress = if (totalBytesToDownload > 0) {
                    bytesDownloaded.toFloat() / totalBytesToDownload.toFloat()
                } else {
                    0f
                }
                _updateState.value = UpdateState.Downloading(progress)
            }
            InstallStatus.DOWNLOADED -> {
                _updateState.value = UpdateState.ReadyToInstall
            }
            InstallStatus.FAILED -> {
                _updateState.value = UpdateState.Error("Download failed")
            }
            InstallStatus.CANCELED -> {
                _updateState.value = UpdateState.Idle
            }
            else -> {
                // Keep current state or no-op
            }
        }
    }

    private fun checkInitialState() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                _updateState.value = UpdateState.ReadyToInstall
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to check initial update state", e)
        }
    }

    /**
     * Checks for available updates and starts a flexible update flow if found.
     * This is typically used for automatic checks on startup outside user mode.
     */
    fun checkForUpdates(updateLauncher: ActivityResultLauncher<IntentSenderRequest>) {
        _updateState.value = UpdateState.Checking
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            ) {
                startUpdate(appUpdateInfo, updateLauncher)
            } else if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                _updateState.value = UpdateState.ReadyToInstall
            } else {
                _updateState.value = UpdateState.NoUpdateAvailable
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to check for updates", e)
            _updateState.value = UpdateState.Error(e.message ?: "Failed to check for updates")
        }
    }

    /**
     * Checks silently without invoking any UI. Used for background checks, e.g. from user mode.
     */
    fun checkSilently() {
        _updateState.value = UpdateState.Checking
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            ) {
                _updateState.value = UpdateState.Idle // Let the action trigger startUpdate if desired
            } else if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                _updateState.value = UpdateState.ReadyToInstall
            } else {
                _updateState.value = UpdateState.NoUpdateAvailable
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Silent update check failed", e)
            _updateState.value = UpdateState.Error(e.message ?: "Failed to check for updates")
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
        _updateState.value = UpdateState.Checking
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            when (appUpdateInfo.updateAvailability()) {
                UpdateAvailability.UPDATE_AVAILABLE -> {
                    if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                        onUpdateFound()
                        startUpdate(appUpdateInfo, updateLauncher)
                    } else {
                        onUpToDate()
                    }
                }
                UpdateAvailability.UPDATE_NOT_AVAILABLE -> {
                    if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                        _updateState.value = UpdateState.ReadyToInstall
                        onUpdateFound() // In a way, it is found and ready
                    } else {
                        _updateState.value = UpdateState.NoUpdateAvailable
                        onUpToDate()
                    }
                }
                else -> {
                    _updateState.value = UpdateState.NoUpdateAvailable
                    onUpToDate()
                }
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Manual update check failed", e)
            _updateState.value = UpdateState.Error(e.message ?: "Unknown error")
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
                AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start update flow", e)
            _updateState.value = UpdateState.Error(e.message ?: "Failed to start update flow")
        }
    }

    /**
     * Installs the already downloaded update and restarts the app.
     */
    fun installDownloadedUpdate() {
        try {
            appUpdateManager.completeUpdate()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to complete update", e)
            _updateState.value = UpdateState.Error(e.message ?: "Failed to complete update")
        }
    }
}

package com.andreas_kratzer.ghosttalk.core

import android.content.Context
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallException
import com.google.android.play.core.install.InstallState
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallErrorCode
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
    /** Update is available on the Play Store but has not been downloaded yet. */
    object UpdateAvailableNotDownloaded : UpdateState
    data class Error(val message: String) : UpdateState
}

/**
 * Result of a [UpdateManager.triggerInstallAction] call.
 * Used by [ControlDeviceActionHandler] to pick the appropriate TTS feedback.
 */
sealed interface UpdateActionResult {
    /** An already-downloaded update is being installed now. App will restart shortly. */
    object Installing : UpdateActionResult
    /** Download is already in progress – user should wait. */
    object AlreadyDownloading : UpdateActionResult
    /** A fresh Play Store check is running – user should wait. */
    object AlreadyChecking : UpdateActionResult
    /** Update was found and the Play Store download dialog has been triggered. */
    object DownloadStarted : UpdateActionResult
    /** No update is available – the app is up to date. */
    object NoUpdate : UpdateActionResult
    /** App was not installed through the Play Store (sideloaded / ADB). */
    object NotFromPlayStore : UpdateActionResult
    /** An error occurred while contacting the Play Store. */
    data class Error(val message: String) : UpdateActionResult
}

/**
 * Handles Google Play In-App Updates.
 */
@Singleton
class UpdateManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(context)
    private val TAG = "UpdateManager"

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    /** Launcher registered by MainActivity – needed to show the Play Store download dialog. */
    private var registeredLauncher: ActivityResultLauncher<IntentSenderRequest>? = null

    /** Must be called from MainActivity.onCreate() after registerForActivityResult() is set up. */
    fun registerLauncher(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        Log.d(TAG, "registerLauncher: launcher registered")
        registeredLauncher = launcher
    }

    private val installListener = InstallStateUpdatedListener { state ->
        handleInstallState(state)
    }

    init {
        appUpdateManager.registerListener(installListener)
        checkInitialState()
    }

    private fun handleInstallState(state: InstallState) {
        val status = state.installStatus()
        Log.d(TAG, "handleInstallState: installStatus=$status, " +
                "bytesDownloaded=${state.bytesDownloaded()}, " +
                "totalBytes=${state.totalBytesToDownload()}")
        when (status) {
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
                Log.i(TAG, "handleInstallState: update DOWNLOADED – ready to install")
                _updateState.value = UpdateState.ReadyToInstall
            }
            InstallStatus.FAILED -> {
                Log.e(TAG, "handleInstallState: install FAILED – errorCode=${state.installErrorCode()}")
                _updateState.value = UpdateState.Error("Download failed (errorCode=${state.installErrorCode()})")
            }
            InstallStatus.CANCELED -> {
                Log.w(TAG, "handleInstallState: install CANCELED by user")
                _updateState.value = UpdateState.Idle
            }
            else -> {
                Log.d(TAG, "handleInstallState: unhandled status=$status – no state change")
            }
        }
    }

    /**
     * Checks whether a previously downloaded update is ready and caches that state.
     * Called at startup so the state is correct without waiting for a user action.
     */
    private fun checkInitialState() {
        Log.d(TAG, "checkInitialState: querying Play Store...")
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            val installStatus = appUpdateInfo.installStatus()
            val availability = appUpdateInfo.updateAvailability()
            Log.d(TAG, "checkInitialState: installStatus=$installStatus, availability=$availability")
            when {
                installStatus == InstallStatus.DOWNLOADED -> {
                    Log.i(TAG, "checkInitialState: update already DOWNLOADED – setting ReadyToInstall")
                    _updateState.value = UpdateState.ReadyToInstall
                }
                availability == UpdateAvailability.UPDATE_AVAILABLE -> {
                    Log.i(TAG, "checkInitialState: update AVAILABLE but not yet downloaded – setting UpdateAvailableNotDownloaded")
                    _updateState.value = UpdateState.UpdateAvailableNotDownloaded
                }
                else -> {
                    Log.d(TAG, "checkInitialState: no pending update (installStatus=$installStatus, availability=$availability)")
                }
            }
        }.addOnFailureListener { e ->
            if (e is InstallException && e.errorCode == InstallErrorCode.ERROR_APP_NOT_OWNED) {
                Log.d(TAG, "checkInitialState: app not installed via Play Store (sideloaded/ADB) – skipping")
            } else {
                Log.e(TAG, "checkInitialState: failed to check update state", e)
            }
        }
    }

    /**
     * Handles the INSTALL_UPDATE button action in a **single tap**.
     *
     * Uses [AppUpdateType.IMMEDIATE] so the Play Store takes over the full screen,
     * downloads and installs the update automatically, then restarts the app –
     * **no user interaction required** during the Play Store UI.
     *
     * Falls back to [AppUpdateType.FLEXIBLE] if IMMEDIATE is not allowed.
     *
     * Decision tree:
     * 1. Cached state is [UpdateState.ReadyToInstall]  → installs immediately (silent restart)
     * 2. Cached state is [UpdateState.Downloading]     → informs the user to wait
     * 3. Cached state is [UpdateState.Checking]        → informs the user to wait
     * 4. Otherwise                                     → asks Play Store directly:
     *    a. Already downloaded                         → installs immediately (silent restart)
     *    b. IMMEDIATE update available                 → full-screen auto-install, no interaction
     *    c. FLEXIBLE update available (fallback)       → opens Play Store download dialog
     *    d. No update                                  → reports up-to-date
     *
     * @param onResult called (possibly async) with the action outcome.
     */
    fun triggerInstallAction(onResult: (UpdateActionResult) -> Unit) {
        Log.d(TAG, "triggerInstallAction: currentState=\${_updateState.value}")
        when (val state = _updateState.value) {
            is UpdateState.ReadyToInstall -> {
                Log.i(TAG, "triggerInstallAction: update ready – installing now (silent restart)")
                onResult(UpdateActionResult.Installing)
                installDownloadedUpdate()
            }
            is UpdateState.Downloading -> {
                Log.d(TAG, "triggerInstallAction: download already in progress (progress=\${state.progress})")
                onResult(UpdateActionResult.AlreadyDownloading)
            }
            is UpdateState.Checking -> {
                Log.d(TAG, "triggerInstallAction: check already in progress")
                onResult(UpdateActionResult.AlreadyChecking)
            }
            else -> {
                // Either Idle, NoUpdateAvailable, UpdateAvailableNotDownloaded, or Error.
                // Always do a fresh Play Store query so a single tap is enough.
                val launcher = registeredLauncher
                if (launcher == null) {
                    Log.e(TAG, "triggerInstallAction: no launcher registered – cannot show Play Store dialog")
                    onResult(UpdateActionResult.Error("Kein Launcher registriert"))
                    return
                }
                Log.d(TAG, "triggerInstallAction: querying Play Store for fresh update info...")
                _updateState.value = UpdateState.Checking
                appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
                    val installStatus = info.installStatus()
                    val availability = info.updateAvailability()
                    Log.d(TAG, "triggerInstallAction: Play Store response – " +
                            "installStatus=$installStatus, availability=$availability, " +
                            "stalenessDays=${info.clientVersionStalenessDays()}")
                    when {
                        installStatus == InstallStatus.DOWNLOADED -> {
                            Log.i(TAG, "triggerInstallAction: update already DOWNLOADED – installing silently")
                            _updateState.value = UpdateState.ReadyToInstall
                            onResult(UpdateActionResult.Installing)
                            installDownloadedUpdate()
                        }
                        availability == UpdateAvailability.UPDATE_AVAILABLE
                                && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) -> {
                            // IMMEDIATE: full-screen auto-install, no user interaction needed
                            Log.i(TAG, "triggerInstallAction: IMMEDIATE update available – starting full-screen auto-install")
                            onResult(UpdateActionResult.DownloadStarted)
                            startUpdate(info, launcher, AppUpdateType.IMMEDIATE)
                        }
                        availability == UpdateAvailability.UPDATE_AVAILABLE
                                && info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> {
                            // IMMEDIATE not allowed – fall back to FLEXIBLE
                            Log.i(TAG, "triggerInstallAction: IMMEDIATE not allowed, falling back to FLEXIBLE")
                            onResult(UpdateActionResult.DownloadStarted)
                            startUpdate(info, launcher, AppUpdateType.FLEXIBLE)
                        }
                        availability == UpdateAvailability.UPDATE_AVAILABLE -> {
                            Log.w(TAG, "triggerInstallAction: update available but neither IMMEDIATE nor FLEXIBLE allowed")
                            _updateState.value = UpdateState.UpdateAvailableNotDownloaded
                            onResult(UpdateActionResult.Error("Update verfügbar, aber kein Update-Typ erlaubt"))
                        }
                        else -> {
                            Log.i(TAG, "triggerInstallAction: no update available (installStatus=$installStatus, availability=$availability)")
                            _updateState.value = UpdateState.NoUpdateAvailable
                            onResult(UpdateActionResult.NoUpdate)
                        }
                    }
                }.addOnFailureListener { e ->
                    if (e is InstallException && e.errorCode == InstallErrorCode.ERROR_APP_NOT_OWNED) {
                        Log.d(TAG, "triggerInstallAction: app not installed via Play Store (sideloaded/ADB)")
                        _updateState.value = UpdateState.NoUpdateAvailable
                        onResult(UpdateActionResult.NotFromPlayStore)
                    } else {
                        Log.e(TAG, "triggerInstallAction: appUpdateInfo check failed", e)
                        _updateState.value = UpdateState.Error(e.message ?: "Unknown error")
                        onResult(UpdateActionResult.Error(e.message ?: "Unbekannter Fehler"))
                    }
                }
            }
        }
    }

    /**
     * Checks for available updates and starts a flexible update flow if found.
     * Used for automatic checks on startup (non-User-Mode) and manual checks from Settings.
     */
    fun checkForUpdates(updateLauncher: ActivityResultLauncher<IntentSenderRequest>) {
        Log.d(TAG, "checkForUpdates: starting update check...")
        _updateState.value = UpdateState.Checking
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            val availability = appUpdateInfo.updateAvailability()
            val installStatus = appUpdateInfo.installStatus()
            Log.d(TAG, "checkForUpdates: availability=$availability, installStatus=$installStatus")
            if (availability == UpdateAvailability.UPDATE_AVAILABLE
                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            ) {
                Log.i(TAG, "checkForUpdates: update available – starting download flow")
                startUpdate(appUpdateInfo, updateLauncher)
            } else if (installStatus == InstallStatus.DOWNLOADED) {
                Log.i(TAG, "checkForUpdates: update already downloaded – ReadyToInstall")
                _updateState.value = UpdateState.ReadyToInstall
            } else {
                Log.d(TAG, "checkForUpdates: no update available")
                _updateState.value = UpdateState.NoUpdateAvailable
            }
        }.addOnFailureListener { e ->
            if (e is InstallException && e.errorCode == InstallErrorCode.ERROR_APP_NOT_OWNED) {
                Log.d(TAG, "checkForUpdates: app not installed via Play Store – skipping")
                _updateState.value = UpdateState.NoUpdateAvailable
            } else {
                Log.e(TAG, "checkForUpdates: failed", e)
                _updateState.value = UpdateState.Error(e.message ?: "Failed to check for updates")
            }
        }
    }

    /**
     * Performs a manual update check and provides feedback via callbacks.
     * Uses [AppUpdateType.IMMEDIATE] so the Play Store handles download + install
     * automatically without any user interaction during the process.
     * Falls back to [AppUpdateType.FLEXIBLE] if IMMEDIATE is not allowed.
     * Used by the Settings screen "Check for updates" button.
     */
    fun checkManualUpdate(
        updateLauncher: ActivityResultLauncher<IntentSenderRequest>,
        onUpdateFound: () -> Unit,
        onUpToDate: () -> Unit,
        onNotFromPlayStore: () -> Unit,
        onError: (String) -> Unit
    ) {
        Log.d(TAG, "checkManualUpdate: starting manual check...")
        _updateState.value = UpdateState.Checking
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            val availability = appUpdateInfo.updateAvailability()
            val installStatus = appUpdateInfo.installStatus()
            Log.d(TAG, "checkManualUpdate: availability=$availability, installStatus=$installStatus, " +
                    "stalenessDays=${appUpdateInfo.clientVersionStalenessDays()}")
            when {
                installStatus == InstallStatus.DOWNLOADED -> {
                    Log.i(TAG, "checkManualUpdate: update already downloaded – installing silently")
                    _updateState.value = UpdateState.ReadyToInstall
                    onUpdateFound()
                    installDownloadedUpdate()
                }
                availability == UpdateAvailability.UPDATE_AVAILABLE
                        && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) -> {
                    Log.i(TAG, "checkManualUpdate: IMMEDIATE update available – starting full-screen auto-install")
                    onUpdateFound()
                    startUpdate(appUpdateInfo, updateLauncher, AppUpdateType.IMMEDIATE)
                }
                availability == UpdateAvailability.UPDATE_AVAILABLE
                        && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> {
                    Log.i(TAG, "checkManualUpdate: IMMEDIATE not allowed, falling back to FLEXIBLE")
                    onUpdateFound()
                    startUpdate(appUpdateInfo, updateLauncher, AppUpdateType.FLEXIBLE)
                }
                else -> {
                    Log.d(TAG, "checkManualUpdate: no update available (availability=$availability)")
                    _updateState.value = UpdateState.NoUpdateAvailable
                    onUpToDate()
                }
            }
        }.addOnFailureListener { e ->
            if (e is InstallException && e.errorCode == InstallErrorCode.ERROR_APP_NOT_OWNED) {
                Log.d(TAG, "checkManualUpdate: app not installed via Play Store – informing caller")
                _updateState.value = UpdateState.NoUpdateAvailable
                onNotFromPlayStore()
            } else {
                Log.e(TAG, "checkManualUpdate: failed", e)
                _updateState.value = UpdateState.Error(e.message ?: "Unknown error")
                onError(e.message ?: "Unbekannter Fehler")
            }
        }
    }

    private fun startUpdate(
        appUpdateInfo: AppUpdateInfo,
        updateLauncher: ActivityResultLauncher<IntentSenderRequest>,
        updateType: Int = AppUpdateType.FLEXIBLE
    ) {
        val typeName = if (updateType == AppUpdateType.IMMEDIATE) "IMMEDIATE" else "FLEXIBLE"
        Log.d(TAG, "startUpdate: launching Play Store $typeName update flow")
        try {
            appUpdateManager.startUpdateFlowForResult(
                appUpdateInfo,
                updateLauncher,
                AppUpdateOptions.newBuilder(updateType).build()
            )
            // For IMMEDIATE updates, the Play Store manages the full lifecycle –
            // no Downloading state needed; the app restarts automatically.
            // For FLEXIBLE, track the download progress via installListener.
            if (updateType != AppUpdateType.IMMEDIATE) {
                _updateState.value = UpdateState.Downloading(0f)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startUpdate: failed to start $typeName update flow", e)
            _updateState.value = UpdateState.Error(e.message ?: "Failed to start update flow")
        }
    }

    /**
     * Installs the already downloaded update and restarts the app.
     */
    fun installDownloadedUpdate() {
        Log.i(TAG, "installDownloadedUpdate: calling completeUpdate()")
        try {
            appUpdateManager.completeUpdate()
        } catch (e: Exception) {
            Log.e(TAG, "installDownloadedUpdate: failed", e)
            _updateState.value = UpdateState.Error(e.message ?: "Failed to complete update")
        }
    }
}

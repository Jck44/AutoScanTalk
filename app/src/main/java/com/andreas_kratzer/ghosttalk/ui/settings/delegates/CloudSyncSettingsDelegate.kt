package com.andreas_kratzer.ghosttalk.ui.settings.delegates

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.widget.Toast
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.andreas_kratzer.ghosttalk.core.cloud.CloudSyncWorker
import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.domain.CloudSyncUseCase
import com.andreas_kratzer.ghosttalk.domain.SyncMode
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudSyncSettingsDelegate @Inject constructor(
    private val application: Application,
    private val settingsRepository: SettingsRepository,
    private val googleAuthManager: GoogleAuthManager,
    private val cloudSyncUseCase: CloudSyncUseCase,
    private val workManager: WorkManager
) {
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _authIntentFlow = MutableSharedFlow<Intent>()
    val authIntentFlow = _authIntentFlow.asSharedFlow()

    private val _signInErrorMessage = MutableStateFlow<String?>(null)
    val signInErrorMessage: StateFlow<String?> = _signInErrorMessage.asStateFlow()

    val userEmail = googleAuthManager.userEmail

    fun signIn(context: Context, scope: CoroutineScope) {
        val activity = findActivity(context) ?: return
        scope.launch {
            _signInErrorMessage.value = null
            val result = googleAuthManager.signIn(activity)
            if (!result) {
                _signInErrorMessage.value = "Anmeldung fehlgeschlagen. SHA-1 korrekt?"
            }
        }
    }

    fun signOut(scope: CoroutineScope) {
        scope.launch {
            googleAuthManager.signOut()
        }
    }

    fun setCloudSyncEnabled(enabled: Boolean) {
        settingsRepository.isCloudSyncEnabled = enabled
        if (enabled) scheduleCloudSync() else workManager.cancelUniqueWork("CloudSyncWorker")
    }

    fun performManualSync(mode: SyncMode, scope: CoroutineScope, driveOverride: Drive? = null) {
        val credential = googleAuthManager.getGoogleCredential()
        if (credential == null && driveOverride == null) return

        scope.launch {
            _isSyncing.value = true
            try {
                val drive = driveOverride ?: Drive.Builder(
                    NetHttpTransport(), GsonFactory.getDefaultInstance(), credential
                ).setApplicationName("GhosTTalk").build()
                
                cloudSyncUseCase.syncBook(drive, settingsRepository.activeBookId, mode)
                settingsRepository.lastSuccessfulSyncTime = System.currentTimeMillis()
            } catch (e: UserRecoverableAuthIOException) {
                _authIntentFlow.emit(e.intent)
            } catch (e: Exception) {
                Toast.makeText(application, "Sync Fehler: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun scheduleCloudSync() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val intervalMin = settingsRepository.syncIntervalMinutes
        val workRequest = PeriodicWorkRequestBuilder<CloudSyncWorker>(intervalMin, TimeUnit.MINUTES)
            .setConstraints(constraints).build()
        
        workManager.enqueueUniquePeriodicWork("CloudSyncWorker", ExistingPeriodicWorkPolicy.UPDATE, workRequest)
    }

    private fun findActivity(context: Context): Activity? {
        var currentContext = context
        while (currentContext is ContextWrapper) {
            if (currentContext is Activity) return currentContext
            currentContext = currentContext.baseContext
        }
        return null
    }
}

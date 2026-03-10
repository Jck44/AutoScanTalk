package com.andreas_kratzer.ghosttalk.ui.settings.delegates

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import com.andreas_kratzer.ghosttalk.domain.auth.PerformManualSyncUseCase
import com.andreas_kratzer.ghosttalk.domain.auth.SetCloudSyncEnabledUseCase
import com.andreas_kratzer.ghosttalk.domain.auth.SignInUseCase
import com.andreas_kratzer.ghosttalk.domain.auth.SignOutUseCase
import com.andreas_kratzer.ghosttalk.domain.auth.RemoteBackupInfo
import com.andreas_kratzer.ghosttalk.domain.auth.SyncMode
import com.google.api.services.drive.Drive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudSyncSettingsDelegate @Inject constructor(
    private val application: Application,
    private val googleAuthManager: GoogleAuthManager,
    private val setCloudSyncEnabledUseCase: SetCloudSyncEnabledUseCase,
    private val performManualSyncUseCase: PerformManualSyncUseCase,
    private val signInUseCase: SignInUseCase,
    private val signOutUseCase: SignOutUseCase
) {
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _authIntentFlow = MutableSharedFlow<Intent>()
    val authIntentFlow = _authIntentFlow.asSharedFlow()

    private val _signInErrorMessage = MutableStateFlow<String?>(null)
    val signInErrorMessage: StateFlow<String?> = _signInErrorMessage.asStateFlow()

    private val _availableBackups = MutableStateFlow<List<RemoteBackupInfo>>(emptyList())
    val availableBackups: StateFlow<List<RemoteBackupInfo>> = _availableBackups.asStateFlow()

    private val _showBackupSelectionDialog = MutableStateFlow(false)
    val showBackupSelectionDialog: StateFlow<Boolean> = _showBackupSelectionDialog.asStateFlow()

    val userEmail = googleAuthManager.userEmail

    fun signIn(context: Context, scope: CoroutineScope) {
        val activity = findActivity(context) ?: return
        scope.launch {
            _signInErrorMessage.value = null
            val result = signInUseCase.execute(activity)
            if (!result) {
                _signInErrorMessage.value = "Anmeldung fehlgeschlagen. SHA-1 korrekt?"
            }
        }
    }

    fun signOut(scope: CoroutineScope) {
        scope.launch {
            signOutUseCase.execute()
        }
    }

    fun setCloudSyncEnabled(context: Context, enabled: Boolean, scope: CoroutineScope) {
        if (enabled && userEmail.value == null) {
            signIn(context, scope)
        } else {
            setCloudSyncEnabledUseCase(enabled)
        }
    }

    fun performManualSync(
        mode: SyncMode,
        scope: CoroutineScope,
        driveOverride: Drive? = null,
        fileIdOverride: String? = null
    ) {
        scope.launch {
            _isSyncing.value = true
            if (fileIdOverride == null) {
                Toast.makeText(application, R.string.settings_cloud_sync_started, Toast.LENGTH_SHORT).show()
            }
            
            when (val result = performManualSyncUseCase.execute(mode, driveOverride, fileIdOverride)) {
                is PerformManualSyncUseCase.Result.Success -> {
                    val messageRes = when (mode) {
                        SyncMode.BACKUP_ONLY -> R.string.settings_cloud_backup_success
                        SyncMode.RESTORE_ONLY -> R.string.settings_cloud_restore_success
                        SyncMode.TWO_WAY -> R.string.settings_cloud_sync_success
                    }
                    Toast.makeText(application, messageRes, Toast.LENGTH_LONG).show()
                }
                is PerformManualSyncUseCase.Result.NoMatchingBackupFound -> {
                    _availableBackups.value = result.backups
                    _showBackupSelectionDialog.value = true
                }
                is PerformManualSyncUseCase.Result.RecoverableAuth -> {
                    _authIntentFlow.emit(result.intent)
                }
                is PerformManualSyncUseCase.Result.Error -> {
                    val errorMsg = application.getString(R.string.settings_cloud_sync_error, result.message)
                    Toast.makeText(application, errorMsg, Toast.LENGTH_LONG).show()
                }
            }
            _isSyncing.value = false
        }
    }

    fun restoreFromBackup(fileId: String, scope: CoroutineScope) {
        _showBackupSelectionDialog.value = false
        performManualSync(SyncMode.RESTORE_ONLY, scope, fileIdOverride = fileId)
    }

    fun dismissBackupSelectionDialog() {
        _showBackupSelectionDialog.value = false
        _availableBackups.value = emptyList<com.andreas_kratzer.ghosttalk.domain.auth.RemoteBackupInfo>()
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

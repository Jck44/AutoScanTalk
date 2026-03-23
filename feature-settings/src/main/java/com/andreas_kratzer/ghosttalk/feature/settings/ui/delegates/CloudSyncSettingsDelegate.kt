package com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.widget.Toast
import com.andreas_kratzer.ghosttalk.feature.settings.R
import com.andreas_kratzer.ghosttalk.core.cloud.AuthManager
import com.andreas_kratzer.ghosttalk.core.cloud.domain.PerformManualSyncUseCase
import com.andreas_kratzer.ghosttalk.core.cloud.domain.RemoteBackupInfo
import com.andreas_kratzer.ghosttalk.core.cloud.domain.SetCloudSyncEnabledUseCase
import com.andreas_kratzer.ghosttalk.core.cloud.domain.SignInUseCase
import com.andreas_kratzer.ghosttalk.core.cloud.domain.SignOutUseCase
import com.andreas_kratzer.ghosttalk.core.cloud.domain.SyncMode
import com.andreas_kratzer.ghosttalk.core.cloud.domain.CloudSyncUseCase
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.andreas_kratzer.ghosttalk.core.data.SyncLogProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudSyncSettingsDelegate @Inject constructor(
    private val application: Application,
    private val authManager: AuthManager,
    private val settingsRepository: SettingsRepository,
    private val setCloudSyncEnabledUseCase: SetCloudSyncEnabledUseCase,
    private val performManualSyncUseCase: PerformManualSyncUseCase,
    private val cloudSyncUseCase: CloudSyncUseCase,
    private val signInUseCase: SignInUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val syncLogProvider: SyncLogProvider
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

    private val _syncLogs = MutableStateFlow<List<String>>(emptyList())
    val syncLogs: StateFlow<List<String>> = _syncLogs.asStateFlow()

    fun loadSyncLogs(scope: CoroutineScope) {
        scope.launch {
            _syncLogs.value = syncLogProvider.loadSavedLogs()
        }
    }

    fun clearSyncLogs(scope: CoroutineScope) {
        scope.launch {
            syncLogProvider.clearLogs()
            _syncLogs.value = emptyList()
        }
    }

    val userEmail = authManager.userEmail

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
        scope: CoroutineScope
    ) {
        scope.launch {
            _isSyncing.value = true
            Toast.makeText(application, R.string.settings_cloud_sync_started, Toast.LENGTH_SHORT).show()
            
            when (val result = performManualSyncUseCase.execute(mode)) {
                is PerformManualSyncUseCase.Result.Success -> {
                    val messageRes = when (mode) {
                        SyncMode.BACKUP_ONLY -> R.string.settings_cloud_backup_success
                        SyncMode.RESTORE_ONLY -> R.string.settings_cloud_restore_success
                        SyncMode.TWO_WAY -> R.string.settings_cloud_sync_success
                    }
                    Toast.makeText(application, messageRes, Toast.LENGTH_LONG).show()
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

    fun dismissBackupSelectionDialog() {
        _showBackupSelectionDialog.value = false
        _availableBackups.value = emptyList()
    }

    fun fetchAvailableBackupsForImport(scope: CoroutineScope) {
        scope.launch {
            val credential = authManager.getGoogleCredential()
            if (credential == null) {
                Toast.makeText(application, "Kein Cloud-Konto verbunden.", Toast.LENGTH_LONG).show()
                return@launch
            }
            _isSyncing.value = true
            try {
                val drive = com.google.api.services.drive.Drive.Builder(
                    com.google.api.client.http.javanet.NetHttpTransport(),
                    com.google.api.client.json.gson.GsonFactory.getDefaultInstance(),
                    credential
                ).setApplicationName("GhostTalk").build()

                val backups = cloudSyncUseCase.getAvailableBackups(drive)
                _availableBackups.value = backups
                if (backups.isEmpty()) {
                    Toast.makeText(application, "Keine Backups in der Cloud gefunden.", Toast.LENGTH_LONG).show()
                } else {
                    _showBackupSelectionDialog.value = true
                }
            } catch (e: UserRecoverableAuthIOException) {
                _authIntentFlow.emit(e.intent)
            } catch (e: Exception) {
                Toast.makeText(application, "Fehler beim Laden der Backups: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun importCloudBackup(backupInfo: RemoteBackupInfo, scope: CoroutineScope, onImported: (String) -> Unit = {}) {
        _showBackupSelectionDialog.value = false
        scope.launch {
            val credential = authManager.getGoogleCredential() ?: return@launch
            _isSyncing.value = true
            Toast.makeText(application, "Import wird gestartet...", Toast.LENGTH_SHORT).show()
            
            try {
                val drive = com.google.api.services.drive.Drive.Builder(
                    com.google.api.client.http.javanet.NetHttpTransport(),
                    com.google.api.client.json.gson.GsonFactory.getDefaultInstance(),
                    credential
                ).setApplicationName("GhostTalk").build()

                val result = cloudSyncUseCase.importCloudBackup(drive, backupInfo.fileId, backupInfo.fileName)
                if (result.isSuccess) {
                    val bookId = result.getOrThrow()
                    settingsRepository.activeBookId = bookId
                    Toast.makeText(application, R.string.book_import_cloud_success, Toast.LENGTH_LONG).show()
                    onImported(bookId)
                } else {
                    val errorMsg = application.getString(R.string.book_import_cloud_error, result.exceptionOrNull()?.message ?: "Unbekannter Fehler")
                    Toast.makeText(application, errorMsg, Toast.LENGTH_LONG).show()
                }
            } catch (e: UserRecoverableAuthIOException) {
                _authIntentFlow.emit(e.intent)
            } catch (e: Exception) {
                val errorMsg = application.getString(R.string.book_import_cloud_error, e.message ?: "Unerwarteter Fehler")
                Toast.makeText(application, errorMsg, Toast.LENGTH_LONG).show()
            } finally {
                _isSyncing.value = false
            }
        }
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

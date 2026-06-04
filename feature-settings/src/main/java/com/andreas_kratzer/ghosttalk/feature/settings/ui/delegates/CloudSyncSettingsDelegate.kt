package com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.andreas_kratzer.ghosttalk.core.cloud.AuthManager
import com.andreas_kratzer.ghosttalk.core.cloud.domain.CloudSyncUseCase
import com.andreas_kratzer.ghosttalk.core.cloud.domain.GetDriveFoldersUseCase
import com.andreas_kratzer.ghosttalk.core.cloud.domain.PerformManualSyncUseCase
import com.andreas_kratzer.ghosttalk.core.cloud.domain.RemoteBackupInfo
import com.andreas_kratzer.ghosttalk.core.cloud.domain.SetCloudSyncEnabledUseCase
import com.andreas_kratzer.ghosttalk.core.cloud.domain.SignInUseCase
import com.andreas_kratzer.ghosttalk.core.cloud.domain.SignOutUseCase
import com.andreas_kratzer.ghosttalk.core.cloud.domain.SyncMode
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.SyncLogProvider
import com.andreas_kratzer.ghosttalk.feature.settings.R
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.services.drive.model.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val getDriveFoldersUseCase: GetDriveFoldersUseCase,
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

    private val _driveFolders = MutableStateFlow<List<File>>(emptyList())
    val driveFolders: StateFlow<List<File>> = _driveFolders.asStateFlow()

    private val _isBrowsingFolders = MutableStateFlow(false)
    val isBrowsingFolders: StateFlow<Boolean> = _isBrowsingFolders.asStateFlow()

    private var lastImportSafUri: String? = null
    private var lastImportSafName: String? = null

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

    fun switchAccount(context: Context, scope: CoroutineScope) {
        val activity = findActivity(context) ?: return
        scope.launch {
            signOutUseCase.execute()
            _signInErrorMessage.value = null
            val result = signInUseCase.execute(activity)
            if (!result) {
                _signInErrorMessage.value = "Konto wechseln fehlgeschlagen."
            }
        }
    }

    fun setCloudSyncEnabled(context: Context, enabled: Boolean, scope: CoroutineScope) {
        if (enabled && userEmail.value == null) {
            signIn(context, scope)
        } else {
            setCloudSyncEnabledUseCase(enabled)
        }
    }

    fun reschedule() {
        setCloudSyncEnabledUseCase.reschedule()
    }

    fun performManualSync(
        mode: SyncMode,
        scope: CoroutineScope,
        onProgress: (Float, String) -> Unit = { _, _ -> },
        onComplete: () -> Unit = {}
    ) {
        scope.launch {
            _isSyncing.value = true
            
            try {
                when (val result = performManualSyncUseCase.execute(mode, onProgress)) {
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
            } finally {
                _isSyncing.value = false
                onComplete()
            }
        }
    }

    fun dismissBackupSelectionDialog() {
        _showBackupSelectionDialog.value = false
        _availableBackups.value = emptyList()
    }

    fun fetchDriveFolders(parentFolderId: String = "root", scope: CoroutineScope) {
        scope.launch {
            val credential = authManager.getGoogleCredential()
            if (credential == null) {
                Toast.makeText(application, "Kein Cloud-Konto verbunden.", Toast.LENGTH_LONG).show()
                return@launch
            }
            _isBrowsingFolders.value = true
            try {
                val drive = com.google.api.services.drive.Drive.Builder(
                    com.google.api.client.http.javanet.NetHttpTransport(),
                    com.google.api.client.json.gson.GsonFactory.getDefaultInstance()
                ) { request ->
                    credential.initialize(request)
                    request.connectTimeout = 3 * 60 * 1000 // 3 minutes
                    request.readTimeout = 3 * 60 * 1000    // 3 minutes
                }.setApplicationName("GhostTalk").build()

                _driveFolders.value = getDriveFoldersUseCase.execute(drive, parentFolderId)
            } catch (e: UserRecoverableAuthIOException) {
                _authIntentFlow.emit(e.intent)
            } catch (e: Exception) {
                Toast.makeText(application, "Fehler beim Laden der Ordner: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                _isBrowsingFolders.value = false
            }
        }
    }

    fun selectDriveFolder(folderId: String?, folderName: String?) {
        settingsRepository.googleDriveFolderId = folderId
        settingsRepository.googleDriveFolderName = folderName
    }

    fun fetchAvailableBackupsForImportByUrlOrId(urlOrId: String, scope: CoroutineScope) {
        val folderId = extractFolderId(urlOrId)
        if (folderId.isEmpty()) {
            Toast.makeText(application, "Ungültige ID oder URL", Toast.LENGTH_LONG).show()
            return
        }
        fetchAvailableBackupsForImport(folderId, scope)
    }

    fun fetchAvailableBackupsFromSaf(uri: String, name: String, scope: CoroutineScope) {
        lastImportSafUri = uri
        lastImportSafName = name
        scope.launch {
            _isSyncing.value = true
            try {
                val backups = cloudSyncUseCase.getAvailableBackups(null, uri)
                _availableBackups.value = backups
                if (backups.isEmpty()) {
                    Toast.makeText(application, "Keine Backups in dem ausgewählten Ordner gefunden.", Toast.LENGTH_LONG).show()
                } else {
                    _showBackupSelectionDialog.value = true
                }
            } catch (e: Exception) {
                Toast.makeText(application, "Fehler beim Laden der Backups: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun fetchAvailableBackupsForImport(folderId: String? = null, scope: CoroutineScope) {
        scope.launch {
            val targetType = settingsRepository.syncTargetType
            val isSaf = targetType == "LOCAL_FOLDER_SAF"
            _isSyncing.value = true
            try {
                val backups = if (isSaf) {
                    cloudSyncUseCase.getAvailableBackups(null, null)
                } else {
                    val credential = authManager.getGoogleCredential()
                    if (credential == null) {
                        Toast.makeText(application, "Kein Cloud-Konto verbunden.", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    val drive = com.google.api.services.drive.Drive.Builder(
                        com.google.api.client.http.javanet.NetHttpTransport(),
                        com.google.api.client.json.gson.GsonFactory.getDefaultInstance()
                    ) { request ->
                        credential.initialize(request)
                        request.connectTimeout = 3 * 60 * 1000 // 3 minutes
                        request.readTimeout = 3 * 60 * 1000    // 3 minutes
                    }.setApplicationName("GhostTalk").build()
                    cloudSyncUseCase.getAvailableBackups(drive, folderId)
                }

                _availableBackups.value = backups
                if (backups.isEmpty()) {
                    Toast.makeText(application, "Keine Backups gefunden.", Toast.LENGTH_LONG).show()
                } else {
                    _showBackupSelectionDialog.value = true
                }
            } catch (e: Exception) {
                if (e is UserRecoverableAuthIOException) {
                    _authIntentFlow.emit(e.intent)
                } else {
                    Toast.makeText(application, "Fehler beim Laden der Backups: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun importCloudBackup(
        backupInfo: RemoteBackupInfo, 
        scope: CoroutineScope, 
        onProgress: (Float, String) -> Unit = { _, _ -> },
        onImported: (String) -> Unit = {},
        onComplete: () -> Unit = {}
    ) {
        _showBackupSelectionDialog.value = false
        scope.launch {
            val isSaf = backupInfo.fileId.startsWith("content://")
            _isSyncing.value = true
            Toast.makeText(application, "Import wird gestartet...", Toast.LENGTH_SHORT).show()
            
            try {
                val drive = if (isSaf) {
                    null
                } else {
                    val credential = authManager.getGoogleCredential()
                    if (credential == null) {
                        Toast.makeText(application, "Anmeldung fehlgeschlagen.", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    com.google.api.services.drive.Drive.Builder(
                        com.google.api.client.http.javanet.NetHttpTransport(),
                        com.google.api.client.json.gson.GsonFactory.getDefaultInstance()
                    ) { request ->
                        credential.initialize(request)
                        request.connectTimeout = 3 * 60 * 1000 // 3 minutes
                        request.readTimeout = 3 * 60 * 1000    // 3 minutes
                    }.setApplicationName("GhostTalk").build()
                }

                val result = cloudSyncUseCase.importCloudBackup(drive, backupInfo.fileId, backupInfo.fileName, onProgress)
                if (result.isSuccess) {
                    val bookId = result.getOrThrow()
                    settingsRepository.activeBookId = bookId
                    
                    // Adopt SAF folder as the sync target if we just imported via SAF
                    if (isSaf && lastImportSafUri != null) {
                        settingsRepository.syncTargetType = "LOCAL_FOLDER_SAF"
                        settingsRepository.localFolderSafUri = lastImportSafUri
                        settingsRepository.localFolderSafName = lastImportSafName
                    }
                    
                    Toast.makeText(application, R.string.book_import_cloud_success, Toast.LENGTH_LONG).show()
                    onImported(bookId)
                } else {
                    val errorMsg = application.getString(R.string.book_import_cloud_error, result.exceptionOrNull()?.message ?: "Unbekannter Fehler")
                    Toast.makeText(application, errorMsg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                if (e is UserRecoverableAuthIOException) {
                    _authIntentFlow.emit(e.intent)
                } else {
                    Toast.makeText(application, "Fehler beim Importieren: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                _isSyncing.value = false
                onComplete()
            }
        }
    }

    fun selectDriveFolderByUrlOrId(urlOrId: String, scope: CoroutineScope, onResult: (Boolean, String?) -> Unit) {
        val folderId = extractFolderId(urlOrId)
        if (folderId.isEmpty()) {
            onResult(false, "Ungültige ID oder URL")
            return
        }
        scope.launch {
            val credential = authManager.getGoogleCredential()
            if (credential == null) {
                onResult(false, "Kein Cloud-Konto verbunden.")
                return@launch
            }
            try {
                val drive = com.google.api.services.drive.Drive.Builder(
                    com.google.api.client.http.javanet.NetHttpTransport(),
                    com.google.api.client.json.gson.GsonFactory.getDefaultInstance()
                ) { request ->
                    credential.initialize(request)
                    request.connectTimeout = 3 * 60 * 1000 // 3 minutes
                    request.readTimeout = 3 * 60 * 1000    // 3 minutes
                }.setApplicationName("GhostTalk").build()

                val folder = withContext(Dispatchers.IO) {
                    drive.files().get(folderId)
                        .setFields("id, name")
                        .execute()
                }
                settingsRepository.googleDriveFolderId = folder.id
                settingsRepository.googleDriveFolderName = folder.name
                onResult(true, folder.name)
            } catch (e: Exception) {
                Log.e("CloudSyncDelegate", "Failed to access manually entered folder: ${e.message}", e)
                onResult(false, "Zugriff verweigert oder Ordner existiert nicht. Hat die App diesen Ordner erstellt?")
            }
        }
    }

    internal fun extractFolderId(input: String): String {
        val trimmed = input.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            val pattern1 = "folders/([a-zA-Z0-9_-]+)".toRegex()
            val match1 = pattern1.find(trimmed)
            if (match1 != null) {
                return match1.groupValues[1]
            }
            val pattern2 = "id=([a-zA-Z0-9_-]+)".toRegex()
            val match2 = pattern2.find(trimmed)
            if (match2 != null) {
                return match2.groupValues[1]
            }
        }
        if (trimmed.matches("[a-zA-Z0-9_-]+".toRegex())) {
            return trimmed
        }
        return ""
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

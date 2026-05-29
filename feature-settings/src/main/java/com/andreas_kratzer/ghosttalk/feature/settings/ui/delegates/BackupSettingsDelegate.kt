package com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates

import android.content.Context
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.SyncLogProvider
import com.andreas_kratzer.ghosttalk.core.data.export.PageImportExportProvider
import com.andreas_kratzer.ghosttalk.feature.settings.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

class BackupSettingsDelegate @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val bookRepository: BookRepository,
    private val importExportManager: PageImportExportProvider,
    private val syncLogProvider: SyncLogProvider
) {
    private val _isBackupRestoreRunning = MutableStateFlow(false)
    val isBackupRestoreRunning: StateFlow<Boolean> = _isBackupRestoreRunning.asStateFlow()

    private val _backupRestoreProgress = MutableStateFlow(0f)
    val backupRestoreProgress: StateFlow<Float> = _backupRestoreProgress.asStateFlow()

    private val _backupRestoreStatus = MutableStateFlow<String?>(null)
    val backupRestoreStatus: StateFlow<String?> = _backupRestoreStatus.asStateFlow()

    private var scope: CoroutineScope? = null

    fun initialize(scope: CoroutineScope) {
        this.scope = scope
    }

    private fun viewModelScopeLaunch(block: suspend CoroutineScope.() -> Unit) {
        val activeScope = checkNotNull(scope) { "BackupSettingsDelegate scope has not been initialized. Call initialize(scope) first." }
        activeScope.launch {
            block()
        }
    }

    fun setBackupRestoreRunning(running: Boolean) {
        _isBackupRestoreRunning.value = running
    }

    fun setBackupRestoreProgress(progress: Float) {
        _backupRestoreProgress.value = progress
    }

    fun setBackupRestoreStatus(status: String?) {
        _backupRestoreStatus.value = status
    }

    suspend fun exportLocalBackup(): String {
        return importExportManager.exportBookToJson(settingsRepository.activeBookId)
    }

    suspend fun exportLocalBackupZip(outputStream: OutputStream) {
        _isBackupRestoreRunning.value = true
        _backupRestoreProgress.value = 0f
        _backupRestoreStatus.value = context.getString(R.string.backup_progress_exporting)
        
        val activeBookId = settingsRepository.activeBookId
        val activeBookName = bookRepository.getBookById(activeBookId)?.name
        
        try {
            importExportManager.exportBookToZip(activeBookId, outputStream) { progress, status ->
                _backupRestoreProgress.value = progress
                _backupRestoreStatus.value = when {
                    status == "Exporting database..." -> context.getString(R.string.backup_progress_exporting)
                    status.startsWith("Compressing audio:") -> context.getString(R.string.backup_progress_compressing, status.substringAfter(": "))
                    status == "Backup complete." -> context.getString(R.string.backup_progress_complete)
                    else -> status
                }
            }
            syncLogProvider.addLogEntry("Lokale Sicherung erstellt", activeBookId, activeBookName)
        } catch (e: Exception) {
            syncLogProvider.addLogEntry("Lokale Sicherung fehlgeschlagen: ${e.message}", activeBookId, activeBookName, isError = true)
            throw e
        } finally {
            delay(1000) // Show complete message briefly
            _isBackupRestoreRunning.value = false
            _backupRestoreStatus.value = null
        }
    }

    suspend fun importLocalBackupZip(
        inputStream: InputStream,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        _isBackupRestoreRunning.value = true
        _backupRestoreProgress.value = 0f
        _backupRestoreStatus.value = context.getString(R.string.restore_progress_importing)

        val activeBookId = settingsRepository.activeBookId
        val activeBookName = bookRepository.getBookById(activeBookId)?.name

        try {
            val result = importExportManager.importFromZip(
                inputStream = inputStream,
                bookId = activeBookId,
                regenerateIds = false,
                restoreSyncSettings = false
            ) { progress, status ->
                _backupRestoreProgress.value = progress
                _backupRestoreStatus.value = when {
                    status == "Importing data..." -> context.getString(R.string.restore_progress_importing)
                    status.startsWith("Extracting:") -> context.getString(R.string.restore_progress_extracting, status.substringAfter(": "))
                    status == "Import complete." -> context.getString(R.string.restore_progress_complete)
                    else -> status
                }
            }
            result.onSuccess { 
                syncLogProvider.addLogEntry("Lokale Wiederherstellung (ZIP) erfolgreich", activeBookId, activeBookName)
                onSuccess() 
            }
            .onFailure { e -> 
                syncLogProvider.addLogEntry("Lokale Wiederherstellung (ZIP) fehlgeschlagen: ${e.message}", activeBookId, activeBookName, isError = true)
                onError("Fehler beim ZIP-Import: ${e.message}") 
            }
        } catch (e: Exception) {
            syncLogProvider.addLogEntry("Fehler bei lokaler Wiederherstellung (ZIP): ${e.message}", activeBookId, activeBookName, isError = true)
            throw e
        } finally {
            delay(1000)
            _isBackupRestoreRunning.value = false
            _backupRestoreStatus.value = null
        }
    }

    suspend fun importLocalBackup(json: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val activeBookId = settingsRepository.activeBookId
        val activeBookName = bookRepository.getBookById(activeBookId)?.name
        
        val importBookId = importExportManager.extractBookIdFromJson(json)
        
        if (importBookId != activeBookId) {
            onError("Fehler: Buch-IDs stimmen nicht überein. Dieses Backup gehört zu einem anderen Buch.")
            return
        }

        val result = importExportManager.importFromJson(
            jsonString = json,
            bookId = activeBookId,
            regenerateIds = false, // Preserve IDs for matching book
            restoreSyncSettings = false
        )
        result.onSuccess { 
            syncLogProvider.addLogEntry("Lokale Wiederherstellung (JSON) erfolgreich", activeBookId, activeBookName)
            onSuccess() 
        }
        .onFailure { e -> 
            syncLogProvider.addLogEntry("Lokale Wiederherstellung (JSON) fehlgeschlagen: ${e.message}", activeBookId, activeBookName, isError = true)
            onError("Fehler beim Import: ${e.message}") 
        }
    }

    suspend fun importGlobalManualBackup(json: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        val result = importExportManager.importCloudBackup(json, null)
        result.onSuccess { bookId -> 
            syncLogProvider.addLogEntry("Globaler Import (JSON) erfolgreich", bookId, null)
            onSuccess(bookId) 
        }
        .onFailure { e -> 
            syncLogProvider.addLogEntry("Globaler Import (JSON) fehlgeschlagen: ${e.message}", null, null, isError = true)
            onError("Fehler beim globalen Import: ${e.message}") 
        }
    }

    suspend fun importGlobalManualBackupZip(
        inputStream: InputStream,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        _isBackupRestoreRunning.value = true
        _backupRestoreProgress.value = 0f
        _backupRestoreStatus.value = context.getString(R.string.restore_progress_importing)

        try {
            val result = importExportManager.importCloudBackupFromZip(
                inputStream = inputStream,
                cloudFileId = null
            ) { progress, status ->
                _backupRestoreProgress.value = progress
                _backupRestoreStatus.value = when {
                    status == "Importing data..." || status == "Importing book..." -> context.getString(R.string.restore_progress_importing)
                    status.startsWith("Extracting:") -> context.getString(R.string.restore_progress_extracting, status.substringAfter(": "))
                    status == "Import complete." -> context.getString(R.string.restore_progress_complete)
                    else -> status
                }
            }
            result.onSuccess { bookId -> 
                syncLogProvider.addLogEntry("Globaler Import (ZIP) erfolgreich", bookId, null)
                onSuccess(bookId) 
            }
            .onFailure { e -> 
                syncLogProvider.addLogEntry("Globaler Import (ZIP) fehlgeschlagen: ${e.message}", null, null, isError = true)
                onError("Fehler beim globalen ZIP-Import: ${e.message}") 
            }
        } catch (e: Exception) {
            syncLogProvider.addLogEntry("Fehler beim globalen ZIP-Import: ${e.message}", null, null, isError = true)
            throw e
        } finally {
            delay(1000)
            _isBackupRestoreRunning.value = false
            _backupRestoreStatus.value = null
        }
    }

    fun handleCloudProgress(progress: Float, status: String) {
        _backupRestoreProgress.value = progress
        _backupRestoreStatus.value = when {
            status == "Uploading to Drive..." -> context.getString(R.string.cloud_progress_uploading)
            status == "Downloading from Drive..." -> context.getString(R.string.cloud_progress_downloading)
            status == "Exporting database..." -> context.getString(R.string.backup_progress_exporting)
            status.startsWith("Compressing audio:") -> context.getString(R.string.backup_progress_compressing, status.substringAfter(": "))
            status.startsWith("Extracting:") -> context.getString(R.string.restore_progress_extracting, status.substringAfter(": "))
            status == "Importing data..." -> context.getString(R.string.restore_progress_importing)
            status == "Import complete." -> context.getString(R.string.restore_progress_complete)
            status == "Backup complete." -> context.getString(R.string.backup_progress_complete)
            else -> status
        }
    }

    fun finishBackupRestoreProgress() {
        viewModelScopeLaunch {
            delay(1000)
            _isBackupRestoreRunning.value = false
            _backupRestoreStatus.value = null
        }
    }
}

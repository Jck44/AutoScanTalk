package com.andreas_kratzer.ghosttalk.domain.auth

import android.content.Intent
import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class PerformManualSyncUseCase @Inject constructor(
    private val googleAuthManager: GoogleAuthManager,
    private val cloudSyncUseCase: CloudSyncUseCase,
    private val settingsRepository: SettingsRepository
) {
    sealed class Result {
        object Success : Result()
        data class NoMatchingBackupFound(val backups: List<RemoteBackupInfo>) : Result()
        data class RecoverableAuth(val intent: Intent) : Result()
        data class Error(val message: String) : Result()
    }

    suspend fun execute(
        mode: SyncMode,
        driveOverride: Drive? = null,
        fileIdOverride: String? = null
    ): Result = withContext(Dispatchers.IO) {
        val credential = googleAuthManager.getGoogleCredential()
        if (credential == null && driveOverride == null) {
            return@withContext Result.Error("Keine Google-Anmeldedaten gefunden.")
        }

        return@withContext try {
            val drive = driveOverride ?: Drive.Builder(
                NetHttpTransport(), 
                GsonFactory.getDefaultInstance(), 
                credential
            ).setApplicationName("GhosTTalk").build()
            
            val success = cloudSyncUseCase.syncBook(drive, settingsRepository.activeBookId, mode, fileIdOverride)
            if (success) {
                if (mode != SyncMode.RESTORE_ONLY) {
                    settingsRepository.lastSuccessfulSyncTime = System.currentTimeMillis()
                }
                Result.Success
            } else if (mode == SyncMode.RESTORE_ONLY && fileIdOverride == null) {
                // If restore failed because no file was found, offer alternatives
                val backups = cloudSyncUseCase.getAvailableBackups(drive)
                if (backups.isNotEmpty()) {
                    Result.NoMatchingBackupFound(backups)
                } else {
                    Result.Error("Keine Backups in der Cloud gefunden.")
                }
            } else {
                Result.Error("Sync fehlgeschlagen.")
            }
        } catch (e: UserRecoverableAuthIOException) {
            Result.RecoverableAuth(e.intent)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unbekannter Fehler beim Sync")
        }
    }
}

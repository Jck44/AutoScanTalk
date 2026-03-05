package com.andreas_kratzer.ghosttalk.domain.auth

import android.content.Intent
import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import javax.inject.Inject

class PerformManualSyncUseCase @Inject constructor(
    private val googleAuthManager: GoogleAuthManager,
    private val cloudSyncUseCase: CloudSyncUseCase,
    private val settingsRepository: SettingsRepository
) {
    sealed class Result {
        object Success : Result()
        data class Error(val message: String) : Result()
        data class RecoverableAuth(val intent: Intent) : Result()
    }

    suspend fun execute(mode: SyncMode, driveOverride: Drive? = null): Result {
        val credential = googleAuthManager.getGoogleCredential()
        if (credential == null && driveOverride == null) {
            return Result.Error("Keine Google-Anmeldedaten gefunden.")
        }

        return try {
            val drive = driveOverride ?: Drive.Builder(
                NetHttpTransport(), 
                GsonFactory.getDefaultInstance(), 
                credential
            ).setApplicationName("GhosTTalk").build()
            
            cloudSyncUseCase.syncBook(drive, settingsRepository.activeBookId, mode)
            settingsRepository.lastSuccessfulSyncTime = System.currentTimeMillis()
            Result.Success
        } catch (e: UserRecoverableAuthIOException) {
            Result.RecoverableAuth(e.intent)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unbekannter Fehler beim Sync")
        }
    }
}

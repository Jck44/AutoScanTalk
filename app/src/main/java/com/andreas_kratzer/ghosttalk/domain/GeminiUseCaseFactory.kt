package com.andreas_kratzer.ghosttalk.domain

import com.andreas_kratzer.ghosttalk.core.cloud.DriveAuthManager
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiUseCaseFactory @Inject constructor(
    private val driveAuthManager: DriveAuthManager
) {
    fun create(oauthTokenProvider: suspend () -> String?): GeminiUseCase {
        return GeminiUseCase(
            oauthTokenProvider = oauthTokenProvider,
            driveProvider = {
                val credential = driveAuthManager.getDriveCredential()
                if (credential == null) {
                    null
                } else {
                    Drive.Builder(
                        NetHttpTransport(),
                        GsonFactory.getDefaultInstance(),
                        credential
                    ).setApplicationName("GhosTTalk").build()
                }
            }
        )
    }
}

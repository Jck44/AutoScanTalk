package com.andreas_kratzer.ghosttalk.domain

import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiUseCaseFactory @Inject constructor(
    private val googleAuthManager: GoogleAuthManager,
    private val logger: Logger
) {
    fun create(oauthTokenProvider: suspend () -> String?): GeminiUseCase {
        return GeminiUseCase(
            oauthTokenProvider = oauthTokenProvider,
            driveProvider = {
                val credential = googleAuthManager.getGoogleCredential()
                if (credential == null) {
                    null
                } else {
                    Drive.Builder(
                        NetHttpTransport(),
                        GsonFactory.getDefaultInstance(),
                        credential
                    ).setApplicationName("GhosTTalk").build()
                }
            },
            logger = logger
        )
    }
}

package com.andreas_kratzer.ghosttalk.domain.genai

import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import javax.inject.Inject

class ActivateGeminiUseCase @Inject constructor(
    private val geminiUseCaseFactory: GeminiUseCaseFactory,
    private val googleAuthManager: GoogleAuthManager,
    private val settingsRepository: SettingsRepository
) {
    suspend fun execute(onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val gemini = geminiUseCaseFactory.create { googleAuthManager.getGoogleCredential()?.getToken() }
        try {
            gemini.generateResponse("Ping")
            settingsRepository.isGeminiEnabled = true
            onSuccess()
        } catch (e: Exception) {
            onError(e)
        }
    }
}

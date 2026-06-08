package com.andreas_kratzer.ghosttalk.core.ai.domain

import com.andreas_kratzer.ghosttalk.core.settings.GenAiSettings
import javax.inject.Inject

class ActivateGeminiUseCase @Inject constructor(
    private val geminiUseCase: GeminiUseCase,
    private val settingsRepository: GenAiSettings
) {
    suspend fun execute(onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        try {
            geminiUseCase.generateResponse("Ping")
            settingsRepository.isGeminiEnabled = true
            settingsRepository.isGeminiVerified = true
            onSuccess()
        } catch (e: Exception) {
            settingsRepository.isGeminiVerified = false
            onError(e)
        }
    }
}

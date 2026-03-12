package com.andreas_kratzer.ghosttalk.domain.genai

import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import javax.inject.Inject

class ActivateGeminiUseCase @Inject constructor(
    private val geminiUseCase: GeminiUseCase,
    private val settingsRepository: SettingsRepository
) {
    suspend fun execute(onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        try {
            geminiUseCase.generateResponse("Ping")
            settingsRepository.isGeminiEnabled = true
            onSuccess()
        } catch (e: Exception) {
            onError(e)
        }
    }
}

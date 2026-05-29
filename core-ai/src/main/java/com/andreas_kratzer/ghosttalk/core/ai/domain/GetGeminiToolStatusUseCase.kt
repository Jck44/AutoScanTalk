package com.andreas_kratzer.ghosttalk.core.ai.domain

import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import javax.inject.Inject

class GetGeminiToolStatusUseCase @Inject constructor(
    private val geminiUseCase: GeminiUseCase,
    private val googleAuthManager: GoogleAuthManager,
    private val settingsRepository: SettingsRepository
) {
    operator fun invoke(): Map<String, GeminiUseCase.ToolStatus> {
        val hasAuth = googleAuthManager.userEmail.value != null || !settingsRepository.geminiApiKey.isNullOrBlank()
        return geminiUseCase.getToolStatus(hasAuth)
    }
}

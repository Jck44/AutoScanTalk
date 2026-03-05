package com.andreas_kratzer.ghosttalk.domain

import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import javax.inject.Inject

class GetGeminiToolStatusUseCase @Inject constructor(
    private val geminiUseCaseFactory: GeminiUseCaseFactory,
    private val googleAuthManager: GoogleAuthManager
) {
    operator fun invoke(): Map<String, GeminiUseCase.ToolStatus> {
        val gemini = geminiUseCaseFactory.create { null }
        return gemini.getToolStatus(googleAuthManager.userEmail.value != null)
    }
}

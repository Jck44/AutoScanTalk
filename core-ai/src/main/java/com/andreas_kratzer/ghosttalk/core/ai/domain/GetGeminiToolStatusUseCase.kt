package com.andreas_kratzer.ghosttalk.core.ai.domain

import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import javax.inject.Inject

class GetGeminiToolStatusUseCase @Inject constructor(
    private val geminiUseCase: GeminiUseCase,
    private val googleAuthManager: GoogleAuthManager
) {
    operator fun invoke(): Map<String, GeminiUseCase.ToolStatus> {
        return geminiUseCase.getToolStatus(googleAuthManager.userEmail.value != null)
    }
}

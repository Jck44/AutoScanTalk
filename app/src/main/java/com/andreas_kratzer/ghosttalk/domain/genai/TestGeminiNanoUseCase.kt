package com.andreas_kratzer.ghosttalk.domain.genai

import com.andreas_kratzer.ghosttalk.domain.executors.LocalIntentRouter
import javax.inject.Inject

class TestGeminiNanoUseCase @Inject constructor(
    private val localIntentRouter: LocalIntentRouter
) {
    suspend fun execute(onResponse: (String) -> Unit, onError: (Exception) -> Unit) {
        try {
            localIntentRouter.routeIntent("Ping") { response ->
                onResponse(response)
            }
        } catch (e: Exception) {
            onError(e)
        }
    }
}

package com.andreas_kratzer.ghosttalk.core.ai.domain

import com.andreas_kratzer.ghosttalk.core.ai.LocalIntentRouter
import javax.inject.Inject

class TestGeminiNanoUseCase @Inject constructor(
    private val localIntentRouter: LocalIntentRouter
) {
    suspend fun execute(onResponse: (String) -> Unit, onError: (Exception) -> Unit) {
        try {
            localIntentRouter.routeIntent { response ->
                onResponse(response)
            }
        } catch (e: Exception) {
            onError(e)
        }
    }
}

package com.andreas_kratzer.ghosttalk.core.ai

interface LocalIntentRouter {
    suspend fun generateRawResponse(prompt: String, maxTokens: Int = 100): String
    suspend fun routeIntent(onSpeak: (String) -> Unit)
    suspend fun executeIntent(intentId: String, onSpeak: (String) -> Unit)
}

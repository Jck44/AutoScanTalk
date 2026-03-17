package com.andreas_kratzer.ghosttalk.core.ai.domain

import com.andreas_kratzer.ghosttalk.core.ai.ClockExecutor
import com.andreas_kratzer.ghosttalk.core.ai.LocalIntentRouter
import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.TextPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class LocalIntent(val id: String, val description: String)

class LocalIntentRouterImpl @Inject constructor(
    private val logger: Logger
) : LocalIntentRouter {

    override suspend fun generateRawResponse(prompt: String, maxTokens: Int): String = withContext(Dispatchers.IO) {
        try {
            val model = Generation.getClient()
            val textPart = TextPart(prompt)
            val builder = GenerateContentRequest.builder(textPart)
            builder.maxOutputTokens = maxTokens
            builder.temperature = 0.0f
            val request = builder.build()
            
            val response = model.generateContent(request)
            return@withContext response.candidates.firstOrNull()?.text ?: ""
        } catch (e: Exception) {
            logger.e("LocalIntentRouter", "Raw generation failed", e)
            ""
        }
    }

    override suspend fun executeIntent(intentId: String, onSpeak: (String) -> Unit) = withContext(Dispatchers.IO) {
        // Gemini Nano currently has no external tool access.
        // It should only be used for raw text generation or future vision tasks.
        onSpeak("Dieses Tool ist für die lokale Verarbeitung aktuell nicht verfügbar.")
    }

    override suspend fun routeIntent(onSpeak: (String) -> Unit) = withContext(Dispatchers.IO) {
        onSpeak("Befehl konnte nicht verarbeitet werden.")
    }
}

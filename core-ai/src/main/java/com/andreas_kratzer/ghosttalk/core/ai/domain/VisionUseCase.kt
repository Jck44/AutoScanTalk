package com.andreas_kratzer.ghosttalk.core.ai.domain

import android.graphics.Bitmap
import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.TextPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VisionUseCase @Inject constructor(
    private val geminiUseCase: GeminiUseCase,
    private val logger: Logger
) {
    private val TAG = "VisionUseCase"

    suspend fun describeImage(bitmap: Bitmap, prompt: String, useCloud: Boolean): String {
        return if (useCloud) {
            describeCloud(bitmap, prompt)
        } else {
            describeImageLocally(bitmap, prompt)
        }
    }

    /**
     * Describes an image using Gemini Nano (Local).
     * Focuses on privacy and speed.
     */
    suspend fun describeImageLocally(bitmap: Bitmap, prompt: String = "Beschreibe kurz was du auf diesem Bild siehst."): String = withContext(Dispatchers.IO) {
        try {
            val model = Generation.getClient()
            val textPart = TextPart(prompt)
            val imagePart = ImagePart(bitmap)
            val request = GenerateContentRequest.builder(imagePart, textPart).build()
            
            val response = model.generateContent(request)
            return@withContext response.candidates.firstOrNull()?.text ?: "Keine lokale Beschreibung möglich."
        } catch (e: Exception) {
            logger.e(TAG, "Local vision failed", e)
            "Lokale Bilderkennung fehlgeschlagen: ${e.message}"
        }
    }

    /**
     * Describes an image using Gemini Cloud.
     * Higher intelligence, requires internet.
     */
    private suspend fun describeCloud(bitmap: Bitmap, prompt: String): String {
        return geminiUseCase.generateResponse(
            prompt = prompt,
            image = bitmap,
            useGoogleSearch = false
        )
    }
}

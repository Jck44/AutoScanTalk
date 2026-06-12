package com.andreas_kratzer.ghosttalk.core.ai.domain

import android.graphics.Bitmap
import com.andreas_kratzer.ghosttalk.core.util.Logger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VisionUseCase @Inject constructor(
    private val geminiUseCase: GeminiUseCase,
    private val logger: Logger
) {
    suspend fun describeImage(bitmap: Bitmap, prompt: String): String {
        return describeCloud(bitmap, prompt)
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

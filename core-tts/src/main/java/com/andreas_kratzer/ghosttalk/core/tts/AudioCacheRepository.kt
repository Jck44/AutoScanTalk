package com.andreas_kratzer.ghosttalk.core.tts

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class CachedAudioItem(
    val file: File,
    val text: String,
    val voiceId: String,
    val modelId: String,
    val sizeBytes: Long
)

@Singleton
class AudioCacheRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun getCachedAudios(): List<CachedAudioItem> = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "elevenlabs")
        if (!cacheDir.exists()) return@withContext emptyList()

        val files = cacheDir.listFiles() ?: return@withContext emptyList()
        files.mapNotNull { file ->
            try {
                val name = file.nameWithoutExtension
                if (!name.startsWith("tts_eleven#")) return@mapNotNull null

                val parts = name.split("#")
                if (parts.size < 4) return@mapNotNull null

                val encodedText = parts[1].substringBefore("-") // remove hash suffix if present
                val decodedBytes = Base64.decode(encodedText, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
                val text = String(decodedBytes, Charsets.UTF_8).let { if (parts[1].contains("-")) "$it..." else it }

                CachedAudioItem(
                    file = file,
                    text = text,
                    voiceId = parts[2],
                    modelId = parts[3],
                    sizeBytes = file.length()
                )
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.text }
    }

    suspend fun deleteFile(file: File): Boolean = withContext(Dispatchers.IO) {
        if (file.exists()) file.delete() else false
    }

    suspend fun deleteAll(): Boolean = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "elevenlabs")
        if (cacheDir.exists()) {
            cacheDir.listFiles()?.forEach { it.delete() }
            true
        } else false
    }
}

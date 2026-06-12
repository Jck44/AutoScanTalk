package com.andreas_kratzer.ghosttalk.core.data.impl

import android.content.Context
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.VocalProfileRepository
import com.andreas_kratzer.ghosttalk.core.data.export.ImportResult
import com.andreas_kratzer.ghosttalk.core.data.export.PageImportExportProvider
import com.andreas_kratzer.ghosttalk.core.data.impl.importexport.BookConfigImportExport
import com.andreas_kratzer.ghosttalk.core.data.impl.importexport.BookJsonExporter
import com.andreas_kratzer.ghosttalk.core.data.impl.importexport.BookJsonImporter
import com.andreas_kratzer.ghosttalk.core.data.impl.importexport.ImportExportJson
import com.andreas_kratzer.ghosttalk.core.data.impl.importexport.MediaArchiveSync
import com.andreas_kratzer.ghosttalk.core.data.impl.importexport.StatisticsImportExport
import com.andreas_kratzer.ghosttalk.core.data.impl.importexport.extractCloudBookId
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.importexport.ImportExportData
import com.andreas_kratzer.ghosttalk.core.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PageImportExportManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val settingsRepository: SettingsRepository,
    private val vocalProfileRepository: VocalProfileRepository,
    private val zipArchiver: ZipArchiver,
    private val mediaArchiveSync: MediaArchiveSync,
    private val statisticsImportExport: StatisticsImportExport,
    private val bookConfigImportExport: BookConfigImportExport,
    private val bookJsonExporter: BookJsonExporter,
    private val bookJsonImporter: BookJsonImporter,
    private val logger: Logger
) : PageImportExportProvider {
    private val TAG = "PageImportExportManager"
    override suspend fun exportPageListToJson(pages: List<Page>): String {
        return bookJsonExporter.exportPageListToJson(pages)
    }

    override suspend fun exportBookToJson(bookId: String, includeSettings: Boolean): String {
        return bookJsonExporter.exportBookToJson(bookId, includeSettings)
    }

    override suspend fun importFromJson(
        json: String,
        bookId: String,
        regenerateIds: Boolean,
        restoreSyncSettings: Boolean
    ): Result<ImportResult> {
        return bookJsonImporter.importFromJson(json, bookId, regenerateIds, restoreSyncSettings)
    }

    override suspend fun extractBookIdFromJson(jsonString: String): String? = withContext(Dispatchers.Default) {
        try {
            val root = ImportExportJson.parseToJsonElement(jsonString) as? JsonObject
            root?.get("bookId")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun extractBookNameFromJson(jsonString: String): String? = withContext(Dispatchers.Default) {
        try {
            val root = ImportExportJson.parseToJsonElement(jsonString) as? JsonObject
            root?.get("bookName")?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun importCloudBackup(jsonString: String, cloudFileId: String?): Result<String> = withContext(Dispatchers.IO) {
        try {
            val importData = ImportExportJson.decodeFromString<ImportExportData>(jsonString)

            val targetBookId = extractCloudBookId(importData.bookId, importData.bookName, cloudFileId)
            
            if (targetBookId == null) {
                return@withContext Result.failure(Exception("Konnte keine Buch-ID im Backup finden."))
            }
            
            val existingBook = bookRepository.getBookById(targetBookId)
            if (existingBook != null) {
                return@withContext Result.failure(Exception("Ein Buch mit der ID '$targetBookId' existiert bereits lokal. Import abgebrochen, um Überschreiben zu verhindern."))
            }

            val newBook = com.andreas_kratzer.ghosttalk.core.model.Book(
                id = targetBookId,
                name = importData.bookName ?: "Importiertes Buch",
                createdAt = importData.bookCreatedAt ?: System.currentTimeMillis(),
                updatedAt = importData.bookUpdatedAt ?: importData.bookCreatedAt ?: System.currentTimeMillis(),
                versionSequence = importData.versionSequence ?: 0L
            )
            bookRepository.insertBook(newBook)

            importFromJson(jsonString, targetBookId, regenerateIds = false)
            Result.success(targetBookId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun exportBookToZip(
        bookId: String, 
        outputStream: OutputStream,
        includeTtsCache: Boolean,
        onProgress: (Float, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        onProgress(0.05f, "Exporting database...")
        val jsonContent = exportBookToJson(bookId)
        val stringEntries = mutableMapOf<String, String>()
        stringEntries["backup.json"] = jsonContent
        
        try {
            val profiles = vocalProfileRepository.getAllProfilesFlow().first()
            val profilesJson = ImportExportJson.encodeToString(profiles)
            stringEntries["vocal_profiles.json"] = profilesJson
            logger.d(TAG, "Exported ${profiles.size} vocal profiles to ZIP")
        } catch (e: Exception) {
            logger.e(TAG, "Failed to export vocal profiles to ZIP", e)
        }

        onProgress(0.1f, "Database exported.")

        val fileEntries = mutableListOf<Pair<File, String>>()
        if (includeTtsCache) {
            val cacheDir = File(context.filesDir, "elevenlabs")
            if (cacheDir.exists() && cacheDir.isDirectory) {
                cacheDir.listFiles()?.filter { it.isFile && it.name.endsWith(".mp3") }?.forEach { file ->
                    fileEntries.add(file to "tts_cache/${file.name}")
                }
            }
        }
        
        val audioDir = File(context.filesDir, "audio_recordings")
        if (audioDir.exists() && audioDir.isDirectory) {
            audioDir.listFiles()?.filter { it.isFile && it.name.endsWith(".ogg") }?.forEach { file ->
                fileEntries.add(file to "audio_recordings/${file.name}")
            }
        }

        zipArchiver.zip(outputStream, stringEntries, fileEntries, onProgress)
    }

    override suspend fun importFromZip(
        inputStream: InputStream,
        bookId: String,
        regenerateIds: Boolean,
        restoreSyncSettings: Boolean,
        onProgress: (Float, String) -> Unit
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var jsonContent: String? = null
            var vocalProfilesJson: String? = null
            
            val ttsCacheDir = File(context.filesDir, "elevenlabs")
            if (!ttsCacheDir.exists()) ttsCacheDir.mkdirs()

            val audioDir = File(context.filesDir, "audio_recordings")
            if (!audioDir.exists()) audioDir.mkdirs()

            val targetDirs = mapOf(
                "tts_cache/" to ttsCacheDir,
                "audio_recordings/" to audioDir
            )

            val handler = object : ZipArchiver.UnzipHandler {
                override fun handleStringEntry(name: String, content: String) {
                    if (name == "backup.json") {
                        jsonContent = content
                    } else if (name == "vocal_profiles.json") {
                        vocalProfilesJson = content
                    } else if (name == "statistics.json") {
                        val statsMode = settingsRepository.syncModeStats
                        if (statsMode == "RESTORE_ONLY") {
                            kotlinx.coroutines.runBlocking {
                                statisticsImportExport.importStatisticsFromJson(content, bookId)
                            }
                        }
                    }
                }

                override fun handleFileEntry(name: String, time: Long, inputStream: InputStream, targetFile: File) {
                    FileOutputStream(targetFile).use { out ->
                        inputStream.copyTo(out)
                    }
                    if (time != -1L) {
                        targetFile.setLastModified(time)
                    }
                }
            }

            zipArchiver.unzip(inputStream, targetDirs, handler, onProgress)

            if (jsonContent == null) {
                return@withContext Result.failure(Exception("Keine backup.json im ZIP gefunden."))
            }

            onProgress(0.9f, "Importing data...")
            val result = importFromJson(jsonContent, bookId, regenerateIds, restoreSyncSettings)
            
            vocalProfilesJson?.let {
                try {
                    val profiles = ImportExportJson.decodeFromString<List<com.andreas_kratzer.ghosttalk.core.model.VocalProfile>>(it)
                    profiles.forEach { profile ->
                        vocalProfileRepository.saveProfile(profile)
                    }
                    logger.d(TAG, "Imported ${profiles.size} vocal profiles from ZIP")
                } catch (e: Exception) {
                    logger.e(TAG, "Failed to import vocal profiles from ZIP", e)
                }
            }

            onProgress(1.0f, "Import complete.")
            result.map { it.pageCount }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getTtsCacheLastModified(): Long {
        return mediaArchiveSync.getLastModified("elevenlabs", ".mp3")
    }

    override suspend fun exportTtsCacheToZip(
        outputStream: OutputStream,
        onProgress: (Float, String) -> Unit
    ) {
        mediaArchiveSync.exportToZip("elevenlabs", "tts_cache", ".mp3", outputStream, onProgress)
    }

    override suspend fun importTtsCacheFromZip(
        inputStream: InputStream,
        onProgress: (Float, String) -> Unit
    ) {
        mediaArchiveSync.importFromZip("elevenlabs", "tts_cache", inputStream, onProgress)
    }

    override suspend fun importCloudBackupFromZip(
        inputStream: InputStream,
        cloudFileId: String?,
        onProgress: (Float, String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            var jsonContent: String? = null
            
            val ttsCacheDir = File(context.filesDir, "elevenlabs")
            if (!ttsCacheDir.exists()) ttsCacheDir.mkdirs()

            val audioDir = File(context.filesDir, "audio_recordings")
            if (!audioDir.exists()) audioDir.mkdirs()

            val targetDirs = mapOf(
                "tts_cache/" to ttsCacheDir,
                "audio_recordings/" to audioDir
            )

            val handler = object : ZipArchiver.UnzipHandler {
                override fun handleStringEntry(name: String, content: String) {
                    if (name == "backup.json") {
                        jsonContent = content
                    }
                }

                override fun handleFileEntry(name: String, time: Long, inputStream: InputStream, targetFile: File) {
                    FileOutputStream(targetFile).use { out ->
                        inputStream.copyTo(out)
                    }
                    if (time != -1L) {
                        targetFile.setLastModified(time)
                    }
                }
            }

            zipArchiver.unzip(inputStream, targetDirs, handler, onProgress)

            if (jsonContent == null) {
                return@withContext Result.failure(Exception("Keine backup.json im ZIP gefunden."))
            }

            onProgress(0.9f, "Importing book...")
            val result = importCloudBackup(jsonContent, cloudFileId)
            onProgress(1.0f, "Import complete.")
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun exportStatisticsToZip(
        bookId: String,
        outputStream: OutputStream
    ) {
        statisticsImportExport.exportStatisticsToZip(bookId, outputStream)
    }

    override suspend fun importStatisticsFromZip(
        bookId: String,
        inputStream: InputStream
    ) {
        statisticsImportExport.importStatisticsFromZip(bookId, inputStream)
    }

    override suspend fun getStatisticsLastModified(bookId: String): Long {
        return statisticsImportExport.getStatisticsLastModified(bookId)
    }

    override suspend fun exportAudioRecordingsToZip(
        outputStream: OutputStream,
        onProgress: (Float, String) -> Unit
    ) {
        mediaArchiveSync.exportToZip("audio_recordings", "audio_recordings", ".ogg", outputStream, onProgress)
    }

    override suspend fun importAudioRecordingsFromZip(
        inputStream: InputStream,
        onProgress: (Float, String) -> Unit
    ) {
        mediaArchiveSync.importFromZip("audio_recordings", "audio_recordings", inputStream, onProgress)
    }

    override fun getAudioRecordingsLastModified(): Long {
        return mediaArchiveSync.getLastModified("audio_recordings", ".ogg")
    }

    override fun exportBookConfigToJson(bookId: String): String {
        return bookConfigImportExport.exportBookConfigToJson(bookId)
    }

    override fun importBookConfigFromJson(jsonString: String, bookId: String): Result<Unit> {
        return bookConfigImportExport.importBookConfigFromJson(jsonString, bookId)
    }

    override fun getBookConfigLastModified(bookId: String): Long {
        return bookConfigImportExport.getBookConfigLastModified(bookId)
    }
}


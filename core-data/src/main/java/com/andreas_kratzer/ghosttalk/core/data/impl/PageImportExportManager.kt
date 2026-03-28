package com.andreas_kratzer.ghosttalk.core.data.impl

import android.content.Context
import androidx.core.content.edit
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.export.PageImportExportProvider
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsMapper
import com.andreas_kratzer.ghosttalk.core.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.core.model.importexport.ImportButton
import com.andreas_kratzer.ghosttalk.core.model.importexport.ImportExportData
import com.andreas_kratzer.ghosttalk.core.model.importexport.ImportPage
import com.andreas_kratzer.ghosttalk.core.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PageImportExportManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val pageRepository: PageRepository,
    private val bookRepository: BookRepository,
    private val settingsRepository: SettingsRepository,
    private val settingsMapper: SettingsMapper,
    private val actionMapper: ActionMapper,
    private val logger: Logger
) : PageImportExportProvider {
    private val TAG = "PageImportExportManager"
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    suspend fun exportPageListToJson(pages: List<Page>): String = withContext(Dispatchers.IO) {
        val exportData = ImportExportData(
            bookId = pages.firstOrNull()?.bookId ?: "unknown",
            bookName = "Exportierte Seiten",
            holdingTimeSeconds = settingsRepository.holdingTimeMillis / 1000f,
            pages = pages.map { page ->
                ImportPage(
                    importId = page.id,
                    name = page.name,
                    templateId = page.templateId,
                    rows = page.rows,
                    columns = page.columns,
                    scanPattern = page.scanPattern,
                    rowNames = page.rowNames,
                    orderIndex = page.orderIndex,
                    createdAt = page.createdAt,
                    buttons = page.buttonConfigs.mapIndexed { index, config ->
                        ImportButton(
                            id = config?.id,
                            index = index.toLong(),
                            label = config?.label ?: "",
                            spokenText = config?.spokenText,
                            auditoryCueText = config?.auditoryCue?.let { if (it is AuditoryCue.TextToSpeechCue) it.text else "" },
                            active = config?.isActive,
                            playActionAsAuditoryCue = config?.playActionAsAuditoryCue,
                            action = config?.buttonAction?.let { actionMapper.exportAction(it, config.spokenText) }
                        )
                    }.filter { it.label.isNotEmpty() || it.action != null || it.auditoryCueText != null }
                )
            }
        )
        json.encodeToString(exportData)
    }

    override suspend fun exportBookToJson(bookId: String): String = withContext(Dispatchers.IO) {
        val book = bookRepository.getBookById(bookId) ?: throw Exception("Book not found")
        val pages = pageRepository.getPagesForBook(bookId)
        
        val baseExportData = ImportExportData(
            bookId = book.id,
            bookName = book.name,
            bookCreatedAt = book.createdAt,
            bookUpdatedAt = book.updatedAt,
            pages = pages.map { page ->
                ImportPage(
                    importId = page.id,
                    name = page.name,
                    templateId = page.templateId,
                    rows = page.rows,
                    columns = page.columns,
                    scanPattern = page.scanPattern,
                    rowNames = page.rowNames,
                    orderIndex = page.orderIndex,
                    createdAt = page.createdAt,
                    buttons = page.buttonConfigs.mapIndexedNotNull { index, config ->
                        config?.let {
                            ImportButton(
                                index = index.toLong(),
                                id = it.id,
                                label = it.label,
                                spokenText = it.spokenText,
                                auditoryCueText = (it.auditoryCue as? AuditoryCue.TextToSpeechCue)?.text,
                                active = it.isActive,
                                playActionAsAuditoryCue = it.playActionAsAuditoryCue,
                                action = actionMapper.exportAction(it.buttonAction)
                            )
                        }
                    }
                )
            }
        )

        val exportData = settingsMapper.exportSettings(bookId, baseExportData)

        logger.d(TAG, "Exported book $bookId: defaultStartPageId='${exportData.defaultStartPageId}', scanDelay='${exportData.scanDelayMillis}'")
        json.encodeToString(exportData)
    }

    override suspend fun importFromJson(
        jsonString: String,
        bookId: String,
        regenerateIds: Boolean,
        restoreSyncSettings: Boolean
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val importData = json.decodeFromString<ImportExportData>(jsonString)
            logger.d(TAG, "Importing JSON for book $bookId: defaultStartPageId='${importData.defaultStartPageId}', bookName='${importData.bookName}'")

            // 1. Clean state: Delete existing pages for this book before importing
            // This ensures that the restored book exactly matches the backup
            pageRepository.deletePagesForBook(bookId)
            logger.d(TAG, "Cleared existing pages for book $bookId before import.")

            // 2. Update book and app settings if provided
            if (restoreSyncSettings) {
                settingsMapper.importSettings(importData)
            }
            importData.bookName?.let { newName ->
                bookRepository.getBookById(bookId)?.let { book ->
                    bookRepository.updateBook(book.copy(
                        name = newName,
                        createdAt = importData.bookCreatedAt ?: book.createdAt,
                        actionLogLimit = importData.actionLogLimit ?: book.actionLogLimit,
                        limitScanCycles = importData.limitScanCycles ?: book.limitScanCycles,
                        scanCycleLimit = importData.scanCycleLimit ?: book.scanCycleLimit,
                        logIgnoredActions = importData.logIgnoredActions ?: book.logIgnoredActions,
                        logStopActions = importData.logStopActions ?: book.logStopActions
                    ))
                }
            }
            
            // 1.1 Update book updated timestamp if provided from import data (e.g. from cloud)
            importData.bookUpdatedAt?.let { timestamp ->
                bookRepository.updateLastModified(bookId, timestamp)
            }

            val idMap = mutableMapOf<String, String>()
            
            // 2. Determine ID regeneration needs
            val sourceBookId = importData.bookId?.takeIf { it.isNotBlank() }
            val forceRegeneration = regenerateIds || (sourceBookId != null && sourceBookId.lowercase() != bookId.lowercase())

            val regeneratedPages = mutableSetOf<String>()
            importData.pages.forEach { importPage ->
                var targetPageId = importPage.importId
                val existingPage = pageRepository.getPageById(targetPageId)
                
                if (forceRegeneration || (existingPage != null && existingPage.bookId != bookId)) {
                    targetPageId = UUID.randomUUID().toString()
                    regeneratedPages.add(importPage.importId)
                }
                idMap[importPage.importId] = targetPageId
            }
            logger.d(TAG, "ID mapping complete. Mapping size: ${idMap.size}. ForceRegeneration: $forceRegeneration")

            // 2.1 Update book-scoped settings with correct book ID prefix
            val prefs = context.getSharedPreferences(SettingsConstants.PREFS_NAME, android.content.Context.MODE_PRIVATE)
            prefs.edit {
                importData.defaultStartPageId?.let { oldId ->
                    val newId = idMap[oldId] ?: oldId
                    val key = "${bookId}_${SettingsConstants.KEY_DEFAULT_START_PAGE_ID}"
                    logger.d(TAG, "Restoring defaultStartPageId: Import JSON had '$oldId'. idMap result: '$newId'. Saving to key: '$key'")
                    putString(key, newId)
                }
                importData.pageSortOrder?.let { 
                    val key = "${bookId}_${SettingsConstants.KEY_PAGE_SORT_ORDER}"
                    logger.d(TAG, "Restoring pageSortOrder: '$it' for key: '$key'")
                    putString(key, it)
                }
                importData.templateSortOrder?.let { 
                    val key = "${bookId}_${SettingsConstants.KEY_TEMPLATE_SORT_ORDER}"
                    logger.d(TAG, "Restoring templateSortOrder: '$it' for key: '$key'")
                    putString(key, it)
                }
            }

            // 2.2 Update favorite book setting if it was the imported book
            importData.favoriteBookId?.let { oldFavId ->
                if (oldFavId == importData.bookId) {
                    settingsRepository.favoriteBookId = bookId
                }
            }

            importData.pages.forEach { importPage ->
                val newPageId = idMap[importPage.importId]!!
                
                // Spatial mapping to 49 (7x7) buttons
                val buttons = MutableList<ButtonConfig?>(49) { null }
                
                importPage.buttons.forEach { importButton ->
                    val action = importButton.action?.let { actionMapper.importAction(it, idMap) }
                    
                    if (importButton.label.isEmpty() && action == null && importButton.auditoryCueText == null) {
                        return@forEach
                    }

                    val finalAction = action ?: SpeakTextButtonAction()
                    val pageRegenerated = regeneratedPages.contains(importPage.importId)
                    val config = ButtonConfig(
                        id = if (forceRegeneration || pageRegenerated) UUID.randomUUID().toString() else (importButton.id ?: UUID.randomUUID().toString()),
                        label = importButton.label,
                        spokenText = importButton.spokenText ?: importButton.action?.textToSpeech,
                        auditoryCue = importButton.auditoryCueText?.let { AuditoryCue.TextToSpeechCue(it) },
                        isActive = importButton.active ?: true,
                        playActionAsAuditoryCue = importButton.playActionAsAuditoryCue ?: false,
                        buttonAction = finalAction
                    )
                    
                    // Spatial logic: map index from source columns to 7 columns
                    val sourceCols = importPage.columns.coerceAtLeast(1)
                    val row = (importButton.index / sourceCols).toInt()
                    val col = (importButton.index % sourceCols).toInt()
                    
                    val globalIndex = row * 7 + col
                    if (globalIndex < buttons.size) {
                        buttons[globalIndex] = config
                    }
                }

                // Auto-expand rows/columns if buttons exceed metadata (Spatial matching)
                var finalRows = importPage.rows
                var finalCols = importPage.columns
                importPage.buttons.forEach { 
                    val maxIndex = it.index.toInt()
                    while (finalRows < 7 && finalRows * finalCols <= maxIndex) {
                        if (finalCols < 7) finalCols++ else finalRows++
                    }
                }

                val page = Page(
                    id = newPageId,
                    bookId = bookId,
                    name = importPage.name,
                    templateId = importPage.templateId,
                    rows = finalRows.coerceIn(1, 7),
                    columns = finalCols.coerceIn(1, 7),
                    scanPattern = importPage.scanPattern ?: "linear",
                    rowNames = importPage.rowNames ?: emptyList(),
                    buttonConfigs = buttons,
                    orderIndex = importPage.orderIndex ?: 0,
                    createdAt = importPage.createdAt ?: System.currentTimeMillis()
                )
                pageRepository.insertPage(page)
            }
            
            // 4. Force refresh of settings flows to ensure UI is updated
            settingsRepository.refresh()
            logger.d(TAG, "Triggered settingsRepository.refresh() after import.")

            Result.success(importData.pages.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun extractBookIdFromJson(jsonString: String): String? = withContext(Dispatchers.Default) {
        try {
            val root = json.parseToJsonElement(jsonString) as? JsonObject
            root?.get("bookId")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun extractBookNameFromJson(jsonString: String): String? = withContext(Dispatchers.Default) {
        try {
            val root = json.parseToJsonElement(jsonString) as? JsonObject
            root?.get("bookName")?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun importCloudBackup(jsonString: String, cloudFileId: String?): Result<String> = withContext(Dispatchers.IO) {
        try {
            val importData = json.decodeFromString<ImportExportData>(jsonString)

            // 1. Update book and app settings if provided
            // Extract bookId from name if missing from field (e.g. "Name [uuid]")
            val extractedId = importData.bookId?.takeIf { it.isNotBlank() } ?: run {
                val name = importData.bookName ?: ""
                val regex = "\\[([a-fA-F0-9-]{36})]".toRegex()
                regex.find(name)?.groupValues?.get(1)
            }

            val targetBookId = extractedId ?: run {
                val uuidRegex = "[a-fA-F0-9-]{36}".toRegex()
                cloudFileId?.let { 
                    uuidRegex.find(it)?.value ?: it.removePrefix("book_").removeSuffix(".json")
                }
            }?.trim()?.lowercase()
            
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
                createdAt = System.currentTimeMillis()
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
        onProgress: (Float, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        ZipOutputStream(outputStream).use { zip ->
            // 1. Write the backup.json (0-10%)
            onProgress(0.05f, "Exporting database...")
            val jsonContent = exportBookToJson(bookId)
            zip.putNextEntry(ZipEntry("backup.json"))
            zip.write(jsonContent.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            onProgress(0.1f, "Database exported.")

            // 2. Write the elevenlabs cache files (10-100%)
            val cacheDir = File(context.filesDir, "elevenlabs")
            if (cacheDir.exists() && cacheDir.isDirectory) {
                val files = cacheDir.listFiles()?.filter { it.isFile && it.name.endsWith(".mp3") } ?: emptyList()
                val totalFiles = files.size
                files.forEachIndexed { index, file ->
                    val fileProgress = 0.1f + (index.toFloat() / totalFiles) * 0.9f
                    onProgress(fileProgress, "Compressing audio: ${file.name}")
                    zip.putNextEntry(ZipEntry("tts_cache/${file.name}"))
                    file.inputStream().use { input ->
                        input.copyTo(zip)
                    }
                    zip.closeEntry()
                }
            }
            onProgress(1f, "Backup complete.")
        }
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
            val zipIn = ZipInputStream(inputStream)
            
            val ttsCacheDir = File(context.filesDir, "elevenlabs")
            if (!ttsCacheDir.exists()) ttsCacheDir.mkdirs()

            // Since we don't know the number of entries in advance easily with ZipInputStream
            // We'll just report progress based on entries processed if we can, 
            // but usually we'd need a ZipFile for that.
            // Let's at least report filenames.
            
            var entry = zipIn.nextEntry
            var entriesProcessed = 0
            while (entry != null) {
                entriesProcessed++
                onProgress(0.1f, "Extracting: ${entry.name}") // Qualitative progress
                
                if (entry.name == "backup.json") {
                    val bytes = zipIn.readBytes()
                    jsonContent = String(bytes, Charsets.UTF_8)
                } else if (entry.name.startsWith("tts_cache/")) {
                    val fileName = entry.name.substringAfter("tts_cache/")
                    if (fileName.isNotEmpty()) {
                        val targetFile = File(ttsCacheDir, fileName)
                        val out = FileOutputStream(targetFile)
                        try {
                            zipIn.copyTo(out)
                        } finally {
                            out.close()
                        }
                    }
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }

            if (jsonContent == null) {
                return@withContext Result.failure(Exception("Keine backup.json im ZIP gefunden."))
            }

            onProgress(0.9f, "Importing data...")
            val result = importFromJson(jsonContent, bookId, regenerateIds, restoreSyncSettings)
            onProgress(1.0f, "Import complete.")
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun importCloudBackupFromZip(
        inputStream: InputStream,
        cloudFileId: String?,
        onProgress: (Float, String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            var jsonContent: String? = null
            val zipIn = ZipInputStream(inputStream)
            
            val ttsCacheDir = File(context.filesDir, "elevenlabs")
            if (!ttsCacheDir.exists()) ttsCacheDir.mkdirs()

            var entry = zipIn.nextEntry
            while (entry != null) {
                onProgress(0.1f, "Extracting: ${entry.name}")
                if (entry.name == "backup.json") {
                    val bytes = zipIn.readBytes()
                    jsonContent = String(bytes, Charsets.UTF_8)
                } else if (entry.name.startsWith("tts_cache/")) {
                    val fileName = entry.name.substringAfter("tts_cache/")
                    if (fileName.isNotEmpty()) {
                        val targetFile = File(ttsCacheDir, fileName)
                        FileOutputStream(targetFile).use { out ->
                            zipIn.copyTo(out)
                        }
                    }
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }

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
}

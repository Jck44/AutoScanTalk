package com.andreas_kratzer.ghosttalk.core.data.impl

import android.content.Context
import androidx.core.content.edit
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.ButtonTemplateRepository
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.UserModeSessionRepository
import com.andreas_kratzer.ghosttalk.core.data.VocalProfileRepository
import com.andreas_kratzer.ghosttalk.core.data.export.PageImportExportProvider
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsMapper
import com.andreas_kratzer.ghosttalk.core.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.core.model.importexport.ExportedButtonStat
import com.andreas_kratzer.ghosttalk.core.model.importexport.ExportedHistoryEvent
import com.andreas_kratzer.ghosttalk.core.model.importexport.ExportedStatistics
import com.andreas_kratzer.ghosttalk.core.model.importexport.ExportedTombstone
import com.andreas_kratzer.ghosttalk.core.model.importexport.ExportedUserModeSession
import com.andreas_kratzer.ghosttalk.core.model.importexport.ImportButton
import com.andreas_kratzer.ghosttalk.core.model.importexport.ImportButtonTemplate
import com.andreas_kratzer.ghosttalk.core.model.importexport.ImportExportData
import com.andreas_kratzer.ghosttalk.core.model.importexport.ImportPage
import com.andreas_kratzer.ghosttalk.core.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("UNUSED_PARAMETER")
@Singleton
class PageImportExportManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val pageRepository: PageRepository,
    private val bookRepository: BookRepository,
    private val settingsRepository: SettingsRepository,
    private val settingsMapper: SettingsMapper,
    private val actionMapper: ActionMapper,
    private val buttonTemplateRepository: ButtonTemplateRepository,
    private val buttonUsageDao: com.andreas_kratzer.ghosttalk.core.database.ButtonUsageDao,
    private val userModeSessionRepository: UserModeSessionRepository,
    private val vocalProfileRepository: VocalProfileRepository,
    private val deletedEntityDao: com.andreas_kratzer.ghosttalk.core.database.DeletedEntityDao,
    private val zipArchiver: ZipArchiver,
    private val logger: Logger
) : PageImportExportProvider {
    private val TAG = "PageImportExportManager"
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    override suspend fun exportPageListToJson(pages: List<Page>): String = withContext(Dispatchers.IO) {
        val exportData = ImportExportData(
            ghosttalk_import_version = "1.1",
            appName = "GhostTalk",
            bookId = pages.firstOrNull()?.bookId ?: "unknown",
            bookName = "Exportierte Seiten",
            holdingTimeSeconds = settingsRepository.holdingTimeMillis / 1000f,
            sourceDevice = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
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
                            spokenTextMode = config?.spokenTextMode?.name,
                            audioFileName = config?.audioFileName,
                            auditoryCueText = config?.auditoryCue?.let { if (it is AuditoryCue.TextToSpeechCue) it.text else "" },
                            active = config?.isActive,
                            playActionAsAuditoryCue = config?.playActionAsAuditoryCue,
                            action = config?.buttonAction?.let { actionMapper.exportAction(it, config.spokenText) }
                        )
                    }.filter { it.label.isNotEmpty() || it.action != null || it.auditoryCueText != null || it.audioFileName != null }
                )
            }
        )
        json.encodeToString(exportData)
    }

    override suspend fun exportBookToJson(bookId: String, includeSettings: Boolean): String = withContext(Dispatchers.IO) {
        val book = bookRepository.getBookById(bookId) ?: throw Exception("Book not found")
        val pages = pageRepository.getPagesForBook(bookId)
        
        val buttonTemplatesList = buttonTemplateRepository.getTemplates().first()
        val mappedButtonTemplates = buttonTemplatesList.map { template ->
            ImportButtonTemplate(
                id = template.id,
                name = template.name,
                isBuiltIn = template.isBuiltIn,
                orderIndex = template.orderIndex,
                button = ImportButton(
                    id = template.buttonConfig.id,
                    index = 0,
                    label = template.buttonConfig.label,
                    spokenText = template.buttonConfig.spokenText,
                    spokenTextMode = template.buttonConfig.spokenTextMode.name,
                    audioFileName = template.buttonConfig.audioFileName,
                    auditoryCueText = (template.buttonConfig.auditoryCue as? AuditoryCue.TextToSpeechCue)?.text,
                    active = template.buttonConfig.isActive,
                    playActionAsAuditoryCue = template.buttonConfig.playActionAsAuditoryCue,
                    action = actionMapper.exportAction(template.buttonConfig.buttonAction)
                )
            )
        }
        
        // Prune database tombstones older than 90 days
        val cutoff = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
        deletedEntityDao.pruneTombstones(cutoff)

        val tombstones = deletedEntityDao.getDeletedEntitiesForBook(bookId).map {
            ExportedTombstone(
                entityId = it.entityId,
                entityType = it.entityType,
                deletedAt = it.deletedAt
            )
        }

        val baseExportData = ImportExportData(
            ghosttalk_import_version = "1.1",
            appName = "GhostTalk",
            bookId = book.id,
            bookName = book.name,
            bookCreatedAt = book.createdAt,
            bookUpdatedAt = book.updatedAt,
            versionSequence = book.versionSequence,
            sourceDevice = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
            buttonTemplates = mappedButtonTemplates.sortedBy { it.id },
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
                    updatedAt = page.updatedAt,
                    buttons = page.buttonConfigs.mapIndexedNotNull { index, config ->
                        config?.let {
                            ImportButton(
                                index = index.toLong(),
                                id = it.id,
                                label = it.label,
                                spokenText = it.spokenText,
                                spokenTextMode = it.spokenTextMode.name,
                                audioFileName = it.audioFileName,
                                auditoryCueText = (it.auditoryCue as? AuditoryCue.TextToSpeechCue)?.text,
                                active = it.isActive,
                                playActionAsAuditoryCue = it.playActionAsAuditoryCue,
                                action = actionMapper.exportAction(it.buttonAction),
                                updatedAt = it.updatedAt
                            )
                        }
                    }.sortedBy { it.index }
                )
            }.sortedBy { it.importId },
            logicalVersion = null, // Deprecated/unused, kept for JSON compatibility
            deletedEntities = tombstones.sortedBy { it.entityId }
        )

        val exportData = if (includeSettings) {
            settingsMapper.exportSettings(bookId, baseExportData)
        } else {
            baseExportData
        }

        logger.d(TAG, "Exported book $bookId: defaultStartPageId='${exportData.defaultStartPageId}', scanDelay='${exportData.scanDelayMillis}'")
        json.encodeToString(exportData)
    }

    override suspend fun importFromJson(
        json: String,
        bookId: String,
        regenerateIds: Boolean,
        restoreSyncSettings: Boolean
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val importData = this@PageImportExportManager.json.decodeFromString<ImportExportData>(json)
            logger.d(TAG, "Importing JSON for book $bookId: defaultStartPageId='${importData.defaultStartPageId}', bookName='${importData.bookName}'")

            // 1. Clean state: Delete existing pages for this book before importing
            // This ensures that the restored book exactly matches the backup
            pageRepository.deletePagesForBook(bookId)
            deletedEntityDao.deleteTombstonesForBook(bookId)
            val importCutoff = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
            importData.deletedEntities?.filter { it.deletedAt >= importCutoff }?.forEach { tombstone ->
                deletedEntityDao.insertDeletedEntity(
                    com.andreas_kratzer.ghosttalk.core.database.DeletedEntity(
                        entityId = tombstone.entityId,
                        entityType = tombstone.entityType,
                        bookId = bookId,
                        deletedAt = tombstone.deletedAt
                    )
                )
            }
            logger.d(TAG, "Cleared existing pages and tombstones for book $bookId before import.")

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
                        logStopActions = importData.logStopActions ?: book.logStopActions,
                        versionSequence = importData.versionSequence ?: book.versionSequence
                    ))
                }
            }
            
            // 1.1 Update book updated timestamp if provided from import data (e.g. from cloud)
            importData.bookUpdatedAt?.let { timestamp ->
                bookRepository.updateLastModified(bookId, timestamp, incrementSequence = false)
            }

            val idMap = mutableMapOf<String, String>()
            
            // 2. Determine ID regeneration needs
            val sourceBookId = importData.bookId?.takeIf { it.isNotBlank() }
            val forceRegeneration = regenerateIds || (sourceBookId != null && sourceBookId.lowercase() != bookId.lowercase())

            val regeneratedPages = mutableSetOf<String>()
            importData.pages.forEach { importPage ->
                var targetPageId = importPage.importId
                if (targetPageId.startsWith("static_row_")) {
                    targetPageId = "static_row_$bookId"
                } else {
                    val existingPage = pageRepository.getPageById(targetPageId)
                    if (forceRegeneration || (existingPage != null && existingPage.bookId != bookId)) {
                        targetPageId = UUID.randomUUID().toString()
                        regeneratedPages.add(importPage.importId)
                    }
                }
                idMap[importPage.importId] = targetPageId
            }
            logger.d(TAG, "ID mapping complete. Mapping size: ${idMap.size}. ForceRegeneration: $forceRegeneration")

            // 2.1 Update book-scoped settings with correct book ID prefix
            val prefs = context.getSharedPreferences(SettingsConstants.PREFS_NAME, Context.MODE_PRIVATE)
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

            val restoredStartPageId = importData.defaultStartPageId?.let { oldId ->
                idMap[oldId] ?: oldId
            } ?: settingsRepository.getDefaultStartPageIdForBook(bookId)

            importData.pages.forEach { importPage ->
                val newPageId = idMap[importPage.importId]!!
                
                // Spatial mapping to 49 (7x7) buttons
                val buttons = MutableList<ButtonConfig?>(49) { null }
                
                importPage.buttons.forEach { importButton ->
                    val action = importButton.action?.let { actionMapper.importAction(it, idMap, restoredStartPageId) }
                    
                    if (importButton.label.isEmpty() && action == null && importButton.auditoryCueText == null && importButton.audioFileName == null) {
                        return@forEach
                    }

                    val finalAction = action ?: SpeakTextButtonAction()
                    val pageRegenerated = regeneratedPages.contains(importPage.importId)
                    val modeString = importButton.spokenTextMode
                    val spokenTextMode = if (modeString != null) {
                        try {
                            com.andreas_kratzer.ghosttalk.core.model.SpokenTextMode.valueOf(modeString)
                        } catch (_: IllegalArgumentException) {
                            com.andreas_kratzer.ghosttalk.core.model.SpokenTextMode.TTS
                        }
                    } else {
                        com.andreas_kratzer.ghosttalk.core.model.SpokenTextMode.TTS
                    }
                    val config = ButtonConfig(
                        id = if (forceRegeneration || pageRegenerated) UUID.randomUUID().toString() else (importButton.id ?: UUID.randomUUID().toString()),
                        label = importButton.label,
                        spokenText = importButton.spokenText ?: importButton.action?.textToSpeech,
                        spokenTextMode = spokenTextMode,
                        audioFileName = importButton.audioFileName,
                        auditoryCue = importButton.auditoryCueText?.let { AuditoryCue.TextToSpeechCue(it) },
                        isActive = importButton.active ?: true,
                        playActionAsAuditoryCue = importButton.playActionAsAuditoryCue ?: false,
                        buttonAction = finalAction,
                        updatedAt = importButton.updatedAt ?: System.currentTimeMillis()
                    )
                    
                    // Spatial logic: if it's a GhostTalk backup, the index is already a 7x7 grid index.
                    // If it's from GoTalk Now or others, map index from source columns to 7 columns.
                    val isGhostTalk = importData.appName == "GhostTalk" || importData.ghosttalk_import_version != null
                    val globalIndex = if (isGhostTalk) {
                        importButton.index.toInt()
                    } else {
                        val sourceCols = importPage.columns.coerceAtLeast(1)
                        val row = (importButton.index / sourceCols).toInt()
                        val col = (importButton.index % sourceCols).toInt()
                        row * 7 + col
                    }
                    
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
                    createdAt = importPage.createdAt ?: System.currentTimeMillis(),
                    updatedAt = importPage.updatedAt ?: importPage.createdAt ?: System.currentTimeMillis()
                )
                pageRepository.insertPage(page)
            }
            
            // 3. Import Button Templates (if syncing settings, since they are global)
            val importButtonTemplates = importData.buttonTemplates
            if (restoreSyncSettings && importButtonTemplates != null) {
                logger.d(TAG, "Importing Button Templates...")
                importButtonTemplates.forEach { importTemplate ->
                    val button = importTemplate.button
                    if (button != null) {
                        val action = button.action?.let { actionMapper.importAction(it, idMap, restoredStartPageId) } ?: SpeakTextButtonAction()
                        
                        val modeString = button.spokenTextMode
                        val spokenTextMode = if (modeString != null) {
                            try {
                                com.andreas_kratzer.ghosttalk.core.model.SpokenTextMode.valueOf(modeString)
                            } catch (_: IllegalArgumentException) {
                                com.andreas_kratzer.ghosttalk.core.model.SpokenTextMode.TTS
                            }
                        } else {
                            com.andreas_kratzer.ghosttalk.core.model.SpokenTextMode.TTS
                        }
                        val config = ButtonConfig(
                            id = button.id ?: UUID.randomUUID().toString(),
                            label = button.label,
                            spokenText = button.spokenText ?: button.action?.textToSpeech,
                            spokenTextMode = spokenTextMode,
                            audioFileName = button.audioFileName,
                            auditoryCue = button.auditoryCueText?.let { text -> AuditoryCue.TextToSpeechCue(text) },
                            isActive = button.active ?: true,
                            playActionAsAuditoryCue = button.playActionAsAuditoryCue ?: false,
                            buttonAction = action
                        )
                        
                        val template = com.andreas_kratzer.ghosttalk.core.model.ButtonTemplate(
                            id = importTemplate.id.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
                            name = importTemplate.name,
                            buttonConfig = config,
                            isBuiltIn = importTemplate.isBuiltIn,
                            orderIndex = importTemplate.orderIndex
                        )
                        buttonTemplateRepository.saveTemplate(template)
                    }
                }
            }
            
            // 3.1 Automatic repair: Ensure the static row page exists in the database
            val staticRowId = "static_row_$bookId"
            if (pageRepository.getPageById(staticRowId) == null) {
                logger.d(TAG, "Static row page missing after import, performing automatic repair for book $bookId")
                val defaultStaticRowPage = Page(
                    id = staticRowId,
                    bookId = bookId,
                    name = "Statische Zeile",
                    templateId = null,
                    rows = 1,
                    columns = 4,
                    scanPattern = "linear",
                    rowNames = emptyList(),
                    buttonConfigs = emptyList(),
                    orderIndex = -1,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                pageRepository.insertPage(defaultStaticRowPage)
            }
            
            // Sanitize imported layout by purging any INSTALL_UPDATE actions
            pageRepository.purgeInstallUpdateButtons()
            
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
            val profilesJson = json.encodeToString(profiles)
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
                                importStatisticsFromJson(content, bookId)
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
            val result = importFromJson(jsonContent!!, bookId, regenerateIds, restoreSyncSettings)
            
            vocalProfilesJson?.let {
                try {
                    val profiles = json.decodeFromString<List<com.andreas_kratzer.ghosttalk.core.model.VocalProfile>>(it)
                    profiles.forEach { profile ->
                        vocalProfileRepository.saveProfile(profile)
                    }
                    logger.d(TAG, "Imported ${profiles.size} vocal profiles from ZIP")
                } catch (e: Exception) {
                    logger.e(TAG, "Failed to import vocal profiles from ZIP", e)
                }
            }

            onProgress(1.0f, "Import complete.")
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getTtsCacheLastModified(): Long {
        val cacheDir = File(context.filesDir, "elevenlabs")
        if (!cacheDir.exists() || !cacheDir.isDirectory) return 0L
        return cacheDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".mp3") }
            ?.maxOfOrNull { it.lastModified() }
            ?: 0L
    }

    override suspend fun exportTtsCacheToZip(
        outputStream: OutputStream,
        onProgress: (Float, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val cacheDir = File(context.filesDir, "elevenlabs")
        val files = if (cacheDir.exists() && cacheDir.isDirectory) {
            cacheDir.listFiles()?.filter { it.isFile && it.name.endsWith(".mp3") } ?: emptyArray<File>().toList()
        } else {
            emptyList()
        }

        val fileEntries = files.map { it to "tts_cache/${it.name}" }
        logger.d(TAG, "Exporting TTS cache: ${files.size} files")
        zipArchiver.zip(outputStream, emptyMap(), fileEntries, onProgress)
    }

    override suspend fun importTtsCacheFromZip(
        inputStream: InputStream,
        onProgress: (Float, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val ttsCacheDir = File(context.filesDir, "elevenlabs")
        if (!ttsCacheDir.exists()) ttsCacheDir.mkdirs()

        val targetDirs = mapOf("tts_cache/" to ttsCacheDir)
        var count = 0

        val handler = object : ZipArchiver.UnzipHandler {
            override fun handleStringEntry(name: String, content: String) {}

            override fun handleFileEntry(name: String, time: Long, inputStream: InputStream, targetFile: File) {
                val fileName = name.substringAfter("tts_cache/")
                onProgress(0.5f, "Extracting: $fileName")
                FileOutputStream(targetFile).use { out ->
                    inputStream.copyTo(out)
                }
                if (time != -1L) {
                    targetFile.setLastModified(time)
                }
                count++
            }
        }

        zipArchiver.unzip(inputStream, targetDirs, handler, onProgress)
        logger.d(TAG, "Imported $count TTS cache files")
        onProgress(1f, "TTS cache import complete.")
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
            val result = importCloudBackup(jsonContent!!, cloudFileId)
            onProgress(1.0f, "Import complete.")
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun exportStatisticsToJson(bookId: String): String = withContext(Dispatchers.IO) {
        val historyList = buttonUsageDao.getHistoryForBook(bookId).first()
        val statsList = buttonUsageDao.getAllStatsForBook(bookId)
        val sessionsList = userModeSessionRepository.getSessionsForBook(bookId).firstOrNull() ?: emptyList()
        
        val exportedStats = statsList.map { stat ->
            ExportedButtonStat(
                buttonConfigId = stat.buttonConfigId,
                pageId = stat.pageId,
                label = stat.label,
                actionJson = stat.actionJson,
                usageCount = stat.usageCount,
                lastUsedAt = stat.lastUsedAt
            )
        }
        
        val exportedHistory = historyList.map { event ->
            ExportedHistoryEvent(
                timestamp = event.timestamp,
                label = event.label,
                actionType = event.actionType,
                buttonId = event.buttonId,
                pageId = event.pageId,
                imagePath = event.imagePath,
                geminiResponse = event.geminiResponse,
                latitude = event.latitude,
                longitude = event.longitude,
                sessionId = event.sessionId,
                reactionTimeMs = event.reactionTimeMs,
                isTouchIntervention = event.isTouchIntervention,
                wifiSsid = event.wifiSsid,
                isHardwareTriggered = event.isHardwareTriggered
            )
        }

        val exportedSessions = sessionsList.map { session ->
            ExportedUserModeSession(
                startTime = session.startTime,
                endTime = session.endTime
            )
        }
        
        val appVerName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Exception) {
            null
        }

        val statistics = ExportedStatistics(
            bookId = bookId,
            history = exportedHistory,
            stats = exportedStats,
            statsVersion = 1,
            appVersion = appVerName,
            userModeSessions = exportedSessions,
            sourceDevice = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        )
        json.encodeToString(statistics)
    }

    private suspend fun importStatisticsFromJson(jsonString: String, bookId: String) = withContext(Dispatchers.IO) {
        try {
            val statistics = json.decodeFromString<ExportedStatistics>(jsonString)
            
            // Clean slate first
            buttonUsageDao.clearHistoryForBook(bookId)
            buttonUsageDao.clearStatsForBook(bookId)
            userModeSessionRepository.clearSessions(bookId)
            
            // Re-insert history events
            statistics.history.forEach { event ->
                val mappedPageId = event.pageId?.let { 
                    if (it.startsWith("static_row_")) "static_row_$bookId" else it 
                }
                val entity = com.andreas_kratzer.ghosttalk.core.database.ButtonUsageHistoryEntity(
                    bookId = bookId,
                    timestamp = event.timestamp,
                    label = event.label,
                    actionType = event.actionType,
                    buttonId = event.buttonId,
                    pageId = mappedPageId,
                    imagePath = event.imagePath,
                    geminiResponse = event.geminiResponse,
                    latitude = event.latitude,
                    longitude = event.longitude,
                    sessionId = event.sessionId,
                    reactionTimeMs = event.reactionTimeMs,
                    isTouchIntervention = event.isTouchIntervention,
                    wifiSsid = event.wifiSsid,
                    isHardwareTriggered = event.isHardwareTriggered
                )
                buttonUsageDao.insertHistoryEvent(entity)
            }
            
            // Re-insert stats counters
            statistics.stats.forEach { stat ->
                val mappedPageId = if (stat.pageId.startsWith("static_row_")) {
                    "static_row_$bookId"
                } else {
                    stat.pageId
                }
                val entity = com.andreas_kratzer.ghosttalk.core.model.ButtonUsageStat(
                    bookId = bookId,
                    buttonConfigId = stat.buttonConfigId,
                    pageId = mappedPageId,
                    label = stat.label,
                    actionJson = stat.actionJson,
                    usageCount = stat.usageCount,
                    lastUsedAt = stat.lastUsedAt
                )
                buttonUsageDao.upsert(entity)
            }

            // Re-insert user mode sessions
            statistics.userModeSessions?.let { sessions ->
                val domainSessions = sessions.map {
                    com.andreas_kratzer.ghosttalk.core.model.UserModeSession(
                        id = 0L,
                        bookId = bookId,
                        startTime = it.startTime,
                        endTime = it.endTime
                    )
                }
                userModeSessionRepository.insertSessions(domainSessions)
            }

            logger.d(TAG, "Imported statistics for book $bookId: ${statistics.history.size} history events, ${statistics.stats.size} stats counter, ${statistics.userModeSessions?.size ?: 0} user mode sessions.")
        } catch (e: Exception) {
            logger.e(TAG, "Failed to import statistics for book $bookId", e)
        }
    }

    override suspend fun exportStatisticsToZip(
        bookId: String,
        outputStream: OutputStream
    ) = withContext(Dispatchers.IO) {
        val statsJson = exportStatisticsToJson(bookId)
        zipArchiver.zip(outputStream, mapOf("statistics.json" to statsJson), emptyList())
    }

    override suspend fun importStatisticsFromZip(
        bookId: String,
        inputStream: InputStream
    ) = withContext(Dispatchers.IO) {
        val handler = object : ZipArchiver.UnzipHandler {
            override fun handleStringEntry(name: String, content: String) {
                if (name == "statistics.json") {
                    kotlinx.coroutines.runBlocking {
                        importStatisticsFromJson(content, bookId)
                    }
                }
            }
            override fun handleFileEntry(name: String, time: Long, inputStream: InputStream, targetFile: File) {}
        }
        zipArchiver.unzip(inputStream, emptyMap(), handler)
    }

    override suspend fun getStatisticsLastModified(bookId: String): Long = withContext(Dispatchers.IO) {
        val lastHistoryTime = buttonUsageDao.getLastHistoryEvent(bookId)?.timestamp ?: 0L
        val lastStatTime = buttonUsageDao.getAllStatsForBook(bookId).maxOfOrNull { it.lastUsedAt } ?: 0L
        val lastSessionTime = userModeSessionRepository.getSessionsForBook(bookId).firstOrNull()?.maxOfOrNull { it.endTime } ?: 0L
        maxOf(lastHistoryTime, lastStatTime, lastSessionTime)
    }

    override suspend fun exportAudioRecordingsToZip(
        outputStream: OutputStream,
        onProgress: (Float, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val audioDir = File(context.filesDir, "audio_recordings")
        val files = if (audioDir.exists() && audioDir.isDirectory) {
            audioDir.listFiles()?.filter { it.isFile && it.name.endsWith(".ogg") } ?: emptyList()
        } else {
            emptyList()
        }

        val fileEntries = files.map { it to "audio_recordings/${it.name}" }
        logger.d(TAG, "Exporting audio recordings: ${files.size} files")
        zipArchiver.zip(outputStream, emptyMap(), fileEntries, onProgress)
    }

    override suspend fun importAudioRecordingsFromZip(
        inputStream: InputStream,
        onProgress: (Float, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val audioDir = File(context.filesDir, "audio_recordings")
        if (!audioDir.exists()) audioDir.mkdirs()

        val targetDirs = mapOf("audio_recordings/" to audioDir)
        var count = 0

        val handler = object : ZipArchiver.UnzipHandler {
            override fun handleStringEntry(name: String, content: String) {}

            override fun handleFileEntry(name: String, time: Long, inputStream: InputStream, targetFile: File) {
                val fileName = name.substringAfter("audio_recordings/")
                onProgress(0.5f, "Extracting: $fileName")
                FileOutputStream(targetFile).use { out ->
                    inputStream.copyTo(out)
                }
                if (time != -1L) {
                    targetFile.setLastModified(time)
                }
                count++
            }
        }

        zipArchiver.unzip(inputStream, targetDirs, handler, onProgress)
        logger.d(TAG, "Imported $count audio recording files")
        onProgress(1f, "Audio recordings import complete.")
    }

    override fun getAudioRecordingsLastModified(): Long {
        val audioDir = File(context.filesDir, "audio_recordings")
        if (audioDir.exists() && audioDir.isDirectory) {
            return audioDir.listFiles()?.filter { it.isFile && it.name.endsWith(".ogg") }
                ?.maxOfOrNull { it.lastModified() }
                ?: 0L
        }
        return 0L
    }

    override fun exportBookConfigToJson(bookId: String): String {
        val configSettings = settingsMapper.exportConfigSettings(bookId)
        val wrapper = com.andreas_kratzer.ghosttalk.core.model.importexport.BookConfigWrapper(
            version = 1,
            bookId = bookId,
            lastModified = getBookConfigLastModified(bookId),
            settings = configSettings
        )
        return json.encodeToString(com.andreas_kratzer.ghosttalk.core.model.importexport.BookConfigWrapper.serializer(), wrapper)
    }

    override fun importBookConfigFromJson(jsonString: String, bookId: String): Result<Unit> {
        return try {
            val wrapper = json.decodeFromString<com.andreas_kratzer.ghosttalk.core.model.importexport.BookConfigWrapper>(jsonString)
            settingsMapper.importSettings(wrapper.settings)
            settingsRepository.updateConfigLastModified(bookId)
            val prefs = context.getSharedPreferences("ghosttalk_settings", Context.MODE_PRIVATE)
            prefs.edit {
                putLong("config_last_synced_remote_time_$bookId", wrapper.lastModified)
                putLong("config_last_synced_local_time_$bookId", settingsRepository.getConfigLastModified(bookId))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getBookConfigLastModified(bookId: String): Long {
        return settingsRepository.getConfigLastModified(bookId)
    }

    private fun isSafeFile(parentDir: File, file: File): Boolean {
        val canonicalParent = parentDir.canonicalPath
        val canonicalTarget = file.canonicalPath
        return canonicalTarget.startsWith(canonicalParent + File.separator)
    }
}


package com.andreas_kratzer.ghosttalk.core.pages

import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.data.TemplateRepository
import com.andreas_kratzer.ghosttalk.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.FrequentActionButtonAction
import com.andreas_kratzer.ghosttalk.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.PageTemplate
import com.andreas_kratzer.ghosttalk.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.model.importexport.ImportAction
import com.andreas_kratzer.ghosttalk.model.importexport.ImportButton
import com.andreas_kratzer.ghosttalk.model.importexport.ImportExportData
import com.andreas_kratzer.ghosttalk.model.importexport.ImportPage
import com.andreas_kratzer.ghosttalk.model.importexport.ImportTemplate
import com.andreas_kratzer.ghosttalk.ui.util.GridUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.UUID

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.encodeToString

class PageImportExportManager @javax.inject.Inject constructor(
    private val bookRepository: com.andreas_kratzer.ghosttalk.data.BookRepository,
    private val pageRepository: PageRepository,
    private val settingsRepository: SettingsRepository,
    private val templateRepository: TemplateRepository,
    private val logger: Logger,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    suspend fun importFromJson(
        jsonString: String, 
        bookId: String, 
        regenerateIds: Boolean? = null,
        restoreSyncSettings: Boolean = true
    ): Result<Int> = importBookFromJson(jsonString, bookId, regenerateIds, restoreSyncSettings)

    fun extractBookNameFromJson(jsonString: String): String? {
        return try {
            val jsonObject = json.parseToJsonElement(jsonString).jsonObject
            jsonObject["bookName"]?.jsonPrimitive?.contentOrNull
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun importBookFromJson(
        jsonString: String, 
        bookId: String, 
        regenerateIds: Boolean? = null,
        restoreSyncSettings: Boolean = true
    ): Result<Int> {
        return withContext(ioDispatcher) {
        try {
            logger.d("PageImportExportManager", "Starting import mapping parsing for book $bookId...")
            val importData = json.decodeFromString<ImportExportData>(jsonString)
            
            val finalRegenerateIds = regenerateIds ?: (importData.bookId != null && importData.bookId != bookId)
            
            if (importData.pages.isEmpty()) {
                logger.e("PageImportExportManager", "Parsed JSON was invalid or missing 'pages'")
                return@withContext Result.failure(Exception("Ungültiges JSON-Format. Seiten fehlen."))
            }

            // Sync settings if present
            importData.holdingTimeSeconds?.let { seconds ->
                settingsRepository.holdingTimeMillis = (seconds * 1000).toLong()
            }
            importData.autoStartScanning?.let { settingsRepository.autoStartScanning = it }
            importData.scanDelayMillis?.let { settingsRepository.scanDelayMillis = it }
            importData.resumeScanningFromStart?.let { settingsRepository.resumeScanningFromStart = it }
            importData.switchActivationKey?.let { settingsRepository.switchActivationKey = it }
            importData.volumeKeysActivate?.let { settingsRepository.volumeKeysActivate = it }
            importData.defaultScanPattern?.let { settingsRepository.defaultScanPattern = it }
            
            importData.isSmartPredictionEnabled?.let { settingsRepository.isSmartPredictionEnabled = it }
            importData.geminiRedoPrediction?.let { settingsRepository.geminiRedoPrediction = it }
            importData.geminiTimeout?.let { settingsRepository.geminiTimeout = it }
            importData.isGeminiEnabled?.let { settingsRepository.isGeminiEnabled = it }
            importData.useLocalGenerativeAi?.let { settingsRepository.useLocalGenerativeAi = it }

            if (restoreSyncSettings) {
                importData.isCloudSyncEnabled?.let { settingsRepository.isCloudSyncEnabled = it }
                importData.syncIntervalMinutes?.let { settingsRepository.syncIntervalMinutes = it }
                importData.syncMode?.let { settingsRepository.syncMode = it }
            }

            importData.ttsLanguage?.let { settingsRepository.ttsLanguage = it }
            importData.ttsVoiceName?.let { settingsRepository.ttsVoiceName = it }
            importData.pageSortOrder?.let { settingsRepository.pageSortOrder = it }
            importData.templateSortOrder?.let { settingsRepository.templateSortOrder = it }
            importData.smartPredictionDelay?.let { settingsRepository.smartPredictionDelayMillis = it }
            importData.keepScreenOnUserMode?.let { settingsRepository.keepScreenOnUserMode = it }
            importData.userModeScreenBehavior?.let { settingsRepository.userModeScreenBehavior = it }
            importData.defaultStartPageId?.let { settingsRepository.defaultStartPageId = it }
            importData.securityPinHash?.let { settingsRepository.securityPinHash = it }
            importData.securityPinSalt?.let { settingsRepository.securityPinSalt = it }
            
            importData.themeMode?.let { settingsRepository.themeMode = it }
            importData.securityPinTimeoutMinutes?.let { settingsRepository.securityPinTimeoutMinutes = it }
            importData.isPinRequiredForDeletion?.let { settingsRepository.isPinRequiredForDeletion = it }
            importData.isBiometricEnabled?.let { settingsRepository.isBiometricEnabled = it }
            importData.isSecurityRequiredForEdit?.let { settingsRepository.isSecurityRequiredForEdit = it }
            importData.isSecurityRequiredForSettings?.let { settingsRepository.isSecurityRequiredForSettings = it }
            importData.startupBehavior?.let { settingsRepository.startupBehavior = it }
            importData.favoriteBookId?.let { settingsRepository.favoriteBookId = it }
            importData.weatherCacheTimeout?.let { settingsRepository.weatherCacheTimeout = it }

            // Update Book metadata if present
            if (importData.bookName != null) {
                val existingBook = bookRepository.getBookById(bookId)
                if (existingBook != null) {
                    bookRepository.updateBook(existingBook.copy(
                        name = importData.bookName,
                        createdAt = importData.bookCreatedAt ?: existingBook.createdAt,
                        updatedAt = importData.bookUpdatedAt ?: System.currentTimeMillis()
                    ))
                }
            }

            // Map IDs for incoming pages. We use the importId if present, else generate a new UUID.
            // Using a unique key for the map to handle empty/missing importIds correctly per page object.
            val pageToIdMap = mutableMapOf<ImportPage, String>()
            val importIdToIdMap = mutableMapOf<String, String>()
            importData.pages.forEach { p ->
                var forceRegenerate = finalRegenerateIds || p.importId.isBlank()
                
                // Collision check: if the ID already exists in the DB but belongs to a DIFFERENT book,
                // we MUST regenerate it to prevent "stealing" the page from the other book.
                if (!forceRegenerate) {
                    val existingPage = pageRepository.getPageById(p.importId)
                    if (existingPage != null && existingPage.bookId != bookId) {
                        logger.w("PageImportExportManager", "Collision detected for page ${p.importId}. It belongs to book ${existingPage.bookId}, but we are importing into $bookId. Forcing ID regeneration.")
                        forceRegenerate = true
                    }
                }
                
                val targetId = if (forceRegenerate) UUID.randomUUID().toString() else p.importId
                pageToIdMap[p] = targetId
                if (p.importId.isNotBlank()) {
                    importIdToIdMap[p.importId] = targetId
                }
            }

            // Handle Templates
            importData.templates?.forEach { importTemplate ->
                if (!importTemplate.isBuiltIn) {
                    val maxIndex = importTemplate.buttons.maxOfOrNull { it.index }?.toInt() ?: -1
                    var rows = importTemplate.rows
                    var columns = importTemplate.columns
                    
                    if (maxIndex >= rows * columns) {
                        if (columns < 7 && maxIndex >= rows * 7) {
                            columns = 7
                        } else if (columns < 7) {
                            while (columns < 7 && rows * columns <= maxIndex) {
                                columns++
                            }
                        }
                        while (rows < 7 && rows * columns <= maxIndex) {
                            rows++
                        }
                    }

                    rows = rows.coerceIn(1, 7)
                    columns = columns.coerceIn(1, 7)

                    val templateButtonConfigs = MutableList<ButtonConfig?>(GridUtils.TOTAL_SLOTS) { null }
                    importTemplate.buttons.forEach { importButton ->
                        val localIdx = importButton.index.toInt()
                        val globalIdx = GridUtils.localToGlobalIndex(localIdx, importTemplate.columns)
                        
                        if (globalIdx < GridUtils.TOTAL_SLOTS) {
                            val importAction = importButton.action
                            val action = importAction?.let { ia ->
                                when (ia.type) {
                                    "SpeakText", "SPEAK" -> SpeakTextButtonAction()
                                    "FrequentAction" -> {
                                        val rank = ia.targetPageImportId?.toIntOrNull() ?: 1
                                        FrequentActionButtonAction(rank)
                                    }
                                    "NavigateToPage", "NAVIGATE" -> {
                                        val sourceId = ia.targetPageImportId ?: ia.targetPageId ?: ""
                                        val targetId = importIdToIdMap[sourceId] ?: sourceId
                                        NavigateToPageButtonAction(targetId)
                                    }
                                    else -> null
                                }
                            }
                            if (importButton.label.isNotBlank() && action != null) {
                                templateButtonConfigs[globalIdx] = ButtonConfig(
                                    id = if (finalRegenerateIds || importButton.id.isNullOrBlank()) UUID.randomUUID().toString() else importButton.id!!,
                                    label = importButton.label,
                                    spokenText = importButton.spokenText ?: importAction.textToSpeech ?: importAction.ttsFeedback,
                                    buttonAction = action,
                                    auditoryCue = importButton.auditoryCueText?.let { AuditoryCue.TextToSpeechCue(it) },
                                    isActive = importButton.active ?: true,
                                    playActionAsAuditoryCue = importButton.playActionAsAuditoryCue ?: false
                                )
                            }
                        }
                    }
                    val pageTemplate = PageTemplate(
                        id = importTemplate.id,
                        name = importTemplate.name,
                        rows = rows,
                        columns = columns,
                        scanPattern = importTemplate.scanPattern,
                        rowNames = importTemplate.rowNames ?: emptyList(),
                        buttonConfigs = templateButtonConfigs,
                        isBuiltIn = false,
                        orderIndex = importTemplate.orderIndex ?: 0,
                        createdAt = importTemplate.createdAt ?: System.currentTimeMillis()
                    )
                    templateRepository.insert(pageTemplate)
                }
            }

            logger.d("PageImportExportManager", "Parsed ${importData.pages.size} pages. Committing to Room DB...")

            val newPages = importData.pages.map { importPage ->
                val newPageId = pageToIdMap[importPage] ?: UUID.randomUUID().toString()
                val maxIndex = importPage.buttons.maxOfOrNull { it.index }?.toInt() ?: -1
                var rows = importPage.rows
                var columns = importPage.columns

                if (maxIndex >= rows * columns) {
                    if (columns < 7 && maxIndex >= rows * 7) {
                        columns = 7
                    } else if (columns < 7) {
                        while (columns < 7 && rows * columns <= maxIndex) {
                            columns++
                        }
                    }
                    while (rows < 7 && rows * columns <= maxIndex) {
                        rows++
                    }
                }

                rows = rows.coerceIn(1, 7)
                columns = columns.coerceIn(1, 7)
                
                val buttonConfigs = MutableList<ButtonConfig?>(GridUtils.TOTAL_SLOTS) { null }
                importPage.buttons.forEach { importButton ->
                    val buttonId = if (importButton.id.isNullOrBlank()) UUID.randomUUID().toString() else importButton.id
                    val localIdx = importButton.index.toInt()
                    // Spatial mapping: place it in the 7x7 storage at (row, col)
                    val globalIdx = GridUtils.localToGlobalIndex(localIdx, importPage.columns)
                    
                    if (globalIdx < GridUtils.TOTAL_SLOTS) {
                        val importAction = importButton.action
                        val action = importAction?.let { ia ->
                            when (ia.type) {
                                "SpeakText", "SPEAK" -> SpeakTextButtonAction()
                                "FrequentAction" -> {
                                    val rank = ia.targetPageImportId?.toIntOrNull() ?: 1
                                    FrequentActionButtonAction(rank)
                                }
                                "NavigateToPage", "NAVIGATE" -> {
                                    val sourceId = ia.targetPageImportId ?: ia.targetPageId ?: ""
                                    val targetId = importIdToIdMap[sourceId] ?: sourceId
                                    NavigateToPageButtonAction(targetId)
                                }
                                else -> null
                            }
                        }
                        if (importButton.label.isNotBlank() && action != null) {
                            val forceButtonRegenerate = finalRegenerateIds || (importPage.importId.isNotBlank() && newPageId != importPage.importId)
                            buttonConfigs[globalIdx] = ButtonConfig(
                                id = if (forceButtonRegenerate || importButton.id.isNullOrBlank()) UUID.randomUUID().toString() else importButton.id!!,
                                label = importButton.label,
                                spokenText = importButton.spokenText ?: importAction.textToSpeech ?: importAction.ttsFeedback,
                                buttonAction = action,
                                auditoryCue = importButton.auditoryCueText?.let { AuditoryCue.TextToSpeechCue(it) },
                                isActive = importButton.active ?: true,
                                playActionAsAuditoryCue = importButton.playActionAsAuditoryCue ?: false
                            )
                        }
                    }
                }

                Page(
                    id = newPageId,
                    bookId = bookId,
                    name = importPage.name,
                    templateId = importPage.templateId,
                    rows = rows,
                    columns = columns,
                    scanPattern = importPage.scanPattern,
                    rowNames = importPage.rowNames ?: emptyList(),
                    buttonConfigs = buttonConfigs,
                    orderIndex = importPage.orderIndex ?: 0,
                    createdAt = importPage.createdAt ?: System.currentTimeMillis()
                )
            }

            newPages.forEach { pageRepository.insertPage(it) }
            logger.d("PageImportExportManager", "Successfully committed ${newPages.size} pages")
            Result.success(newPages.size)
        } catch (e: Exception) {
            logger.e("PageImportExportManager", "Exception during import", e)
            Result.failure(e)
        }
    }
}

    suspend fun exportBookToJson(bookId: String): String = withContext(ioDispatcher) {
        val book = bookRepository.getBookById(bookId)
        val pages = pageRepository.getPagesForBook(bookId)
        exportToJson(pages, book)
    }

    suspend fun exportToJson(pages: List<Page>, book: com.andreas_kratzer.ghosttalk.model.Book? = null): String = withContext(ioDispatcher) {
        val importPages = pages.map { page ->
            // Use current rows/cols of the page to decide which buttons to export
            // and how to map their indices
            val buttons = page.buttonConfigs.mapIndexedNotNull { globalIndex, config ->
                if (config != null && GridUtils.isVisibleInGrid(globalIndex, page.rows, page.columns)) {
                    val importAction = when (val action = config.buttonAction) {
                        is SpeakTextButtonAction -> ImportAction(
                            type = "SPEAK",
                            textToSpeech = config.spokenText,
                            targetPageImportId = null,
                            ttsFeedback = null
                        )
                        is FrequentActionButtonAction -> ImportAction(
                            type = "FrequentAction",
                            textToSpeech = null,
                            targetPageImportId = action.rank.toString(),
                            ttsFeedback = null
                        )
                        is NavigateToPageButtonAction -> ImportAction(
                            type = "NAVIGATE",
                            textToSpeech = null,
                            targetPageImportId = action.pageId,
                            ttsFeedback = null
                        )
                        else -> null
                    }
                    
                    // Reverse spatial mapping: global 7x7 back to local (rows x cols)
                    val localIndex = GridUtils.globalToLocalIndex(globalIndex, page.columns)

                    ImportButton(
                        id = config.id,
                        index = localIndex.toLong(),
                        label = config.label,
                        spokenText = config.spokenText,
                        auditoryCueText = (config.auditoryCue as? AuditoryCue.TextToSpeechCue)?.text,
                        action = importAction,
                        active = config.isActive,
                        playActionAsAuditoryCue = config.playActionAsAuditoryCue
                    )
                } else null
            }
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
                buttons = buttons
            )
        }
        
        val allTemplates = templateRepository.getAllTemplates().first()
        val importTemplates = allTemplates.filter { !it.isBuiltIn }.map { template ->
            val buttons = template.buttonConfigs.mapIndexedNotNull { index, config ->
                config?.let {
                    val importAction = when (val action = it.buttonAction) {
                        is SpeakTextButtonAction -> ImportAction(
                            type = "SPEAK",
                            textToSpeech = it.spokenText,
                            targetPageImportId = null,
                            ttsFeedback = null
                        )
                        is FrequentActionButtonAction -> ImportAction(
                            type = "FrequentAction",
                            textToSpeech = null,
                            targetPageImportId = action.rank.toString(),
                            ttsFeedback = null
                        )
                        is NavigateToPageButtonAction -> ImportAction(
                            type = "NAVIGATE",
                            textToSpeech = null,
                            targetPageImportId = action.pageId,
                            ttsFeedback = null
                        )
                        else -> null
                    }
                    ImportButton(
                        id = it.id,
                        index = index.toLong(),
                        label = it.label,
                        spokenText = it.spokenText,
                        auditoryCueText = (it.auditoryCue as? AuditoryCue.TextToSpeechCue)?.text,
                        action = importAction,
                        active = it.isActive,
                        playActionAsAuditoryCue = it.playActionAsAuditoryCue
                    )
                }
            }
            ImportTemplate(
                id = template.id,
                name = template.name,
                rows = template.rows,
                columns = template.columns,
                scanPattern = template.scanPattern,
                rowNames = template.rowNames,
                orderIndex = template.orderIndex,
                createdAt = template.createdAt,
                isBuiltIn = template.isBuiltIn,
                buttons = buttons
            )
        }

        val exportData = ImportExportData(
            ghosttalk_import_version = "1.1",
            appName = "GhosTTalk (Export)",
            bookName = book?.name,
            bookId = book?.id,
            bookCreatedAt = book?.createdAt,
            bookUpdatedAt = book?.updatedAt,
            themeMode = settingsRepository.themeMode,
            securityPinTimeoutMinutes = settingsRepository.securityPinTimeoutMinutes,
            isPinRequiredForDeletion = settingsRepository.isPinRequiredForDeletion,
            isBiometricEnabled = settingsRepository.isBiometricEnabled,
            isSecurityRequiredForEdit = settingsRepository.isSecurityRequiredForEdit,
            isSecurityRequiredForSettings = settingsRepository.isSecurityRequiredForSettings,
            startupBehavior = settingsRepository.startupBehavior,
            favoriteBookId = settingsRepository.favoriteBookId,
            weatherCacheTimeout = settingsRepository.weatherCacheTimeout,
            holdingTimeSeconds = settingsRepository.holdingTimeMillis / 1000f,
            autoStartScanning = settingsRepository.autoStartScanning,
            scanDelayMillis = settingsRepository.scanDelayMillis,
            resumeScanningFromStart = settingsRepository.resumeScanningFromStart,
            switchActivationKey = settingsRepository.switchActivationKey,
            volumeKeysActivate = settingsRepository.volumeKeysActivate,
            defaultScanPattern = settingsRepository.defaultScanPattern,
            isSmartPredictionEnabled = settingsRepository.isSmartPredictionEnabled,
            geminiRedoPrediction = settingsRepository.geminiRedoPrediction,
            geminiTimeout = settingsRepository.geminiTimeout,
            isGeminiEnabled = settingsRepository.isGeminiEnabled,
            useLocalGenerativeAi = settingsRepository.useLocalGenerativeAi,
            isCloudSyncEnabled = settingsRepository.isCloudSyncEnabled,
            syncIntervalMinutes = settingsRepository.syncIntervalMinutes,
            syncMode = settingsRepository.syncMode,
            ttsLanguage = settingsRepository.ttsLanguage,
            ttsVoiceName = settingsRepository.ttsVoiceName,
            pageSortOrder = settingsRepository.pageSortOrder,
            templateSortOrder = settingsRepository.templateSortOrder,
            smartPredictionDelay = settingsRepository.smartPredictionDelayMillis,
            keepScreenOnUserMode = settingsRepository.keepScreenOnUserMode,
            userModeScreenBehavior = settingsRepository.userModeScreenBehavior,
            defaultStartPageId = settingsRepository.defaultStartPageId,
            securityPinHash = settingsRepository.securityPinHash,
            securityPinSalt = settingsRepository.securityPinSalt,
            templates = importTemplates,
            pages = importPages
        )
        json.encodeToString(exportData)
    }
}

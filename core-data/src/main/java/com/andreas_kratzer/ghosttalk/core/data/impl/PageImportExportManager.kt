package com.andreas_kratzer.ghosttalk.core.data.impl

import android.content.Context
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants
import androidx.core.content.edit
import com.andreas_kratzer.ghosttalk.core.data.export.PageImportExportProvider
import com.andreas_kratzer.ghosttalk.core.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction
import com.andreas_kratzer.ghosttalk.core.model.DeviceActionType
import com.andreas_kratzer.ghosttalk.core.model.FrequentActionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiNanoButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiSearchButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiVisionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.SmartHomeButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SmartHomeProvider
import com.andreas_kratzer.ghosttalk.core.model.SmartPredictionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.core.model.WeatherButtonAction
import com.andreas_kratzer.ghosttalk.core.model.importexport.ImportAction
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
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PageImportExportManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val pageRepository: PageRepository,
    private val bookRepository: BookRepository,
    private val settingsRepository: SettingsRepository,
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
                            action = config?.buttonAction?.let { exportAction(it, config.spokenText) }
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
        val exportData = ImportExportData(
            bookId = book.id,
            bookName = book.name,
            bookCreatedAt = book.createdAt,
            bookUpdatedAt = book.updatedAt,
            actionLogLimit = settingsRepository.getActionLogLimitForBook(bookId),
            limitScanCycles = settingsRepository.getLimitScanCyclesForBook(bookId),
            scanCycleLimit = settingsRepository.getScanCycleLimitForBook(bookId),
            logIgnoredActions = settingsRepository.getLogIgnoredActionsForBook(bookId),
            logStopActions = settingsRepository.getLogStopActionsForBook(bookId),
            holdingTimeSeconds = settingsRepository.getHoldingTimeMillisForBook(bookId) / 1000f,
            autoStartScanning = settingsRepository.getAutoStartScanningForBook(bookId),
            scanDelayMillis = settingsRepository.getScanDelayMillisForBook(bookId),
            resumeScanningFromStart = settingsRepository.getResumeScanningFromStartForBook(bookId),
            switchActivationKey = settingsRepository.getSwitchActivationKeyForBook(bookId),
            volumeKeysActivate = settingsRepository.getVolumeKeysActivateForBook(bookId),
            defaultScanPattern = settingsRepository.getDefaultScanPatternForBook(bookId),
            isSmartPredictionEnabled = settingsRepository.getIsSmartPredictionEnabledForBook(bookId),
            geminiRedoPrediction = settingsRepository.geminiRedoPrediction,
            geminiTimeout = settingsRepository.geminiTimeout,
            isGeminiEnabled = settingsRepository.isGeminiEnabled,
            useLocalGenerativeAi = settingsRepository.useLocalGenerativeAi,
            isCloudSyncEnabled = settingsRepository.isCloudSyncEnabled,
            syncIntervalMinutes = settingsRepository.syncIntervalMinutes,
            syncMode = settingsRepository.syncMode,
            ttsLanguage = settingsRepository.ttsLanguage,
            ttsVoiceName = settingsRepository.ttsVoiceName,
            pageSortOrder = settingsRepository.getPageSortOrderForBook(bookId),
            templateSortOrder = settingsRepository.getTemplateSortOrderForBook(bookId),
            smartPredictionDelay = settingsRepository.getSmartPredictionDelayForBook(bookId),
            keepScreenOnUserMode = settingsRepository.keepScreenOnUserMode,
            userModeScreenBehavior = settingsRepository.userModeScreenBehavior,
            themeMode = settingsRepository.themeMode,
            securityPinTimeoutMinutes = settingsRepository.securityPinTimeoutMinutes,
            isPinRequiredForDeletion = settingsRepository.isPinRequiredForDeletion,
            isBiometricEnabled = settingsRepository.isBiometricEnabled,
            isSecurityRequiredForEdit = settingsRepository.isSecurityRequiredForEdit,
            isSecurityRequiredForSettings = settingsRepository.isSecurityRequiredForSettings,
            startupBehavior = settingsRepository.startupBehavior,
            favoriteBookId = settingsRepository.favoriteBookId,
            weatherCacheTimeout = settingsRepository.weatherCacheTimeout,
            defaultStartPageId = settingsRepository.getDefaultStartPageIdForBook(bookId),
            securityPinHash = settingsRepository.securityPinHash,
            securityPinSalt = settingsRepository.securityPinSalt,
            appLanguage = settingsRepository.appLanguage,
            isNotificationReadingEnabled = settingsRepository.isNotificationReadingEnabled,
            monitoredNotificationApps = settingsRepository.monitoredNotificationApps.toList(),
            showPageIdInLog = settingsRepository.showPageIdInLog,
            bluetoothDelay = settingsRepository.bluetoothDelay,
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
                                action = exportAction(it.buttonAction)
                            )
                        }
                    }
                )
            }
        )

        logger.d(TAG, "Exported book $bookId: defaultStartPageId='${exportData.defaultStartPageId}', scanDelay='${exportData.scanDelayMillis}'")
        json.encodeToString(exportData)
    }

    private fun exportAction(action: ButtonAction, spokenText: String? = null): ImportAction {
        return when (action) {
            is SpeakTextButtonAction -> ImportAction(type = "SPEAK", textToSpeech = spokenText)
            is NavigateToPageButtonAction -> ImportAction(type = "NAVIGATE", targetPageId = action.pageId, targetPageImportId = action.pageId)
            is FrequentActionButtonAction -> ImportAction(type = "SMART_PREDICTION", rank = action.rank)
            is GeminiButtonAction -> ImportAction(type = "GEMINI", prompt = action.prompt)
            is GeminiSearchButtonAction -> ImportAction(type = "GEMINI_SEARCH", prompt = action.prompt)
            is GeminiNanoButtonAction -> ImportAction(type = "GEMINI_NANO", intent = action.intent)
            is SmartPredictionButtonAction -> ImportAction(type = "SMART_PREDICTION", rank = action.rank)
            is GeminiVisionButtonAction -> ImportAction(type = "GEMINI_VISION", prompt = action.prompt, useCloud = action.useCloud)
            is ControlDeviceButtonAction -> ImportAction(
                type = "DEVICE_CONTROL",
                deviceActionType = action.actionType.name,
                volumeValue = action.volumeValue,
                contactName = action.contactName,
                contactPhone = action.contactPhone,
                messageText = action.messageText
            )
            is WeatherButtonAction -> ImportAction(type = "WEATHER")
            is SmartHomeButtonAction -> ImportAction(
                type = "SMART_HOME",
                smartHomeProvider = action.provider.name,
                smartHomeDeviceId = action.deviceId,
                smartHomeDeviceName = action.deviceName,
                smartHomeIntent = action.intent,
                smartHomeValue = action.value
            )
        }
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
                importData.holdingTimeSeconds?.let { 
                    settingsRepository.holdingTimeMillis = (it * 1000).toLong()
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
                importData.isCloudSyncEnabled?.let { settingsRepository.isCloudSyncEnabled = it }
                importData.syncIntervalMinutes?.let { settingsRepository.syncIntervalMinutes = it }
                importData.syncMode?.let { settingsRepository.syncMode = it }
                importData.ttsLanguage?.let { settingsRepository.ttsLanguage = it }
                importData.ttsVoiceName?.let { settingsRepository.ttsVoiceName = it }
                importData.smartPredictionDelay?.let { settingsRepository.smartPredictionDelay = it }
                importData.keepScreenOnUserMode?.let { settingsRepository.keepScreenOnUserMode = it }
                importData.userModeScreenBehavior?.let { settingsRepository.userModeScreenBehavior = it }
                importData.themeMode?.let { settingsRepository.themeMode = it }
                importData.securityPinTimeoutMinutes?.let { settingsRepository.securityPinTimeoutMinutes = it }
                importData.isPinRequiredForDeletion?.let { settingsRepository.isPinRequiredForDeletion = it }
                importData.isBiometricEnabled?.let { settingsRepository.isBiometricEnabled = it }
                importData.isSecurityRequiredForEdit?.let { settingsRepository.isSecurityRequiredForEdit = it }
                importData.isSecurityRequiredForSettings?.let { settingsRepository.isSecurityRequiredForSettings = it }
                importData.startupBehavior?.let { settingsRepository.startupBehavior = it }
                importData.weatherCacheTimeout?.let { settingsRepository.weatherCacheTimeout = it }
                importData.securityPinHash?.let { settingsRepository.securityPinHash = it }
                importData.securityPinSalt?.let { settingsRepository.securityPinSalt = it }
                importData.appLanguage?.let { settingsRepository.appLanguage = it }
                importData.isNotificationReadingEnabled?.let { settingsRepository.isNotificationReadingEnabled = it }
                importData.monitoredNotificationApps?.let { settingsRepository.monitoredNotificationApps = it.toSet() }
                importData.showPageIdInLog?.let { settingsRepository.showPageIdInLog = it }
                importData.bluetoothDelay?.let { settingsRepository.bluetoothDelay = it }
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
                    val action = importButton.action?.let { importAction(it, idMap) }
                    
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

    private fun importAction(importAction: ImportAction, idMap: Map<String, String>): ButtonAction? {
        val type = importAction.type.uppercase()
        @Suppress("SpellCheckingInspection")
        return when (type) {
            "SPEAK", "SPEAKTEXT" -> SpeakTextButtonAction()
            "NAVIGATE", "NAVIGATETOPAGE" -> {
                val oldId = importAction.targetPageId ?: importAction.targetPageImportId ?: ""
                NavigateToPageButtonAction(idMap[oldId] ?: oldId)
            }
            "GEMINI" -> GeminiButtonAction(importAction.prompt ?: "")
            "GEMINI_SEARCH" -> GeminiSearchButtonAction(importAction.prompt ?: "")
            "GEMINI_NANO" -> GeminiNanoButtonAction(importAction.intent ?: "")
            "GEMINI_VISION" -> com.andreas_kratzer.ghosttalk.core.model.GeminiVisionButtonAction(importAction.prompt ?: "", importAction.useCloud ?: false)
            "SMART_PREDICTION" -> SmartPredictionButtonAction(importAction.rank ?: 1)
            "DEVICE_CONTROL" -> {
                val typeName = importAction.deviceActionType ?: "READ_TIME"
                ControlDeviceButtonAction(
                    actionType = try { DeviceActionType.valueOf(typeName) } catch(_: Exception) { DeviceActionType.READ_TIME },
                    volumeValue = importAction.volumeValue,
                    contactName = importAction.contactName,
                    contactPhone = importAction.contactPhone,
                    messageText = importAction.messageText
                )
            }
            "WEATHER" -> WeatherButtonAction()
            "GOOGLE_HOME" -> SmartHomeButtonAction(
                provider = SmartHomeProvider.GOOGLE_HOME,
                deviceId = importAction.googleHomeDeviceId ?: "",
                intent = importAction.googleHomeCommand ?: "",
                value = importAction.googleHomeValue
            )
            "SMART_HOME" -> SmartHomeButtonAction(
                provider = SmartHomeProvider.valueOf(importAction.smartHomeProvider ?: "GOOGLE_HOME"),
                deviceId = importAction.smartHomeDeviceId ?: "",
                deviceName = importAction.smartHomeDeviceName ?: "",
                intent = importAction.smartHomeIntent ?: "",
                value = importAction.smartHomeValue
            )
            else -> null
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
}

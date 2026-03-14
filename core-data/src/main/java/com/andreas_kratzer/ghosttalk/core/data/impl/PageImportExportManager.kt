package com.andreas_kratzer.ghosttalk.core.data.impl

import android.content.Context
import com.andreas_kratzer.ghosttalk.core.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction
import com.andreas_kratzer.ghosttalk.core.model.DeviceActionType
import com.andreas_kratzer.ghosttalk.core.model.FrequentActionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiNanoButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiSearchButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.SmartPredictionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.core.model.WeatherButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GoogleHomeButtonAction
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.export.PageImportExportProvider
import com.andreas_kratzer.ghosttalk.core.model.importexport.*
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
    @ApplicationContext private val context: Context,
    private val pageRepository: PageRepository,
    private val bookRepository: BookRepository,
    private val settingsRepository: SettingsRepository
) : PageImportExportProvider {
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

    private fun exportAction(action: ButtonAction, spokenText: String? = null): ImportAction {
        return when (action) {
            is SpeakTextButtonAction -> ImportAction(type = "SPEAK", textToSpeech = spokenText)
            is NavigateToPageButtonAction -> ImportAction(type = "NAVIGATE", targetPageId = action.pageId, targetPageImportId = action.pageId)
            is FrequentActionButtonAction -> ImportAction(type = "SMART_PREDICTION", rank = action.rank)
            is GeminiButtonAction -> ImportAction(type = "GEMINI", prompt = action.prompt)
            is GeminiSearchButtonAction -> ImportAction(type = "GEMINI_SEARCH", prompt = action.prompt)
            is GeminiNanoButtonAction -> ImportAction(type = "GEMINI_NANO", intent = action.intent)
            is SmartPredictionButtonAction -> ImportAction(type = "SMART_PREDICTION", rank = action.rank)
            is ControlDeviceButtonAction -> ImportAction(
                type = "DEVICE_CONTROL",
                deviceActionType = action.actionType.name,
                volumeValue = action.volumeValue,
                contactName = action.contactName,
                contactPhone = action.contactPhone,
                messageText = action.messageText
            )
            is WeatherButtonAction -> ImportAction(type = "WEATHER")
            is GoogleHomeButtonAction -> ImportAction(
                type = "GOOGLE_HOME",
                googleHomeDeviceId = action.deviceId,
                googleHomeTrait = action.trait,
                googleHomeCommand = action.command,
                googleHomeValue = action.value
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
            
            // 1. Update book and app settings if provided
            importData.holdingTimeSeconds?.let { 
                settingsRepository.holdingTimeMillis = (it * 1000).toLong()
            }
            importData.bookName?.let { newName ->
                bookRepository.getBookById(bookId)?.let { book ->
                    bookRepository.updateBook(book.copy(
                        name = newName,
                        createdAt = importData.bookCreatedAt ?: book.createdAt
                    ))
                }
            }

            val idMap = mutableMapOf<String, String>()
            
            // 2. Determine ID regeneration needs
            val sourceBookId = importData.bookId
            val forceRegeneration = regenerateIds || (sourceBookId != null && sourceBookId != bookId)

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
            Result.success(importData.pages.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun importAction(importAction: ImportAction, idMap: Map<String, String>): ButtonAction? {
        val type = importAction.type?.uppercase() ?: return null
        return when (type) {
            "SPEAK", "SPEAKTEXT" -> SpeakTextButtonAction()
            "NAVIGATE", "NAVIGATETOPAGE" -> {
                val oldId = importAction.targetPageId ?: importAction.targetPageImportId ?: ""
                NavigateToPageButtonAction(idMap[oldId] ?: oldId)
            }
            "GEMINI" -> GeminiButtonAction(importAction.prompt ?: "")
            "GEMINI_SEARCH" -> GeminiSearchButtonAction(importAction.prompt ?: "")
            "GEMINI_NANO" -> GeminiNanoButtonAction(importAction.intent ?: "")
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
            "GOOGLE_HOME" -> GoogleHomeButtonAction(
                deviceId = importAction.googleHomeDeviceId ?: "",
                trait = importAction.googleHomeTrait ?: "",
                command = importAction.googleHomeCommand ?: "",
                value = importAction.googleHomeValue
            )
            else -> null
        }
    }

    override suspend fun extractBookIdFromJson(jsonString: String): String? = withContext(Dispatchers.Default) {
        try {
            val root = json.parseToJsonElement(jsonString) as? JsonObject
            root?.get("bookId")?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
    }

    suspend fun extractBookNameFromJson(jsonString: String): String? = withContext(Dispatchers.Default) {
        try {
            val root = json.parseToJsonElement(jsonString) as? JsonObject
            root?.get("bookName")?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun importCloudBackup(jsonString: String, cloudFileId: String?): Result<String> = withContext(Dispatchers.IO) {
        try {
            val importData = json.decodeFromString<ImportExportData>(jsonString)
            
            // Extract bookId from name if missing from field (e.g. "Name [uuid]")
            val extractedId = importData.bookId ?: run {
                val name = importData.bookName ?: ""
                val regex = "\\[([a-fA-F0-9-]{36})\\]".toRegex()
                regex.find(name)?.groupValues?.get(1)
            }

            if (extractedId == null && cloudFileId == null) {
                return@withContext Result.failure(Exception("Konnte keine Buch-ID im Backup finden."))
            }

            val targetBookId = cloudFileId ?: extractedId!!
            
            val existingBook = bookRepository.getBookById(targetBookId)
            if (existingBook != null && cloudFileId == null) {
                return@withContext Result.failure(Exception("Ein Buch mit dieser ID existiert bereits lokal."))
            }

            if (existingBook == null) {
                val newBook = com.andreas_kratzer.ghosttalk.core.model.Book(
                    id = targetBookId,
                    name = importData.bookName ?: "Importiertes Buch",
                    createdAt = System.currentTimeMillis()
                )
                bookRepository.insertBook(newBook)
            }

            importFromJson(jsonString, targetBookId, regenerateIds = false)
            Result.success(targetBookId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

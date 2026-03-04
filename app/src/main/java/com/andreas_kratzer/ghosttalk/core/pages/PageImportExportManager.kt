package com.andreas_kratzer.ghosttalk.core.pages

import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.model.FrequentActionButtonAction
import com.andreas_kratzer.ghosttalk.model.PageTemplate
import com.andreas_kratzer.ghosttalk.data.TemplateRepository
import com.andreas_kratzer.ghosttalk.model.importexport.ImportExportData
import com.andreas_kratzer.ghosttalk.model.importexport.ImportPage
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.util.UUID

class PageImportExportManager @javax.inject.Inject constructor(
    private val pageRepository: PageRepository,
    private val settingsRepository: SettingsRepository,
    private val templateRepository: TemplateRepository,
    private val logger: Logger,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val gson = Gson()

    suspend fun importFromJson(jsonString: String, bookId: String): Result<Int> = importBookFromJson(jsonString, bookId)

    suspend fun importBookFromJson(jsonString: String, bookId: String): Result<Int> = withContext(ioDispatcher) {
        try {
            logger.d("PageImportExportManager", "Starting import mapping parsing for book $bookId...")
            val importData = gson.fromJson(jsonString, ImportExportData::class.java)

            if (importData.pages.isEmpty()) {
                logger.e("PageImportExportManager", "Parsed JSON was invalid or missing 'pages'")
                return@withContext Result.failure(Exception("Ungültiges JSON-Format. Seiten fehlen."))
            }

            // Sync holding time if present
            importData.holdingTimeSeconds?.let { seconds ->
                settingsRepository.holdingTimeMillis = (seconds * 1000).toLong()
                logger.d("PageImportExportManager", "Updated holdingTimeMillis to ${settingsRepository.holdingTimeMillis}")
            }

            // Map UUIDs for incoming pages first, so templates with Navigation actions can reference them
            val pageIdMap = mutableMapOf<String, String>()
            importData.pages.forEach { p ->
                pageIdMap[p.importId] = UUID.randomUUID().toString()
            }

            // Handle Templates
            importData.templates?.forEach { importTemplate ->
                if (!importTemplate.isBuiltIn) {
                    val templateButtonConfigs = (0 until (importTemplate.rows * importTemplate.columns)).map { index ->
                        val importButton = importTemplate.buttons.find { it.index.toInt() == index }
                        if (importButton == null || (importButton.label.isBlank() && importButton.action == null)) {
                            null
                        } else {
                            val importAction = importButton.action
                            val action = importAction?.let { ia ->
                                when (ia.type) {
                                    "SpeakText", "SPEAK" -> SpeakTextButtonAction()
                                    "FrequentAction" -> {
                                        val rank = ia.targetPageImportId?.toIntOrNull() ?: 1
                                        FrequentActionButtonAction(rank)
                                    }
                                    "NavigateToPage", "NAVIGATE" -> {
                                        val targetId = pageIdMap[ia.targetPageImportId] ?: ia.targetPageImportId ?: ""
                                        NavigateToPageButtonAction(targetId)
                                    }
                                    else -> null
                                }
                            }
                            if (importButton.label.isBlank() || action == null) {
                                null
                            } else {
                                ButtonConfig(
                                    id = UUID.randomUUID().toString(),
                                    label = importButton.label,
                                    spokenText = importButton.spokenText 
                                        ?: importAction.textToSpeech 
                                        ?: importAction.ttsFeedback,
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
                        rows = importTemplate.rows,
                        columns = importTemplate.columns,
                        buttonConfigs = templateButtonConfigs,
                        isBuiltIn = false
                    )
                    templateRepository.insert(pageTemplate)
                }
            }

            logger.d("PageImportExportManager", "Parsed ${importData.pages.size} pages. Committing to Room DB...")

            val newPages = importData.pages.map { importPage ->
                val newPageId = pageIdMap[importPage.importId] ?: UUID.randomUUID().toString()
                
                val buttonConfigs = (0 until (importPage.rows * importPage.columns)).map { index ->
                    val importButton = importPage.buttons.find { it.index.toInt() == index }
                    if (importButton == null || (importButton.label.isBlank() && importButton.action == null)) {
                        null
                    } else {
                        val importAction = importButton.action
                        val action = importAction?.let { ia ->
                            when (ia.type) {
                                "SpeakText", "SPEAK" -> SpeakTextButtonAction()
                                "FrequentAction" -> {
                                    val rank = ia.targetPageImportId?.toIntOrNull() ?: 1
                                    FrequentActionButtonAction(rank)
                                }
                                "NavigateToPage", "NAVIGATE" -> {
                                    val targetId = pageIdMap[ia.targetPageImportId] ?: ia.targetPageImportId ?: ""
                                    NavigateToPageButtonAction(targetId)
                                }
                                else -> null
                            }
                        }

                        if (importButton.label.isBlank() || action == null) {
                            null
                        } else {
                            ButtonConfig(
                                id = UUID.randomUUID().toString(),
                                label = importButton.label,
                            spokenText = importButton.spokenText 
                                ?: importAction.textToSpeech 
                                ?: importAction.ttsFeedback,
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
                    rows = importPage.rows,
                    columns = importPage.columns,
                    scanPattern = null,
                    rowNames = emptyList(),
                    buttonConfigs = buttonConfigs
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

    suspend fun exportBookToJson(bookId: String): String = withContext(ioDispatcher) {
        val pages = pageRepository.getPagesForBook(bookId)
        exportToJson(pages)
    }

    suspend fun exportToJson(pages: List<Page>): String = withContext(ioDispatcher) {
        val importPages = pages.map { page ->
            val buttons = page.buttonConfigs.mapIndexedNotNull { index, config ->
                config?.let {
                    val importAction = when (val action = it.buttonAction) {
                        is SpeakTextButtonAction -> com.andreas_kratzer.ghosttalk.model.importexport.ImportAction(
                            type = "SPEAK",
                            textToSpeech = it.spokenText,
                            targetPageImportId = null,
                            ttsFeedback = null
                        )
                        is FrequentActionButtonAction -> com.andreas_kratzer.ghosttalk.model.importexport.ImportAction(
                            type = "FrequentAction",
                            textToSpeech = null,
                            targetPageImportId = action.rank.toString(),
                            ttsFeedback = null
                        )
                        is NavigateToPageButtonAction -> com.andreas_kratzer.ghosttalk.model.importexport.ImportAction(
                            type = "NAVIGATE",
                            textToSpeech = null,
                            targetPageImportId = action.pageId,
                            ttsFeedback = null
                        )
                        else -> null // Skip exporting app-specific actions for standard format
                    }
                    com.andreas_kratzer.ghosttalk.model.importexport.ImportButton(
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
            ImportPage(
                importId = page.id,
                name = page.name,
                rows = page.rows,
                columns = page.columns,
                buttons = buttons
            )
        }
        
        // Export custom templates
        val allTemplates = templateRepository.getAllTemplates().first()
        val importTemplates = allTemplates.filter { !it.isBuiltIn }.map { template ->
            val buttons = template.buttonConfigs.mapIndexedNotNull { index, config ->
                config?.let {
                    val importAction = when (val action = it.buttonAction) {
                        is SpeakTextButtonAction -> com.andreas_kratzer.ghosttalk.model.importexport.ImportAction(
                            type = "SPEAK",
                            textToSpeech = it.spokenText,
                            targetPageImportId = null,
                            ttsFeedback = null
                        )
                        is FrequentActionButtonAction -> com.andreas_kratzer.ghosttalk.model.importexport.ImportAction(
                            type = "FrequentAction",
                            textToSpeech = null,
                            targetPageImportId = action.rank.toString(),
                            ttsFeedback = null
                        )
                        is NavigateToPageButtonAction -> com.andreas_kratzer.ghosttalk.model.importexport.ImportAction(
                            type = "NAVIGATE",
                            textToSpeech = null,
                            targetPageImportId = action.pageId,
                            ttsFeedback = null
                        )
                        else -> null // Skip exporting app-specific actions for standard format
                    }
                    com.andreas_kratzer.ghosttalk.model.importexport.ImportButton(
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
            com.andreas_kratzer.ghosttalk.model.importexport.ImportTemplate(
                id = template.id,
                name = template.name,
                rows = template.rows,
                columns = template.columns,
                isBuiltIn = template.isBuiltIn,
                buttons = buttons
            )
        }

        val exportData = ImportExportData(
            ghosttalk_import_version = "1.0",
            appName = "GhosTTalk (Export)",
            holdingTimeSeconds = settingsRepository.holdingTimeMillis / 1000f,
            templates = importTemplates,
            pages = importPages
        )
        gson.toJson(exportData)
    }
}

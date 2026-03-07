package com.andreas_kratzer.ghosttalk.core.pages

import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.data.TemplateRepository
import com.andreas_kratzer.ghosttalk.model.*
import com.andreas_kratzer.ghosttalk.model.importexport.*
import com.andreas_kratzer.ghosttalk.ui.util.GridUtils
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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
                                        val targetId = pageIdMap[ia.targetPageImportId] ?: ia.targetPageImportId ?: ""
                                        NavigateToPageButtonAction(targetId)
                                    }
                                    else -> null
                                }
                            }
                            if (importButton.label.isNotBlank() && action != null) {
                                templateButtonConfigs[globalIdx] = ButtonConfig(
                                    id = UUID.randomUUID().toString(),
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
                        buttonConfigs = templateButtonConfigs,
                        isBuiltIn = false
                    )
                    templateRepository.insert(pageTemplate)
                }
            }

            logger.d("PageImportExportManager", "Parsed ${importData.pages.size} pages. Committing to Room DB...")

            val newPages = importData.pages.map { importPage ->
                val newPageId = pageIdMap[importPage.importId] ?: UUID.randomUUID().toString()
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
                                    val targetId = pageIdMap[ia.targetPageImportId] ?: ia.targetPageImportId ?: ""
                                    NavigateToPageButtonAction(targetId)
                                }
                                else -> null
                            }
                        }
                        if (importButton.label.isNotBlank() && action != null) {
                            buttonConfigs[globalIdx] = ButtonConfig(
                                id = UUID.randomUUID().toString(),
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
                    templateId = null,
                    rows = rows,
                    columns = columns,
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
                rows = page.rows,
                columns = page.columns,
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

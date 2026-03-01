package com.example.gostalk.core

import com.example.gostalk.core.util.Logger
import com.example.gostalk.data.PageRepository
import com.example.gostalk.model.Page
import com.example.gostalk.model.importexport.ImportExportData
import com.example.gostalk.model.importexport.ImportPage
import com.example.gostalk.model.ButtonConfig
import com.example.gostalk.model.SpeakTextButtonAction
import com.example.gostalk.model.NavigateToPageButtonAction
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class PageImportExportManager(
    private val pageRepository: PageRepository,
    private val logger: Logger,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val gson = Gson()

    suspend fun importFromJson(jsonString: String, bookId: String): Result<Int> = withContext(ioDispatcher) {
        try {
            logger.d("PageImportExportManager", "Starting import mapping parsing...")
            val importData = gson.fromJson(jsonString, ImportExportData::class.java)

            if (importData.pages.isNullOrEmpty()) {
                logger.e("PageImportExportManager", "Parsed JSON was invalid or missing 'pages'")
                return@withContext Result.failure(Exception("Ungültiges JSON-Format. Seiten fehlen."))
            }

            logger.d("PageImportExportManager", "Parsed ${importData.pages.size} pages. Committing to Room DB...")

            // Mapping logic extracted from PageViewModel
            val pageIdMap = mutableMapOf<String, String>()
            importData.pages.forEach { p ->
                pageIdMap[p.importId] = UUID.randomUUID().toString()
            }

            val newPages = importData.pages.map { importPage ->
                val newPageId = pageIdMap[importPage.importId] ?: UUID.randomUUID().toString()
                
                val buttonConfigs = (0 until (importPage.rows * importPage.columns)).map { index ->
                    val importButton = importPage.buttons.find { it.index.toInt() == index }
                    if (importButton == null || (importButton.label.isBlank() && importButton.action == null)) {
                        null
                    } else {
                        val action = importButton.action?.let { importAction ->
                            when (importAction.type) {
                                "SpeakText", "SPEAK" -> SpeakTextButtonAction(importAction.textToSpeech ?: "")
                                "NavigateToPage", "NAVIGATE" -> {
                                    val targetId = pageIdMap[importAction.targetPageImportId] ?: importAction.targetPageImportId ?: ""
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
                                buttonAction = action,
                                auditoryCue = null,
                                isActive = importButton.active ?: true
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

    suspend fun exportToJson(pages: List<Page>): String = withContext(ioDispatcher) {
        val importPages = pages.map { page ->
            val buttons = page.buttonConfigs.mapIndexedNotNull { index, config ->
                config?.let {
                    val importAction = when (val action = it.buttonAction) {
                        is com.example.gostalk.model.SpeakTextButtonAction -> com.example.gostalk.model.importexport.ImportAction(
                            type = "SPEAK",
                            textToSpeech = action.textToSpeech,
                            targetPageImportId = null,
                            ttsFeedback = null
                        )
                        is com.example.gostalk.model.NavigateToPageButtonAction -> com.example.gostalk.model.importexport.ImportAction(
                            type = "NAVIGATE",
                            textToSpeech = null,
                            targetPageImportId = action.pageId,
                            ttsFeedback = null
                        )
                        else -> null
                    }
                    com.example.gostalk.model.importexport.ImportButton(
                        index = index.toLong(),
                        label = it.label,
                        auditoryCueText = null,
                        action = importAction,
                        active = it.isActive
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
        val exportData = ImportExportData(
            gostalk_import_version = "1.0",
            appName = "GoSTalk (Export)",
            pages = importPages
        )
        gson.toJson(exportData)
    }
}

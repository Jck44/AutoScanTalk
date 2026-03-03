package com.andreas_kratzer.ghosttalk.core

import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.importexport.ImportExportData
import com.andreas_kratzer.ghosttalk.model.importexport.ImportPage
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.model.NavigateToPageButtonAction
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class PageImportExportManager @javax.inject.Inject constructor(
    private val pageRepository: PageRepository,
    private val logger: Logger,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val gson = Gson()

    suspend fun importFromJson(jsonString: String, bookId: String): Result<Int> = importBookFromJson(jsonString, bookId)

    suspend fun importBookFromJson(jsonString: String, bookId: String): Result<Int> = withContext(ioDispatcher) {
        try {
            logger.d("PageImportExportManager", "Starting import mapping parsing for book $bookId...")
            val importData = gson.fromJson(jsonString, ImportExportData::class.java)

            if (importData.pages.isNullOrEmpty()) {
                logger.e("PageImportExportManager", "Parsed JSON was invalid or missing 'pages'")
                return@withContext Result.failure(Exception("Ungültiges JSON-Format. Seiten fehlen."))
            }

            // Optional: Lösche alte Seiten des Buches, falls wir ein komplettes Restore machen (für Sync)
            // pageRepository.deletePagesForBook(bookId) 

            logger.d("PageImportExportManager", "Parsed ${importData.pages.size} pages. Committing to Room DB...")

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
                            textToSpeech = action.textToSpeech,
                            targetPageImportId = null,
                            ttsFeedback = null
                        )
                        is NavigateToPageButtonAction -> com.andreas_kratzer.ghosttalk.model.importexport.ImportAction(
                            type = "NAVIGATE",
                            textToSpeech = null,
                            targetPageImportId = action.pageId,
                            ttsFeedback = null
                        )
                        else -> null
                    }
                    com.andreas_kratzer.ghosttalk.model.importexport.ImportButton(
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
            ghosttalk_import_version = "1.0",
            appName = "GhosTTalk (Export)",
            pages = importPages
        )
        gson.toJson(exportData)
    }
}

package com.andreas_kratzer.ghosttalk.core.data.impl.importexport

import android.content.Context
import androidx.core.content.edit
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.ButtonTemplateRepository
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.export.ImportResult
import com.andreas_kratzer.ghosttalk.core.data.impl.ActionMapper
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsMapper
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.core.model.importexport.ImportExportData
import com.andreas_kratzer.ghosttalk.core.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookJsonImporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val pageRepository: PageRepository,
    private val bookRepository: BookRepository,
    private val settingsRepository: SettingsRepository,
    private val settingsMapper: SettingsMapper,
    private val actionMapper: ActionMapper,
    private val buttonTemplateRepository: ButtonTemplateRepository,
    private val deletedEntityDao: com.andreas_kratzer.ghosttalk.core.database.DeletedEntityDao,
    private val logger: Logger
) {
    private val TAG = "BookJsonImporter"

    suspend fun importFromJson(
        jsonString: String,
        bookId: String,
        regenerateIds: Boolean,
        restoreSyncSettings: Boolean
    ): Result<ImportResult> = withContext(Dispatchers.IO) {
        try {
            val importData = ImportExportJson.decodeFromString<ImportExportData>(jsonString)
            logger.d(TAG, "Importing JSON for book $bookId: defaultStartPageId='${importData.defaultStartPageId}', bookName='${importData.bookName}'")

            val warnings = mutableListOf<String>()

            pageRepository.runInTransaction {
                // 1. Clear and restore tombstones
                clearAndRestoreTombstones(bookId, importData)

                // 2. Apply book meta and settings
                applyBookMeta(bookId, importData, restoreSyncSettings)

                // 3. Build ID maps
                val (idMap, regeneratedPages, forceRegeneration) = buildIdMap(bookId, importData, regenerateIds)

                // 4. Restore book scoped preferences
                restoreBookScopedPrefs(bookId, importData, idMap)

                val restoredStartPageId = importData.defaultStartPageId?.let { oldId ->
                    idMap[oldId] ?: oldId
                } ?: settingsRepository.getDefaultStartPageIdForBook(bookId)

                // 5. Import pages
                importPages(bookId, importData, idMap, regeneratedPages, restoredStartPageId, forceRegeneration, warnings)

                // 6. Import templates
                importTemplates(importData, idMap, restoredStartPageId, restoreSyncSettings, warnings)

                // 7. Ensure static row
                ensureStaticRow(bookId)

                // 8. Purge install update buttons
                pageRepository.purgeInstallUpdateButtons()
            }

            settingsRepository.refresh()
            logger.d(TAG, "Triggered settingsRepository.refresh() after import.")

            if (warnings.isNotEmpty()) {
                logger.w(TAG, "Import für Buch $bookId abgeschlossen mit ${warnings.size} Warnung(en): $warnings")
            }
            Result.success(ImportResult(pageCount = importData.pages.size, warnings = warnings))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun clearAndRestoreTombstones(bookId: String, importData: ImportExportData) {
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
    }

    private suspend fun applyBookMeta(bookId: String, importData: ImportExportData, restoreSyncSettings: Boolean) {
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
        
        importData.bookUpdatedAt?.let { timestamp ->
            bookRepository.updateLastModified(bookId, timestamp, incrementSequence = false)
        }
    }

    private suspend fun buildIdMap(bookId: String, importData: ImportExportData, regenerateIds: Boolean): Triple<Map<String, String>, Set<String>, Boolean> {
        val idMap = mutableMapOf<String, String>()
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
        return Triple(idMap, regeneratedPages, forceRegeneration)
    }

    private fun restoreBookScopedPrefs(bookId: String, importData: ImportExportData, idMap: Map<String, String>) {
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

        importData.favoriteBookId?.let { oldFavId ->
            if (oldFavId == importData.bookId) {
                settingsRepository.favoriteBookId = bookId
            }
        }
    }

    private suspend fun importPages(
        bookId: String,
        importData: ImportExportData,
        idMap: Map<String, String>,
        regeneratedPages: Set<String>,
        restoredStartPageId: String?,
        forceRegeneration: Boolean,
        warnings: MutableList<String>
    ) {
        importData.pages.forEach { importPage ->
            val newPageId = idMap[importPage.importId]!!
            val buttons = MutableList<ButtonConfig?>(49) { null }
            
            importPage.buttons.forEach { importButton ->
                val action = importButton.action?.let { actionMapper.importAction(it, idMap, restoredStartPageId) }
                
                if (importButton.label.isEmpty() && action == null && importButton.auditoryCueText == null && importButton.audioFileName == null) {
                    return@forEach
                }

                val finalAction = action ?: SpeakTextButtonAction()
                val pageRegenerated = regeneratedPages.contains(importPage.importId)
                val config = buildButtonConfigFromImport(
                    button = importButton,
                    forceRegeneration = forceRegeneration,
                    pageRegenerated = pageRegenerated,
                    finalAction = finalAction,
                    warnings = warnings,
                    contextDescription = "Seite '${importPage.name}'"
                )
                
                val isGhostTalk = importData.appName == "GhostTalk" || importData.ghosttalk_import_version != null
                val globalIndex = mapImportIndexToGrid(isGhostTalk, importButton.index, importPage.columns)
                
                if (globalIndex < buttons.size) {
                    buttons[globalIndex] = config
                }
            }

            val (finalRows, finalCols) = autoExpandGrid(importPage.rows, importPage.columns, importPage.buttons)

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
            pageRepository.insertPageRaw(page)
        }
    }

    private suspend fun importTemplates(
        importData: ImportExportData,
        idMap: Map<String, String>,
        restoredStartPageId: String?,
        restoreSyncSettings: Boolean,
        warnings: MutableList<String>
    ) {
        val importButtonTemplates = importData.buttonTemplates
        if (restoreSyncSettings && importButtonTemplates != null) {
            logger.d(TAG, "Importing Button Templates...")
            importButtonTemplates.forEach { importTemplate ->
                val button = importTemplate.button
                if (button != null) {
                    val action = button.action?.let { actionMapper.importAction(it, idMap, restoredStartPageId) } ?: SpeakTextButtonAction()
                    val config = buildButtonConfigFromImport(
                        button = button,
                        forceRegeneration = false,
                        pageRegenerated = false,
                        finalAction = action,
                        warnings = warnings,
                        contextDescription = "Vorlage '${importTemplate.name}'"
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
    }

    private suspend fun ensureStaticRow(bookId: String) {
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
    }
}

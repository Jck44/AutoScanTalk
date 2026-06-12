package com.andreas_kratzer.ghosttalk.core.data.impl.importexport

import android.content.Context
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.ButtonTemplateRepository
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.impl.ActionMapper
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsMapper
import com.andreas_kratzer.ghosttalk.core.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.importexport.ExportedTombstone
import com.andreas_kratzer.ghosttalk.core.model.importexport.ImportButton
import com.andreas_kratzer.ghosttalk.core.model.importexport.ImportButtonTemplate
import com.andreas_kratzer.ghosttalk.core.model.importexport.ImportExportData
import com.andreas_kratzer.ghosttalk.core.model.importexport.ImportPage
import com.andreas_kratzer.ghosttalk.core.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookJsonExporter @Inject constructor(
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
    private val TAG = "BookJsonExporter"

    suspend fun exportPageListToJson(pages: List<Page>): String = withContext(Dispatchers.IO) {
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
        ImportExportJson.encodeToString(ImportExportData.serializer(), exportData)
    }

    suspend fun exportBookToJson(bookId: String, includeSettings: Boolean): String = withContext(Dispatchers.IO) {
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
        
        val cutoff = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
        val tombstones = deletedEntityDao.getDeletedEntitiesForBookSince(bookId, cutoff).map {
            ExportedTombstone(
                entityId = it.entityId,
                entityType = it.entityType,
                deletedAt = it.deletedAt
            )
        }

        val localVersionCode = try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(pInfo)
        } catch (_: Exception) {
            null
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
            app_version_code = localVersionCode,
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
            logicalVersion = null,
            deletedEntities = tombstones.sortedBy { it.entityId }
        )

        val exportData = if (includeSettings) {
            settingsMapper.exportSettings(bookId, baseExportData)
        } else {
            baseExportData
        }

        logger.d(TAG, "Exported book $bookId: defaultStartPageId='${exportData.defaultStartPageId}', scanDelay='${exportData.scanDelayMillis}'")
        val serialized = ImportExportJson.encodeToString(ImportExportData.serializer(), exportData)

        try {
            deletedEntityDao.pruneTombstones(cutoff)
        } catch (e: Exception) {
            logger.e(TAG, "Pruning alter Tombstones nach Export fehlgeschlagen (nicht fatal)", e)
        }

        serialized
    }
}

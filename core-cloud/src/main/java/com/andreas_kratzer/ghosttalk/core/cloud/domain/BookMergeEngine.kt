package com.andreas_kratzer.ghosttalk.core.cloud.domain

import com.andreas_kratzer.ghosttalk.core.model.importexport.ImportExportData
import com.andreas_kratzer.ghosttalk.core.model.importexport.ImportPage
import com.andreas_kratzer.ghosttalk.core.util.Logger

class BookMergeEngine(private val logger: Logger) {
    private val TAG = "BookMergeEngine"

    fun mergeBooks(local: ImportExportData, remote: ImportExportData): ImportExportData {
        val useLocalMetadata = (local.bookUpdatedAt ?: 0L) >= (remote.bookUpdatedAt ?: 0L)
        val mergedBookName = if (useLocalMetadata) local.bookName else remote.bookName
        val mergedDefaultStartPageId = if (useLocalMetadata) local.defaultStartPageId else remote.defaultStartPageId
        val mergedPageSortOrder = if (useLocalMetadata) local.pageSortOrder else remote.pageSortOrder
        val mergedTemplateSortOrder = if (useLocalMetadata) local.templateSortOrder else remote.templateSortOrder
        val mergedActionLogLimit = if (useLocalMetadata) local.actionLogLimit else remote.actionLogLimit
        val mergedLimitScanCycles = if (useLocalMetadata) local.limitScanCycles else remote.limitScanCycles
        val mergedScanCycleLimit = if (useLocalMetadata) local.scanCycleLimit else remote.scanCycleLimit
        val mergedLogIgnoredActions = if (useLocalMetadata) local.logIgnoredActions else remote.logIgnoredActions
        val mergedLogStopActions = if (useLocalMetadata) local.logStopActions else remote.logStopActions

        val localPagesMap = local.pages.associateBy { it.importId }
        val remotePagesMap = remote.pages.associateBy { it.importId }

        val localTombstones = local.deletedEntities?.associateBy { it.entityId } ?: emptyMap()
        val remoteTombstones = remote.deletedEntities?.associateBy { it.entityId } ?: emptyMap()

        val allPageIds = localPagesMap.keys + remotePagesMap.keys
        val mergedPages = allPageIds.mapNotNull { pageId ->
            val localPage = localPagesMap[pageId]
            val remotePage = remotePagesMap[pageId]

            if (localPage == null && remotePage != null) {
                val tombstone = localTombstones[pageId]
                if (tombstone != null) {
                    val remoteTime = remotePage.updatedAt ?: remotePage.createdAt ?: 0L
                    if (tombstone.deletedAt >= remoteTime) null else remotePage
                } else {
                    remotePage
                }
            } else if (remotePage == null && localPage != null) {
                val tombstone = remoteTombstones[pageId]
                if (tombstone != null) {
                    val localTime = localPage.updatedAt ?: localPage.createdAt ?: 0L
                    if (tombstone.deletedAt >= localTime) null else localPage
                } else {
                    localPage
                }
            } else if (localPage != null && remotePage != null) {
                val localPageTime = localPage.updatedAt ?: localPage.createdAt ?: 0L
                val remotePageTime = remotePage.updatedAt ?: remotePage.createdAt ?: 0L
                val useLocalPage = localPageTime >= remotePageTime

                val name = if (useLocalPage) localPage.name else remotePage.name
                val rows = if (useLocalPage) localPage.rows else remotePage.rows
                val columns = if (useLocalPage) localPage.columns else remotePage.columns
                val templateId = if (useLocalPage) localPage.templateId else remotePage.templateId
                val scanPattern = if (useLocalPage) localPage.scanPattern else remotePage.scanPattern
                val rowNames = if (useLocalPage) localPage.rowNames else remotePage.rowNames
                val orderIndex = if (useLocalPage) localPage.orderIndex else remotePage.orderIndex
                val createdAt = if (useLocalPage) localPage.createdAt else remotePage.createdAt
                val updatedAt = maxOf(localPageTime, remotePageTime)

                val localButtonsMap = localPage.buttons.associateBy { it.index }
                val remoteButtonsMap = remotePage.buttons.associateBy { it.index }
                val allButtonIndices = localButtonsMap.keys + remoteButtonsMap.keys
                val mergedButtons = allButtonIndices.mapNotNull { index ->
                    val localButton = localButtonsMap[index]
                    val remoteButton = remoteButtonsMap[index]

                    if (localButton == null && remoteButton != null) {
                        val remoteTime = remoteButton.updatedAt ?: 0L
                        if (localPageTime >= remoteTime) null else remoteButton
                    } else if (remoteButton == null && localButton != null) {
                        val localTime = localButton.updatedAt ?: 0L
                        if (remotePageTime >= localTime) null else localButton
                    } else if (localButton != null && remoteButton != null) {
                        val localButtonTime = localButton.updatedAt ?: 0L
                        val remoteButtonTime = remoteButton.updatedAt ?: 0L
                        if (localButtonTime >= remoteButtonTime) localButton else remoteButton
                    } else {
                        null
                    }
                }

                ImportPage(
                    importId = pageId,
                    name = name,
                    rows = rows,
                    columns = columns,
                    templateId = templateId,
                    scanPattern = scanPattern,
                    rowNames = rowNames,
                    orderIndex = orderIndex,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    buttons = mergedButtons
                )
            } else {
                null
            }
        }

        val localTemplatesMap = (local.buttonTemplates ?: emptyList()).associateBy { it.id }
        val remoteTemplatesMap = (remote.buttonTemplates ?: emptyList()).associateBy { it.id }
        val allTemplateIds = localTemplatesMap.keys + remoteTemplatesMap.keys
        val mergedButtonTemplates = allTemplateIds.map { id ->
            val localT = localTemplatesMap[id]
            val remoteT = remoteTemplatesMap[id]
            if (localT == null) {
                remoteT!!
            } else if (remoteT == null) {
                localT
            } else {
                val localTime = localT.button?.updatedAt ?: 0L
                val remoteTime = remoteT.button?.updatedAt ?: 0L
                if (localTime >= remoteTime) localT else remoteT
            }
        }

        val mergedTombstones = if (local.deletedEntities == null && remote.deletedEntities == null) {
            null
        } else {
            val cutoff = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000 // 90 days
            (local.deletedEntities.orEmpty() + remote.deletedEntities.orEmpty())
                .associateBy { it.entityId }
                .filterKeys { pageId -> mergedPages.none { it.importId == pageId } }
                .values
                .filter { it.deletedAt >= cutoff }
                .toList()
        }

        return local.copy(
            bookName = mergedBookName,
            defaultStartPageId = mergedDefaultStartPageId,
            pageSortOrder = mergedPageSortOrder,
            templateSortOrder = mergedTemplateSortOrder,
            actionLogLimit = mergedActionLogLimit,
            limitScanCycles = mergedLimitScanCycles,
            scanCycleLimit = mergedScanCycleLimit,
            logIgnoredActions = mergedLogIgnoredActions,
            logStopActions = mergedLogStopActions,
            bookUpdatedAt = maxOf(local.bookUpdatedAt ?: 0L, remote.bookUpdatedAt ?: 0L),
            pages = mergedPages,
            buttonTemplates = mergedButtonTemplates,
            deletedEntities = mergedTombstones
        )
    }

    fun calculateStructuralMd5FromJson(jsonStr: String): String {
        return try {
            val jsonParser = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }
            val data = jsonParser.decodeFromString<ImportExportData>(jsonStr)
            val cleanData = data.copy(
                bookUpdatedAt = 0L,
                versionSequence = 0L,
                sourceDevice = null,
                isCloudSyncEnabled = null,
                syncIntervalMinutes = null,
                syncModeBook = null,
                syncModeTts = null,
                syncModeStats = null,
                syncMode = null
            )
            val cleanJson = jsonParser.encodeToString(ImportExportData.serializer(), cleanData)
            val messageDigest = java.security.MessageDigest.getInstance("MD5")
            val hashBytes = messageDigest.digest(cleanJson.toByteArray(Charsets.UTF_8))
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            logger.e(TAG, "Failed to calculate structural MD5 from JSON", e)
            ""
        }
    }
}

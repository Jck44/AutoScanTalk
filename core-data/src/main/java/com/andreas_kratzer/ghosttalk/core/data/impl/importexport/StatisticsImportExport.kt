package com.andreas_kratzer.ghosttalk.core.data.impl.importexport

import android.content.Context
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.UserModeSessionRepository
import com.andreas_kratzer.ghosttalk.core.data.impl.ZipArchiver
import com.andreas_kratzer.ghosttalk.core.database.ButtonUsageDao
import com.andreas_kratzer.ghosttalk.core.model.importexport.ExportedButtonStat
import com.andreas_kratzer.ghosttalk.core.model.importexport.ExportedHistoryEvent
import com.andreas_kratzer.ghosttalk.core.model.importexport.ExportedStatistics
import com.andreas_kratzer.ghosttalk.core.model.importexport.ExportedUserModeSession
import com.andreas_kratzer.ghosttalk.core.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatisticsImportExport @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val buttonUsageDao: ButtonUsageDao,
    private val userModeSessionRepository: UserModeSessionRepository,
    private val settingsRepository: SettingsRepository,
    private val zipArchiver: ZipArchiver,
    private val logger: Logger
) {
    private val TAG = "StatisticsImportExport"

    suspend fun exportStatisticsToJson(bookId: String): String = withContext(Dispatchers.IO) {
        val historyList = buttonUsageDao.getHistoryForBook(bookId).first()
        val statsList = buttonUsageDao.getAllStatsForBook(bookId)
        val sessionsList = userModeSessionRepository.getSessionsForBook(bookId).firstOrNull() ?: emptyList()
        
        val exportedStats = statsList.map { stat ->
            ExportedButtonStat(
                buttonConfigId = stat.buttonConfigId,
                pageId = stat.pageId,
                label = stat.label,
                actionJson = stat.actionJson,
                usageCount = stat.usageCount,
                lastUsedAt = stat.lastUsedAt
            )
        }
        
        val exportedHistory = historyList.map { event ->
            ExportedHistoryEvent(
                timestamp = event.timestamp,
                label = event.label,
                actionType = event.actionType,
                buttonId = event.buttonId,
                pageId = event.pageId,
                imagePath = event.imagePath,
                geminiResponse = event.geminiResponse,
                latitude = event.latitude,
                longitude = event.longitude,
                sessionId = event.sessionId,
                reactionTimeMs = event.reactionTimeMs,
                isTouchIntervention = event.isTouchIntervention,
                wifiSsid = event.wifiSsid,
                isHardwareTriggered = event.isHardwareTriggered
            )
        }

        val exportedSessions = sessionsList.map { session ->
            ExportedUserModeSession(
                startTime = session.startTime,
                endTime = session.endTime
            )
        }
        
        val appVerName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Exception) {
            null
        }

        val statistics = ExportedStatistics(
            bookId = bookId,
            history = exportedHistory,
            stats = exportedStats,
            statsVersion = 1,
            appVersion = appVerName,
            userModeSessions = exportedSessions,
            sourceDevice = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        )
        ImportExportJson.encodeToString(ExportedStatistics.serializer(), statistics)
    }

    suspend fun importStatisticsFromJson(jsonString: String, bookId: String) = withContext(Dispatchers.IO) {
        try {
            val statistics = ImportExportJson.decodeFromString<ExportedStatistics>(jsonString)
            
            // Clean slate first
            buttonUsageDao.clearHistoryForBook(bookId)
            buttonUsageDao.clearStatsForBook(bookId)
            userModeSessionRepository.clearSessions(bookId)
            
            // Re-insert history events
            statistics.history.forEach { event ->
                val mappedPageId = event.pageId?.let { 
                    if (it.startsWith("static_row_")) "static_row_$bookId" else it 
                }
                val entity = com.andreas_kratzer.ghosttalk.core.database.ButtonUsageHistoryEntity(
                    bookId = bookId,
                    timestamp = event.timestamp,
                    label = event.label,
                    actionType = event.actionType,
                    buttonId = event.buttonId,
                    pageId = mappedPageId,
                    imagePath = event.imagePath,
                    geminiResponse = event.geminiResponse,
                    latitude = event.latitude,
                    longitude = event.longitude,
                    sessionId = event.sessionId,
                    reactionTimeMs = event.reactionTimeMs,
                    isTouchIntervention = event.isTouchIntervention,
                    wifiSsid = event.wifiSsid,
                    isHardwareTriggered = event.isHardwareTriggered
                )
                buttonUsageDao.insertHistoryEvent(entity)
            }
            
            // Re-insert stats counters
            statistics.stats.forEach { stat ->
                val mappedPageId = if (stat.pageId.startsWith("static_row_")) {
                    "static_row_$bookId"
                } else {
                    stat.pageId
                }
                val entity = com.andreas_kratzer.ghosttalk.core.model.ButtonUsageStat(
                    bookId = bookId,
                    buttonConfigId = stat.buttonConfigId,
                    pageId = mappedPageId,
                    label = stat.label,
                    actionJson = stat.actionJson,
                    usageCount = stat.usageCount,
                    lastUsedAt = stat.lastUsedAt
                )
                buttonUsageDao.upsert(entity)
            }

            // Re-insert user mode sessions
            statistics.userModeSessions?.let { sessions ->
                val domainSessions = sessions.map {
                    com.andreas_kratzer.ghosttalk.core.model.UserModeSession(
                        id = 0L,
                        bookId = bookId,
                        startTime = it.startTime,
                        endTime = it.endTime
                    )
                }
                userModeSessionRepository.insertSessions(domainSessions)
            }

            logger.d(TAG, "Imported statistics for book $bookId: ${statistics.history.size} history events, ${statistics.stats.size} stats counter, ${statistics.userModeSessions?.size ?: 0} user mode sessions.")
        } catch (e: Exception) {
            logger.e(TAG, "Failed to import statistics for book $bookId", e)
        }
    }

    suspend fun exportStatisticsToZip(
        bookId: String,
        outputStream: OutputStream
    ) = withContext(Dispatchers.IO) {
        val statsJson = exportStatisticsToJson(bookId)
        zipArchiver.zip(outputStream, mapOf("statistics.json" to statsJson), emptyList())
    }

    suspend fun importStatisticsFromZip(
        bookId: String,
        inputStream: InputStream
    ) = withContext(Dispatchers.IO) {
        val handler = object : ZipArchiver.UnzipHandler {
            override fun handleStringEntry(name: String, content: String) {
                if (name == "statistics.json") {
                    kotlinx.coroutines.runBlocking {
                        importStatisticsFromJson(content, bookId)
                    }
                }
            }
            override fun handleFileEntry(name: String, time: Long, inputStream: InputStream, targetFile: File) {}
        }
        zipArchiver.unzip(inputStream, emptyMap(), handler)
    }

    suspend fun getStatisticsLastModified(bookId: String): Long = withContext(Dispatchers.IO) {
        val lastHistoryTime = buttonUsageDao.getLastHistoryEvent(bookId)?.timestamp ?: 0L
        val lastStatTime = buttonUsageDao.getAllStatsForBook(bookId).maxOfOrNull { it.lastUsedAt } ?: 0L
        val lastSessionTime = userModeSessionRepository.getSessionsForBook(bookId).firstOrNull()?.maxOfOrNull { it.endTime } ?: 0L
        maxOf(lastHistoryTime, lastStatTime, lastSessionTime)
    }
}

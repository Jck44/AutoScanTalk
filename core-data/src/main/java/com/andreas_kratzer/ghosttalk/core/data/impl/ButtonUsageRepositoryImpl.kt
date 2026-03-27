package com.andreas_kratzer.ghosttalk.core.data.impl

import com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.core.database.ButtonUsageDao
import com.andreas_kratzer.ghosttalk.core.database.ButtonUsageHistoryEntity
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ButtonUsageStat
import com.andreas_kratzer.ghosttalk.core.model.GroupedButtonUsageStat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Repository for tracking button usage statistics per book.
 * Uses an aggregated counter approach (one row per button per book).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ButtonUsageRepositoryImpl @Inject constructor(
    private val dao: ButtonUsageDao,
    private val settingsRepository: com.andreas_kratzer.ghosttalk.core.data.SettingsRepository,
    @param:com.andreas_kratzer.ghosttalk.core.di.ApplicationScope private val scope: kotlinx.coroutines.CoroutineScope
) : ButtonUsageRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val buttonHistory: StateFlow<List<ButtonUsageRepository.ButtonUsageEvent>> = 
        settingsRepository.activeBookIdFlow
            .flatMapLatest { bookId ->
                if (bookId == null) kotlinx.coroutines.flow.flowOf(emptyList())
                else dao.getHistoryForBook(bookId).map { entities ->
                    entities.map { it.toDomain() }
                }
            }
            .stateIn(
                scope = scope,
                started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    /**
     * Records a button press. Increments the usage counter or creates a new entry.
     */
    override suspend fun recordUsage(bookId: String, pageId: String, buttonConfig: ButtonConfig, rows: Int, columns: Int, indexInPage: Int) {
        val existing = dao.getStatForButton(bookId, buttonConfig.id)
        val stat = if (existing != null) {
            existing.copy(
                label = buttonConfig.label,
                actionJson = json.encodeToString(buttonConfig.buttonAction),
                pageId = pageId, // Update pageId (last used location)
                usageCount = existing.usageCount + 1,
                lastUsedAt = System.currentTimeMillis()
            )
        } else {
            ButtonUsageStat(
                bookId = bookId,
                buttonConfigId = buttonConfig.id,
                pageId = pageId,
                label = buttonConfig.label,
                actionJson = json.encodeToString(buttonConfig.buttonAction),
                usageCount = 1,
                lastUsedAt = System.currentTimeMillis()
            )
        }
        dao.upsert(stat)

        // Persistent history event
        val event = ButtonUsageHistoryEntity(
            bookId = bookId,
            timestamp = System.currentTimeMillis(),
            label = buttonConfig.label,
            actionType = buttonConfig.buttonAction::class.simpleName ?: "Unknown",
            buttonId = buttonConfig.id,
            pageId = pageId
        )
        dao.insertHistoryEvent(event)

        // Pruning based on active book settings
        val limit = settingsRepository.actionLogLimit 
        dao.pruneHistory(bookId, limit)
    }

    override suspend fun updateLastEventImage(imagePath: String) {
        val bookId = settingsRepository.activeBookId
        val lastEvent = dao.getLastHistoryEvent(bookId)
        lastEvent?.let {
            dao.updateHistoryEvent(it.copy(imagePath = imagePath))
        }
    }

    override suspend fun updateLastEventDetails(details: String) {
        val bookId = settingsRepository.activeBookId
        val lastEvent = dao.getLastHistoryEvent(bookId)
        lastEvent?.let {
            dao.updateHistoryEvent(it.copy(geminiResponse = details))
        }
    }

    override suspend fun deleteUsageEvent(timestamp: Long) {
        dao.deleteHistoryEventByTimestamp(timestamp)
    }

    /**
     * Returns the top N most frequently used buttons for a book.
     */
    override suspend fun getTopActions(bookId: String, limit: Int): List<ButtonUsageStat> {
        return dao.getTopButtons(bookId, limit)
    }

    override suspend fun getGroupedUsageStats(bookId: String): List<GroupedButtonUsageStat> {
        val allStats = dao.getAllStatsForBook(bookId)
        
        return allStats
            .groupBy { it.label.lowercase() to it.actionJson }
            .map { (key, children) ->
                GroupedButtonUsageStat(
                    label = children.maxByOrNull { it.lastUsedAt }?.label ?: children.first().label,
                    actionJson = key.second,
                    totalCount = children.sumOf { it.usageCount },
                    lastUsedAt = children.maxOf { it.lastUsedAt },
                    children = children.sortedByDescending { it.usageCount }
                )
            }
            .sortedByDescending { it.totalCount }
    }

    /**
     * Clears all statistics for a book.
     */
    override suspend fun clearStats(bookId: String) {
        dao.clearHistoryForBook(bookId)
        dao.clearStatsForBook(bookId)
    }

    private fun ButtonUsageHistoryEntity.toDomain() = ButtonUsageRepository.ButtonUsageEvent(
        timestamp = timestamp,
        label = label,
        actionType = actionType,
        imagePath = imagePath,
        buttonId = buttonId,
        pageId = pageId,
        geminiResponse = geminiResponse
    )
}

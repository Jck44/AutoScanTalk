package com.andreas_kratzer.ghosttalk.core.data.impl

import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ButtonUsageStat
import com.andreas_kratzer.ghosttalk.core.model.FrequentActionButtonAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.core.database.ButtonUsageDao
import javax.inject.Inject

/**
 * Repository for tracking button usage statistics per book.
 * Uses an aggregated counter approach (one row per button per book).
 */
class ButtonUsageRepositoryImpl @Inject constructor(
    private val dao: ButtonUsageDao
) : ButtonUsageRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _buttonHistory = MutableStateFlow<List<ButtonUsageRepository.ButtonUsageEvent>>(emptyList())
    override val buttonHistory: StateFlow<List<ButtonUsageRepository.ButtonUsageEvent>> = _buttonHistory.asStateFlow()

    /**
     * Records a button press. Increments the usage counter or creates a new entry.
     */
    override suspend fun recordUsage(bookId: String, pageId: String, buttonConfig: ButtonConfig, rows: Int, columns: Int, indexInPage: Int) {
        // ... (lines 34-67 are same)
        val existing = dao.getStatForButton(bookId, buttonConfig.id)
        val stat = if (existing != null) {
            existing.copy(
                label = buttonConfig.label,
                actionJson = json.encodeToString(buttonConfig.buttonAction),
                usageCount = existing.usageCount + 1,
                lastUsedAt = System.currentTimeMillis()
            )
        } else {
            ButtonUsageStat(
                bookId = bookId,
                buttonConfigId = buttonConfig.id,
                label = buttonConfig.label,
                actionJson = json.encodeToString(buttonConfig.buttonAction),
                usageCount = 1,
                lastUsedAt = System.currentTimeMillis()
            )
        }
        dao.upsert(stat)

        // Update in-memory history
        _buttonHistory.update { current ->
            val newEvent = ButtonUsageRepository.ButtonUsageEvent(
                timestamp = System.currentTimeMillis(),
                label = buttonConfig.label,
                actionType = buttonConfig.buttonAction::class.simpleName ?: "Unknown",
                buttonId = buttonConfig.id,
                pageId = pageId
            )
            val combined = listOf(newEvent) + current
            val keep = combined.take(15)
            
            // Delete images of dropped events
            if (combined.size > 15) {
                combined.drop(15).forEach { dropped ->
                    dropped.imagePath?.let { path ->
                        try {
                            java.io.File(path).delete()
                        } catch (_: Exception) {}
                    }
                }
            }
            keep
        }
    }

    override suspend fun updateLastEventImage(imagePath: String) {
        _buttonHistory.update { current ->
            if (current.isEmpty()) return@update current
            val last = current.first()
            listOf(last.copy(imagePath = imagePath)) + current.drop(1)
        }
    }

    /**
     * Returns the top N most frequently used buttons for a book.
     */
    override suspend fun getTopActions(bookId: String, limit: Int): List<ButtonUsageStat> {
        return dao.getTopButtons(bookId, limit)
    }

    /**
     * Clears all statistics for a book.
     */
    override suspend fun clearStats(bookId: String) {
        _buttonHistory.update { current ->
            current.forEach { event ->
                event.imagePath?.let { path ->
                    try {
                        java.io.File(path).delete()
                    } catch (_: Exception) {}
                }
            }
            emptyList()
        }
        dao.clearStatsForBook(bookId)
    }
}

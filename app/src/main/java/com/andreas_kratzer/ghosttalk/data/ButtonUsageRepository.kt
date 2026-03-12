package com.andreas_kratzer.ghosttalk.data

import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ButtonUsageStat
import com.andreas_kratzer.ghosttalk.core.model.FrequentActionButtonAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * Repository for tracking button usage statistics per book.
 * Uses an aggregated counter approach (one row per button per book).
 */
class ButtonUsageRepository @Inject constructor(
    private val dao: ButtonUsageDao
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    data class ButtonUsageEvent(val timestamp: Long, val label: String, val actionType: String)

    private val _buttonHistory = MutableStateFlow<List<ButtonUsageEvent>>(emptyList())
    val buttonHistory: StateFlow<List<ButtonUsageEvent>> = _buttonHistory.asStateFlow()

    /**
     * Records a button press. Increments the usage counter or creates a new entry.
     */
    suspend fun recordUsage(bookId: String, buttonConfig: ButtonConfig, rows: Int, columns: Int, indexInPage: Int) {
        // Ignore inactive buttons
        if (!buttonConfig.isActive) {
            return
        }

        // Ignore FrequentActionButtonAction to prevent ranking loops
        if (buttonConfig.buttonAction is FrequentActionButtonAction) {
            return
        }

        // Only record if visible in the current grid configuration
        if (!com.andreas_kratzer.ghosttalk.core.util.GridUtils.isVisibleInGrid(indexInPage, rows, columns)) {
            return
        }
        
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
            val newEvent = ButtonUsageEvent(
                timestamp = System.currentTimeMillis(),
                label = buttonConfig.label,
                actionType = buttonConfig.buttonAction::class.simpleName ?: "Unknown"
            )
            (listOf(newEvent) + current).take(15)
        }
    }

    /**
     * Returns the top N most frequently used buttons for a book.
     */
    suspend fun getTopActions(bookId: String, limit: Int): List<ButtonUsageStat> {
        return dao.getTopButtons(bookId, limit)
    }

    /**
     * Clears all statistics for a book.
     */
    suspend fun clearStats(bookId: String) {
        dao.clearStatsForBook(bookId)
    }
}

package com.andreas_kratzer.ghosttalk.data

import com.andreas_kratzer.ghosttalk.model.ButtonAction
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.ButtonUsageStat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import javax.inject.Inject

/**
 * Repository for tracking button usage statistics per book.
 * Uses an aggregated counter approach (one row per button per book).
 */
class ButtonUsageRepository @Inject constructor(
    private val dao: ButtonUsageDao
) {
    data class ButtonUsageEvent(val timestamp: Long, val label: String, val actionType: String)

    private val _buttonHistory = MutableStateFlow<List<ButtonUsageEvent>>(emptyList())
    val buttonHistory: StateFlow<List<ButtonUsageEvent>> = _buttonHistory.asStateFlow()

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(ButtonAction::class.java, ButtonActionAdapter())
        .create()

    /**
     * Records a button press. Increments the usage counter or creates a new entry.
     */
    suspend fun recordUsage(bookId: String, buttonConfig: ButtonConfig, rows: Int, columns: Int, indexInPage: Int) {
        // Ignore inactive buttons
        if (!buttonConfig.isActive) {
            return
        }

        // Ignore FrequentActionButtonAction to prevent ranking loops
        if (buttonConfig.buttonAction is com.andreas_kratzer.ghosttalk.model.FrequentActionButtonAction) {
            return
        }

        // Only record if visible in the current grid configuration
        if (!com.andreas_kratzer.ghosttalk.ui.util.GridUtils.isVisibleInGrid(indexInPage, rows, columns)) {
            return
        }
        
        val existing = dao.getStatForButton(bookId, buttonConfig.id)
        val stat = if (existing != null) {
            existing.copy(
                label = buttonConfig.label,
                actionJson = gson.toJson(buttonConfig.buttonAction, ButtonAction::class.java),
                usageCount = existing.usageCount + 1,
                lastUsedAt = System.currentTimeMillis()
            )
        } else {
            ButtonUsageStat(
                bookId = bookId,
                buttonConfigId = buttonConfig.id,
                label = buttonConfig.label,
                actionJson = gson.toJson(buttonConfig.buttonAction, ButtonAction::class.java),
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

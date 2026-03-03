package com.andreas_kratzer.ghosttalk.data

import com.andreas_kratzer.ghosttalk.model.ButtonAction
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.ButtonUsageStat
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
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(ButtonAction::class.java, ButtonActionAdapter())
        .create()

    /**
     * Records a button press. Increments the usage counter or creates a new entry.
     */
    suspend fun recordUsage(bookId: String, buttonConfig: ButtonConfig) {
        // Ignore FrequentActionButtonAction to prevent ranking loops
        if (buttonConfig.buttonAction is com.andreas_kratzer.ghosttalk.model.FrequentActionButtonAction) {
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

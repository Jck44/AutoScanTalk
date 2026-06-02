package com.andreas_kratzer.ghosttalk.core.model

import androidx.room.Entity
import androidx.room.Index

/**
 * Tracks how often each button is used within a specific book.
 * One row per unique button per book (aggregated counter, not event log).
 */
@Entity(
    tableName = "button_usage_stats",
    primaryKeys = ["bookId", "buttonConfigId"],
    indices = [Index(value = ["bookId", "usageCount"])]
)
data class ButtonUsageStat(
    val bookId: String,
    val buttonConfigId: String,
    val pageId: String, // Store pageId where button was used
    val label: String,
    val actionJson: String,
    val usageCount: Long = 0,
    val firstUsedAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis()
)

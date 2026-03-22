package com.andreas_kratzer.ghosttalk.core.database

import androidx.room.*

@Entity(
    tableName = "button_usage_history",
    indices = [Index(value = ["bookId", "timestamp"])]
)
data class ButtonUsageHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val timestamp: Long,
    val label: String,
    val actionType: String,
    val imagePath: String? = null,
    val buttonId: String? = null,
    val pageId: String? = null,
    val geminiResponse: String? = null
)

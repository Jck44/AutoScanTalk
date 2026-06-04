package com.andreas_kratzer.ghosttalk.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "button_usage_history",
    indices = [
        Index(value = ["bookId", "timestamp"]),
        Index(value = ["bookId", "buttonId", "timestamp"])
    ]
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
    val geminiResponse: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val sessionId: Long? = null,
    val reactionTimeMs: Long? = null,
    val isTouchIntervention: Boolean = false,
    val wifiSsid: String? = null,
    val isHardwareTriggered: Boolean = false,
    val scanCyclesBeforeClick: Int? = null,
    val isAccidental: Boolean = false,
    val intendedButtonId: String? = null
)

package com.andreas_kratzer.ghosttalk.core.data

import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ButtonUsageStat
import kotlinx.coroutines.flow.StateFlow

interface ButtonUsageRepository : ButtonUsageProvider {
    val buttonHistory: StateFlow<List<ButtonUsageEvent>>

    suspend fun recordUsage(bookId: String, buttonConfig: ButtonConfig, rows: Int, columns: Int, indexInPage: Int)
    suspend fun clearStats(bookId: String)

    data class ButtonUsageEvent(val timestamp: Long, val label: String, val actionType: String)
}

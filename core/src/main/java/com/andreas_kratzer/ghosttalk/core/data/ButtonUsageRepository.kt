package com.andreas_kratzer.ghosttalk.core.data

import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.GroupedButtonUsageStat
import kotlinx.coroutines.flow.StateFlow

interface ButtonUsageRepository : ButtonUsageProvider {
    val buttonHistory: StateFlow<List<ButtonUsageEvent>>

    suspend fun getGroupedUsageStats(bookId: String): List<GroupedButtonUsageStat>

    suspend fun recordUsage(bookId: String, pageId: String, buttonConfig: ButtonConfig, rows: Int, columns: Int, indexInPage: Int)
    suspend fun clearStats(bookId: String)
    suspend fun updateLastEventImage(imagePath: String)
    suspend fun updateLastEventDetails(details: String)
    suspend fun deleteUsageEvent(timestamp: Long)

    data class ButtonUsageEvent(
        val timestamp: Long, 
        val label: String, 
        val actionType: String,
        val imagePath: String? = null,
        val buttonId: String? = null,
        val pageId: String? = null,
        val geminiResponse: String? = null
    )
}

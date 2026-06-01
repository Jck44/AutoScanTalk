package com.andreas_kratzer.ghosttalk.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.andreas_kratzer.ghosttalk.core.model.ButtonUsageStat

@Dao
interface ButtonUsageDao {

    @Query("""
        SELECT 
            bookId, 
            MIN(buttonConfigId) as buttonConfigId, 
            MIN(pageId) as pageId, 
            MAX(label) as label, 
            actionJson, 
            SUM(usageCount) as usageCount, 
            MAX(lastUsedAt) as lastUsedAt 
        FROM button_usage_stats 
        WHERE bookId = :bookId 
        GROUP BY LOWER(label), actionJson 
        ORDER BY usageCount DESC 
        LIMIT :limit
    """)
    suspend fun getTopButtons(bookId: String, limit: Int): List<ButtonUsageStat>

    @Query("SELECT * FROM button_usage_stats WHERE bookId = :bookId AND buttonConfigId = :buttonId")
    suspend fun getStatForButton(bookId: String, buttonId: String): ButtonUsageStat?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stat: ButtonUsageStat)

    @Query("SELECT * FROM button_usage_stats WHERE bookId = :bookId")
    suspend fun getAllStatsForBook(bookId: String): List<ButtonUsageStat>

    @Query("DELETE FROM button_usage_stats WHERE bookId = :bookId")
    suspend fun clearStatsForBook(bookId: String)

    // History
    @Query("SELECT * FROM button_usage_history WHERE bookId = :bookId ORDER BY timestamp DESC")
    fun getHistoryForBook(bookId: String): kotlinx.coroutines.flow.Flow<List<ButtonUsageHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryEvent(event: ButtonUsageHistoryEntity)

    @Update
    suspend fun updateHistoryEvent(event: ButtonUsageHistoryEntity)

    @Query("DELETE FROM button_usage_history WHERE timestamp = :timestamp")
    suspend fun deleteHistoryEventByTimestamp(timestamp: Long)

    @Query("DELETE FROM button_usage_history WHERE bookId = :bookId")
    suspend fun clearHistoryForBook(bookId: String)

    @Query("SELECT * FROM button_usage_history WHERE bookId = :bookId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastHistoryEvent(bookId: String): ButtonUsageHistoryEntity?

    @Query("DELETE FROM button_usage_history WHERE bookId = :bookId AND id NOT IN (SELECT id FROM button_usage_history WHERE bookId = :bookId ORDER BY timestamp DESC LIMIT :limit)")
    suspend fun pruneHistory(bookId: String, limit: Int)

    @Query("DELETE FROM button_usage_history WHERE timestamp < :threshold")
    suspend fun pruneHistoryByTimestamp(threshold: Long)

    @Query("DELETE FROM button_usage_stats WHERE lastUsedAt < :threshold")
    suspend fun pruneStatsByTimestamp(threshold: Long)
}

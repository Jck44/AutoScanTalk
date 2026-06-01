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

    // Smarte lokale Statistik-Queries (Markov, Zeit, Ort)
    @Query("""
        SELECT successor.buttonId AS buttonId, COUNT(successor.buttonId) AS count
        FROM button_usage_history AS anchor
        JOIN button_usage_history AS successor ON successor.bookId = anchor.bookId 
          AND successor.timestamp > anchor.timestamp
        WHERE anchor.bookId = :bookId
          AND anchor.buttonId = :lastButtonId
          AND anchor.timestamp >= :sinceTimestamp
          AND NOT EXISTS (
              SELECT 1 FROM button_usage_history AS middle
              WHERE middle.bookId = anchor.bookId
                AND middle.timestamp > anchor.timestamp
                AND middle.timestamp < successor.timestamp
          )
          AND successor.buttonId IS NOT NULL
        GROUP BY successor.buttonId
        ORDER BY count DESC
        LIMIT :limit
    """)
    suspend fun getMostFrequentNextButtons(bookId: String, lastButtonId: String, sinceTimestamp: Long, limit: Int): List<SuccessorCount>

    @Query("""
        SELECT buttonId
        FROM button_usage_history
        WHERE bookId = :bookId
          AND timestamp >= :sinceTimestamp
          AND buttonId IS NOT NULL
          AND CAST(strftime('%w', datetime(timestamp / 1000, 'unixepoch', 'localtime')) AS INTEGER) = :dayOfWeek
          AND CAST(strftime('%H', datetime(timestamp / 1000, 'unixepoch', 'localtime')) AS INTEGER) >= :startHour
          AND CAST(strftime('%H', datetime(timestamp / 1000, 'unixepoch', 'localtime')) AS INTEGER) < :endHour
        GROUP BY buttonId
        ORDER BY COUNT(buttonId) DESC
        LIMIT :limit
    """)
    suspend fun getMostFrequentButtonsForContext(
        bookId: String,
        dayOfWeek: Int,
        startHour: Int,
        endHour: Int,
        sinceTimestamp: Long,
        limit: Int
    ): List<String>

    @Query("""
        SELECT buttonId
        FROM button_usage_history
        WHERE bookId = :bookId
          AND timestamp >= :sinceTimestamp
          AND buttonId IS NOT NULL
          AND latitude IS NOT NULL
          AND longitude IS NOT NULL
          AND ABS(latitude - :lat) <= :radiusDeg
          AND ABS(longitude - :lng) <= :radiusDeg
        GROUP BY buttonId
        ORDER BY COUNT(buttonId) DESC
        LIMIT :limit
    """)
    suspend fun getMostFrequentButtonsAtLocation(
        bookId: String,
        lat: Double,
        lng: Double,
        radiusDeg: Double,
        sinceTimestamp: Long,
        limit: Int
    ): List<String>

    @Query("""
        SELECT buttonId
        FROM button_usage_history
        WHERE bookId = :bookId
          AND timestamp >= :sinceTimestamp
          AND buttonId IS NOT NULL
          AND latitude IS NULL
          AND longitude IS NULL
        GROUP BY buttonId
        ORDER BY COUNT(buttonId) DESC
        LIMIT :limit
    """)
    suspend fun getMostFrequentButtonsAtNullLocation(
        bookId: String,
        sinceTimestamp: Long,
        limit: Int
    ): List<String>
}

data class SuccessorCount(
    val buttonId: String,
    val count: Int
)


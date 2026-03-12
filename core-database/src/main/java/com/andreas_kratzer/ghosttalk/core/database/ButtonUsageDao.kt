package com.andreas_kratzer.ghosttalk.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.andreas_kratzer.ghosttalk.core.model.ButtonUsageStat

@Dao
interface ButtonUsageDao {

    @Query("SELECT * FROM button_usage_stats WHERE bookId = :bookId ORDER BY usageCount DESC LIMIT :limit")
    suspend fun getTopButtons(bookId: String, limit: Int): List<ButtonUsageStat>

    @Query("SELECT * FROM button_usage_stats WHERE bookId = :bookId AND buttonConfigId = :buttonId")
    suspend fun getStatForButton(bookId: String, buttonId: String): ButtonUsageStat?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stat: ButtonUsageStat)

    @Query("DELETE FROM button_usage_stats WHERE bookId = :bookId")
    suspend fun clearStatsForBook(bookId: String)
}

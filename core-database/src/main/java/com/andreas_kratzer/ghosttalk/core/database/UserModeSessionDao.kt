package com.andreas_kratzer.ghosttalk.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserModeSessionDao {
    @Query("SELECT * FROM user_mode_sessions WHERE bookId = :bookId ORDER BY startTime DESC")
    fun getSessionsForBook(bookId: String): Flow<List<UserModeSessionEntity>>

    @Query("SELECT * FROM user_mode_sessions WHERE bookId = :bookId ORDER BY startTime DESC")
    suspend fun getSessionsForBookList(bookId: String): List<UserModeSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: UserModeSessionEntity): Long

    @Query("UPDATE user_mode_sessions SET endTime = :endTime WHERE id = :id")
    suspend fun updateSessionEndTime(id: Long, endTime: Long)

    @Query("DELETE FROM user_mode_sessions WHERE bookId = :bookId")
    suspend fun clearSessionsForBook(bookId: String)

    @Query("DELETE FROM user_mode_sessions WHERE bookId = :bookId AND id NOT IN (SELECT id FROM user_mode_sessions WHERE bookId = :bookId ORDER BY startTime DESC LIMIT :limit)")
    suspend fun pruneSessions(bookId: String, limit: Int)
}

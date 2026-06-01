package com.andreas_kratzer.ghosttalk.core.data

import com.andreas_kratzer.ghosttalk.core.model.UserModeSession
import kotlinx.coroutines.flow.Flow

interface UserModeSessionRepository {
    fun getSessionsForBook(bookId: String): Flow<List<UserModeSession>>
    suspend fun startSession(bookId: String): Long
    suspend fun updateActiveSession(id: Long, endTime: Long)
    suspend fun clearSessions(bookId: String)
    suspend fun insertSessions(sessions: List<UserModeSession>)
}

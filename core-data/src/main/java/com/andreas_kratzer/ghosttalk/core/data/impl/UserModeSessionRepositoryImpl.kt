package com.andreas_kratzer.ghosttalk.core.data.impl

import com.andreas_kratzer.ghosttalk.core.data.UserModeSessionRepository
import com.andreas_kratzer.ghosttalk.core.database.UserModeSessionDao
import com.andreas_kratzer.ghosttalk.core.database.UserModeSessionEntity
import com.andreas_kratzer.ghosttalk.core.model.UserModeSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserModeSessionRepositoryImpl @Inject constructor(
    private val dao: UserModeSessionDao
) : UserModeSessionRepository {

    override fun getSessionsForBook(bookId: String): Flow<List<UserModeSession>> {
        return dao.getSessionsForBook(bookId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun startSession(bookId: String): Long {
        val now = System.currentTimeMillis()
        val session = UserModeSessionEntity(
            bookId = bookId,
            startTime = now,
            endTime = now
        )
        val id = dao.insertSession(session)
        dao.pruneSessions(bookId, 100)
        return id
    }

    override suspend fun updateActiveSession(id: Long, endTime: Long) {
        dao.updateSessionEndTime(id, endTime)
    }

    override suspend fun clearSessions(bookId: String) {
        dao.clearSessionsForBook(bookId)
    }
}

package com.andreas_kratzer.ghosttalk.core.database

import com.andreas_kratzer.ghosttalk.core.data.impl.UserModeSessionRepositoryImpl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserModeSessionRepositoryTest {

    private val mockDao = mockk<UserModeSessionDao>(relaxed = true)
    private lateinit var repository: UserModeSessionRepositoryImpl

    @Before
    fun setup() {
        repository = UserModeSessionRepositoryImpl(mockDao)
    }

    @Test
    fun `getSessionsForBook maps entities to domain models`() = runTest {
        val entities = listOf(
            UserModeSessionEntity(id = 1L, bookId = "book1", startTime = 1000L, endTime = 2000L),
            UserModeSessionEntity(id = 2L, bookId = "book1", startTime = 3000L, endTime = 4000L)
        )
        every { mockDao.getSessionsForBook("book1") } returns flowOf(entities)

        val result = repository.getSessionsForBook("book1").first()

        assertEquals(2, result.size)
        assertEquals(1L, result[0].id)
        assertEquals("book1", result[0].bookId)
        assertEquals(1000L, result[0].startTime)
        assertEquals(2000L, result[0].endTime)
    }

    @Test
    fun `startSession inserts new session and prunes old ones`() = runTest {
        coEvery { mockDao.insertSession(any()) } returns 42L

        val sessionId = repository.startSession("book1")

        assertEquals(42L, sessionId)
        coVerify { 
            mockDao.insertSession(match { it.bookId == "book1" && it.startTime == it.endTime }) 
            mockDao.pruneSessions("book1", 100)
        }
    }

    @Test
    fun `updateActiveSession calls updateSessionEndTime`() = runTest {
        repository.updateActiveSession(42L, 9999L)
        coVerify { mockDao.updateSessionEndTime(42L, 9999L) }
    }

    @Test
    fun `clearSessions calls clearSessionsForBook`() = runTest {
        repository.clearSessions("book1")
        coVerify { mockDao.clearSessionsForBook("book1") }
    }
}

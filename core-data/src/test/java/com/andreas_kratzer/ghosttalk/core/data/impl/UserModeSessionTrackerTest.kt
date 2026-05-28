package com.andreas_kratzer.ghosttalk.core.data.impl

import com.andreas_kratzer.ghosttalk.core.data.AppStateRepository
import com.andreas_kratzer.ghosttalk.core.data.UserModeSessionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserModeSessionTrackerTest {

    private val mockAppStateRepository = mockk<AppStateRepository>(relaxed = true)
    private val mockSessionRepository = mockk<UserModeSessionRepository>(relaxed = true)
    
    private val isUserModeActiveFlow = MutableStateFlow(false)
    private val activeBookIdFlow = MutableStateFlow<String?>("book1")

    @Before
    fun setup() {
        every { mockAppStateRepository.isUserModeActive } returns isUserModeActiveFlow
        every { mockAppStateRepository.activeBookId } returns activeBookIdFlow
    }

    @Test
    fun `tracker starts a session when isUserModeActive becomes true`() = runTest {
        val tracker = UserModeSessionTracker(
            appStateRepository = mockAppStateRepository,
            sessionRepository = mockSessionRepository,
            scope = backgroundScope
        )
        coEvery { mockSessionRepository.startSession("book1") } returns 101L

        tracker.start()
        testScheduler.runCurrent()

        isUserModeActiveFlow.value = true
        testScheduler.runCurrent()

        coVerify(exactly = 1) { mockSessionRepository.startSession("book1") }
    }

    @Test
    fun `tracker updates the session end time periodically while active`() = runTest {
        val tracker = UserModeSessionTracker(
            appStateRepository = mockAppStateRepository,
            sessionRepository = mockSessionRepository,
            scope = backgroundScope
        )
        coEvery { mockSessionRepository.startSession("book1") } returns 101L

        tracker.start()
        testScheduler.runCurrent()

        isUserModeActiveFlow.value = true
        testScheduler.runCurrent()

        // Advance by 15 seconds (should trigger update at 10s)
        advanceTimeBy(15000)

        coVerify(atLeast = 1) { mockSessionRepository.updateActiveSession(101L, any()) }
    }

    @Test
    fun `tracker stops the session and updates end time when isUserModeActive becomes false`() = runTest {
        val tracker = UserModeSessionTracker(
            appStateRepository = mockAppStateRepository,
            sessionRepository = mockSessionRepository,
            scope = backgroundScope
        )
        coEvery { mockSessionRepository.startSession("book1") } returns 101L

        tracker.start()
        testScheduler.runCurrent()

        isUserModeActiveFlow.value = true
        testScheduler.runCurrent()

        // Transition user mode to false
        isUserModeActiveFlow.value = false
        testScheduler.runCurrent()

        coVerify(exactly = 1) { mockSessionRepository.updateActiveSession(101L, any()) }
    }
}

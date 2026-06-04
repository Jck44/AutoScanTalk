package com.andreas_kratzer.ghosttalk.core.data.impl

import com.andreas_kratzer.ghosttalk.core.actions.CallActionProxy
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
    private val mockCallActionProxy = mockk<CallActionProxy>(relaxed = true)
    
    private val isUserModeActiveFlow = MutableStateFlow(false)
    private val activeBookIdFlow = MutableStateFlow<String?>("book1")
    private val isInCallFlow = MutableStateFlow(false)

    private val lazyCallActionProxy = object : dagger.Lazy<CallActionProxy> {
        override fun get(): CallActionProxy = mockCallActionProxy
    }

    @Before
    fun setup() {
        every { mockAppStateRepository.isUserModeActive } returns isUserModeActiveFlow
        every { mockAppStateRepository.activeBookId } returns activeBookIdFlow
        every { mockCallActionProxy.isInCall } returns isInCallFlow
    }

    @Test
    fun `tracker starts a session when isUserModeActive becomes true`() = runTest {
        val tracker = UserModeSessionTracker(
            appStateRepository = mockAppStateRepository,
            sessionRepository = mockSessionRepository,
            callActionProxy = lazyCallActionProxy,
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
            callActionProxy = lazyCallActionProxy,
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
            callActionProxy = lazyCallActionProxy,
            scope = backgroundScope
        )
        coEvery { mockSessionRepository.startSession("book1") } returns 101L

        tracker.start()
        testScheduler.runCurrent()

        isUserModeActiveFlow.value = true
        testScheduler.runCurrent()

        // Transition user mode to false (no call active)
        isUserModeActiveFlow.value = false
        testScheduler.runCurrent()

        coVerify(exactly = 1) { mockSessionRepository.updateActiveSession(101L, any()) }
    }

    @Test
    fun `tracker pauses session when isUserModeActive becomes false during call`() = runTest {
        val tracker = UserModeSessionTracker(
            appStateRepository = mockAppStateRepository,
            sessionRepository = mockSessionRepository,
            callActionProxy = lazyCallActionProxy,
            scope = backgroundScope
        )
        coEvery { mockSessionRepository.startSession("book1") } returns 101L

        tracker.start()
        testScheduler.runCurrent()

        isUserModeActiveFlow.value = true
        testScheduler.runCurrent()

        // Call becomes active
        isInCallFlow.value = true
        // Then user mode deactivates
        isUserModeActiveFlow.value = false
        testScheduler.runCurrent()

        // Active session should be updated (paused)
        coVerify(exactly = 1) { mockSessionRepository.updateActiveSession(101L, any()) }
    }

    @Test
    fun `tracker resumes session when isUserModeActive becomes true after call interruption`() = runTest {
        val tracker = UserModeSessionTracker(
            appStateRepository = mockAppStateRepository,
            sessionRepository = mockSessionRepository,
            callActionProxy = lazyCallActionProxy,
            scope = backgroundScope
        )
        coEvery { mockSessionRepository.startSession("book1") } returns 101L

        tracker.start()
        testScheduler.runCurrent()

        // 1. Start session
        isUserModeActiveFlow.value = true
        testScheduler.runCurrent()

        // 2. Interrupted by call
        isInCallFlow.value = true
        isUserModeActiveFlow.value = false
        testScheduler.runCurrent()

        // 3. Call ends, user mode resumes on the same book
        isInCallFlow.value = false
        isUserModeActiveFlow.value = true
        testScheduler.runCurrent()

        // Should not have called startSession again, but should have updated/resumed existing session 101L
        coVerify(exactly = 1) { mockSessionRepository.startSession("book1") }
        coVerify(atLeast = 2) { mockSessionRepository.updateActiveSession(101L, any()) }
    }

    @Test
    fun `tracker starts new session if user mode resumes on different book after call`() = runTest {
        val tracker = UserModeSessionTracker(
            appStateRepository = mockAppStateRepository,
            sessionRepository = mockSessionRepository,
            callActionProxy = lazyCallActionProxy,
            scope = backgroundScope
        )
        coEvery { mockSessionRepository.startSession("book1") } returns 101L
        coEvery { mockSessionRepository.startSession("book2") } returns 102L

        tracker.start()
        testScheduler.runCurrent()

        // 1. Start session on book1
        isUserModeActiveFlow.value = true
        testScheduler.runCurrent()

        // 2. Interrupted by call
        isInCallFlow.value = true
        isUserModeActiveFlow.value = false
        testScheduler.runCurrent()

        // 3. User switches book to book2, user mode resumes
        activeBookIdFlow.value = "book2"
        isInCallFlow.value = false
        isUserModeActiveFlow.value = true
        testScheduler.runCurrent()

        // Should start a new session for book2 instead of resuming 101L
        coVerify(exactly = 1) { mockSessionRepository.startSession("book1") }
        coVerify(exactly = 1) { mockSessionRepository.startSession("book2") }
    }
}

package com.andreas_kratzer.ghosttalk.core.data.impl

import android.util.Log
import com.andreas_kratzer.ghosttalk.core.actions.CallActionProxy
import com.andreas_kratzer.ghosttalk.core.data.AppStateRepository
import com.andreas_kratzer.ghosttalk.core.data.UserModeSessionRepository
import com.andreas_kratzer.ghosttalk.core.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserModeSessionTracker @Inject constructor(
    private val appStateRepository: AppStateRepository,
    private val sessionRepository: UserModeSessionRepository,
    private val callActionProxy: dagger.Lazy<CallActionProxy>,
    @param:ApplicationScope private val scope: CoroutineScope
) {
    private var trackingJob: Job? = null
    private var updateLoopJob: Job? = null
    private var activeSessionId: Long? = null
    
    private var interruptedSessionId: Long? = null
    private var interruptedBookId: String? = null

    val currentSessionId: Long?
        get() = activeSessionId

    fun start() {
        if (trackingJob != null) return // Already tracking

        trackingJob = scope.launch {
            appStateRepository.isUserModeActive.collectLatest { isActive ->
                if (isActive) {
                    val bookId = appStateRepository.activeBookId.value
                    if (bookId != null) {
                        if (interruptedSessionId != null && interruptedBookId == bookId) {
                            resumeSession(interruptedSessionId!!, bookId)
                        } else {
                            startNewSession(bookId)
                        }
                    } else {
                        Log.e("UserModeSessionTracker", "Cannot start user mode session: activeBookId is null")
                    }
                } else {
                    val isCallActive = callActionProxy.get().isInCall.value
                    val currentBookId = appStateRepository.activeBookId.value
                    if (isCallActive && activeSessionId != null && currentBookId != null) {
                        interruptedSessionId = activeSessionId
                        interruptedBookId = currentBookId
                        pauseActiveSession()
                    } else {
                        stopActiveSession()
                    }
                }
            }
        }
    }

    private suspend fun startNewSession(bookId: String) {
        stopActiveSession()

        try {
            val id = sessionRepository.startSession(bookId)
            activeSessionId = id

            updateLoopJob = scope.launch {
                val currentId = id
                while (activeSessionId == currentId) {
                    delay(10000) // Update database row every 10 seconds
                    if (activeSessionId == currentId) {
                        sessionRepository.updateActiveSession(currentId, System.currentTimeMillis())
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("UserModeSessionTracker", "Error starting user mode session", e)
        }
    }

    private suspend fun pauseActiveSession() {
        updateLoopJob?.cancel()
        updateLoopJob = null

        activeSessionId?.let { sessionId ->
            try {
                sessionRepository.updateActiveSession(sessionId, System.currentTimeMillis())
            } catch (e: Exception) {
                Log.e("UserModeSessionTracker", "Error pausing user mode session", e)
            }
            activeSessionId = null
        }
    }

    private suspend fun resumeSession(sessionId: Long, bookId: String) {
        // Stop any current active session if it exists, though it shouldn't
        stopActiveSession()

        try {
            activeSessionId = sessionId
            interruptedSessionId = null
            interruptedBookId = null

            // Update database row immediately to reflect we resumed it
            sessionRepository.updateActiveSession(sessionId, System.currentTimeMillis())

            updateLoopJob = scope.launch {
                val currentId = sessionId
                while (activeSessionId == currentId) {
                    delay(10000) // Update database row every 10 seconds
                    if (activeSessionId == currentId) {
                        sessionRepository.updateActiveSession(currentId, System.currentTimeMillis())
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("UserModeSessionTracker", "Error resuming user mode session", e)
            // Fallback: start a new session if resuming fails
            startNewSession(bookId)
        }
    }

    private suspend fun stopActiveSession() {
        updateLoopJob?.cancel()
        updateLoopJob = null

        activeSessionId?.let { sessionId ->
            try {
                sessionRepository.updateActiveSession(sessionId, System.currentTimeMillis())
            } catch (e: Exception) {
                Log.e("UserModeSessionTracker", "Error stopping user mode session", e)
            }
            activeSessionId = null
        }
        interruptedSessionId = null
        interruptedBookId = null
    }
}


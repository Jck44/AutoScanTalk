package com.andreas_kratzer.ghosttalk.core.data.impl

import android.util.Log
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
    @param:ApplicationScope private val scope: CoroutineScope
) {
    private var trackingJob: Job? = null
    private var updateLoopJob: Job? = null
    private var activeSessionId: Long? = null

    fun start() {
        if (trackingJob != null) return // Already tracking

        trackingJob = scope.launch {
            appStateRepository.isUserModeActive.collectLatest { isActive ->
                if (isActive) {
                    val bookId = appStateRepository.activeBookId.value
                    if (bookId != null) {
                        startNewSession(bookId)
                    } else {
                        Log.e("UserModeSessionTracker", "Cannot start user mode session: activeBookId is null")
                    }
                } else {
                    stopActiveSession()
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
                while (true) {
                    delay(10000) // Update database row every 10 seconds
                    activeSessionId?.let { sessionId ->
                        sessionRepository.updateActiveSession(sessionId, System.currentTimeMillis())
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("UserModeSessionTracker", "Error starting user mode session", e)
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
    }
}

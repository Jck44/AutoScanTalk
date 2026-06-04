package com.andreas_kratzer.ghosttalk.core.actions

import kotlinx.coroutines.flow.StateFlow

/**
 * Interface to control the scanning state manually.
 */
interface ScannerController {
    /**
     * Whether the scanning is currently paused manually by a user action.
     */
    val isPausedManually: StateFlow<Boolean>

    /**
     * Toggles the manual pause state.
     */
    fun togglePause()

    /**
     * Whether the scanning is currently paused for reading a notification.
     */
    val isPausedForNotification: StateFlow<Boolean>

    /**
     * Pauses the scanning temporarily for a notification read.
     */
    fun pauseForNotification()

    /**
     * Resumes the scanning after a notification read.
     */
    fun resumeFromNotification()
}

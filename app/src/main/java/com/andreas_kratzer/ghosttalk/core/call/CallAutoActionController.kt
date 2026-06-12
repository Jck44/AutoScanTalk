package com.andreas_kratzer.ghosttalk.core.call

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.andreas_kratzer.ghosttalk.core.data.AppStateRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallAutoActionController @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val appStateRepository: AppStateRepository
) {
    companion object {
        private const val TAG = "CallAutoActionController"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var scanCyclesCompleted = 0
    private var inactiveTimerRunnable: Runnable? = null

    fun setupRingingTimers(
        isRinging: () -> Boolean,
        onAnswer: () -> Unit,
        onHangUp: () -> Unit
    ) {
        stopAutoActions()
        val isUserMode = appStateRepository.isUserModeActive.value
        if (isUserMode) {
            scanCyclesCompleted = 0
        } else {
            val delaySeconds = settingsRepository.incomingCallDelayUserModeInactive
            val autoActionStr = settingsRepository.incomingCallAutoActionUserModeInactive
            val autoAction = CallAutoActionPolicy.determineAutoAction(autoActionStr)
            if (autoAction != CallAutoAction.NONE) {
                inactiveTimerRunnable = Runnable {
                    if (isRinging()) {
                        Log.i(TAG, "Inactive timer reached limit, triggering: $autoAction")
                        when (autoAction) {
                            CallAutoAction.ANSWER -> onAnswer()
                            CallAutoAction.REJECT -> onHangUp()
                            else -> {}
                        }
                    }
                }
                handler.postDelayed(inactiveTimerRunnable!!, delaySeconds * 1000L)
            }
        }
    }

    fun incrementScanCycle(
        isRinging: () -> Boolean,
        onAnswer: () -> Unit,
        onHangUp: () -> Unit
    ) {
        if (!isRinging()) return
        val isUserMode = appStateRepository.isUserModeActive.value
        if (isUserMode) {
            scanCyclesCompleted++
            val limit = settingsRepository.incomingCallScanLimitUserModeActive
            val autoActionStr = settingsRepository.incomingCallAutoActionUserModeActive
            val autoAction = CallAutoActionPolicy.determineAutoAction(autoActionStr)
            if (CallAutoActionPolicy.shouldTriggerActiveScanLimit(scanCyclesCompleted, limit) && autoAction != CallAutoAction.NONE) {
                Log.i(TAG, "Active scan cycles reached limit ($limit), triggering: $autoAction")
                when (autoAction) {
                    CallAutoAction.ANSWER -> onAnswer()
                    CallAutoAction.REJECT -> onHangUp()
                    else -> {}
                }
            }
        }
    }

    fun stopAutoActions() {
        inactiveTimerRunnable?.let {
            handler.removeCallbacks(it)
            inactiveTimerRunnable = null
        }
        scanCyclesCompleted = 0
    }
}

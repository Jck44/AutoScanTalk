package com.andreas_kratzer.ghosttalk.ui.pages.delegates

import android.app.Application
import com.andreas_kratzer.ghosttalk.core.call.CallState
import com.andreas_kratzer.ghosttalk.core.call.SystemCallManager
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.di.ApplicationScope
import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallManagementDelegate @Inject constructor(
    private val application: Application,
    val systemCallManager: SystemCallManager,
    private val settingsRepository: SettingsRepository,
    private val ttsHelper: TextToSpeechHelper,
    @ApplicationScope private val appScope: CoroutineScope
) {
    val callState = systemCallManager.callState
    val callerName = systemCallManager.callerName
    val callerPhone = systemCallManager.callerPhone
    val callDurationSeconds = systemCallManager.callDurationSeconds
    val isOutgoing = systemCallManager.isOutgoing
    val isSimulatedCall = systemCallManager.isSimulatedFlow

    val isHangUpButtonFocused = MutableStateFlow(false)
    val hangUpPressCount = MutableStateFlow(0)
    val focusedCallScreenButton = MutableStateFlow("ANNEHMEN") // "ANNEHMEN" or "ABLEHNEN"
    private var callScanJob: Job? = null
    private var lastCallPressTime = 0L

    // Resets the hang-up press counter once the configured time window between presses
    // elapses, so accidental presses (coughing/laughing) spread over time don't accumulate
    // toward a hang-up. Runs on the injected application scope because a hang-up press can
    // occur during an outgoing call (DIALING), where no call-scanning scope is active.
    private var hangUpResetJob: Job? = null

    fun speakCallScreenButton(button: String, isInitial: Boolean) {
        val textRes = if (button == "ANNEHMEN") {
            com.andreas_kratzer.ghosttalk.R.string.call_answer
        } else {
            com.andreas_kratzer.ghosttalk.R.string.call_reject
        }
        val text = application.getString(textRes)
        val cueDevice = settingsRepository.cuesAudioDeviceAddress
        val queueMode = if (isInitial) {
            android.speech.tts.TextToSpeech.QUEUE_ADD
        } else {
            android.speech.tts.TextToSpeech.QUEUE_FLUSH
        }
        ttsHelper.speakRouted(text, cueDevice, queueMode = queueMode, isForCues = true)
    }

    fun startCallScanning(scope: CoroutineScope) {
        callScanJob?.cancel()
        focusedCallScreenButton.value = "ANNEHMEN"
        speakCallScreenButton("ANNEHMEN", isInitial = true)
        val scanDelay = settingsRepository.scanDelayMillis
        callScanJob = scope.launch {
            while (true) {
                delay(scanDelay)
                if (focusedCallScreenButton.value == "ANNEHMEN") {
                    focusedCallScreenButton.value = "ABLEHNEN"
                } else {
                    focusedCallScreenButton.value = "ANNEHMEN"
                    systemCallManager.incrementScanCycle()
                }
                speakCallScreenButton(focusedCallScreenButton.value, isInitial = false)
            }
        }
    }

    fun stopCallScanning() {
        callScanJob?.cancel()
        callScanJob = null
    }

    fun resetHangUpState() {
        hangUpResetJob?.cancel()
        hangUpResetJob = null
        isHangUpButtonFocused.value = false
        hangUpPressCount.value = 0
    }

    fun handleCallButtonPress(): Boolean {
        val state = systemCallManager.callState.value
        if (state == CallState.RINGING) {
            if (focusedCallScreenButton.value == "ANNEHMEN") {
                systemCallManager.answerCall()
            } else {
                systemCallManager.hangUp()
            }
            return true
        }

        if (state == CallState.ACTIVE || state == CallState.DIALING) {
            val currentTime = System.currentTimeMillis()
            val holdingTime = settingsRepository.holdingTimeMillis
            if (currentTime - lastCallPressTime < holdingTime) {
                // Ignore rapid accidental presses (debounce / Haltezeit)
                return true
            }
            val requiredPresses = settingsRepository.hangUpPressesRequired
            val windowMillis = settingsRepository.hangUpPressWindowSeconds * 1000L

            // If a time window is configured, a press that arrives too long after the
            // previous one restarts the count instead of accumulating toward a hang-up.
            val withinWindow = windowMillis <= 0L ||
                hangUpPressCount.value == 0 ||
                currentTime - lastCallPressTime <= windowMillis
            val nextPressCount = if (withinWindow) hangUpPressCount.value + 1 else 1

            lastCallPressTime = currentTime
            hangUpPressCount.value = nextPressCount

            if (requiredPresses <= 1 || nextPressCount >= requiredPresses) {
                hangUpResetJob?.cancel()
                systemCallManager.hangUp()
            } else {
                isHangUpButtonFocused.value = true
                val cueDevice = settingsRepository.cuesAudioDeviceAddress
                val text = application.getString(com.andreas_kratzer.ghosttalk.R.string.call_hang_up)
                ttsHelper.speakRouted(text, cueDevice, isForCues = true)
                scheduleHangUpReset(windowMillis)
            }
            return true
        }

        return false
    }

    /**
     * Schedules the hang-up press counter (and its visual/audible focus) to reset once
     * [windowMillis] elapses without another press. A non-positive window disables the
     * timer, preserving the legacy behavior where presses never time out.
     */
    private fun scheduleHangUpReset(windowMillis: Long) {
        hangUpResetJob?.cancel()
        if (windowMillis <= 0L) return
        hangUpResetJob = appScope.launch {
            delay(windowMillis)
            isHangUpButtonFocused.value = false
            hangUpPressCount.value = 0
        }
    }
}

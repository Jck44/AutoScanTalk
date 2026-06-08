package com.andreas_kratzer.ghosttalk.ui.pages.delegates

import android.app.Application
import com.andreas_kratzer.ghosttalk.core.actions.ActionExecutor
import com.andreas_kratzer.ghosttalk.core.call.SystemCallManager
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.scanning.ScanCoordinator
import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class CallManagementDelegate @Inject constructor(
    private val application: Application,
    private val systemCallManager: SystemCallManager,
    private val settingsRepository: SettingsRepository,
    private val ttsHelper: TextToSpeechHelper,
    private val actionExecutor: ActionExecutor,
    private val scanCoordinator: ScanCoordinator
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
}

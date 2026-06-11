package com.andreas_kratzer.ghosttalk.ui.pages

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andreas_kratzer.ghosttalk.core.call.CallState
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.CallManagementDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class CallViewModel @Inject constructor(
    application: Application,
    private val callManagementDelegate: CallManagementDelegate
) : AndroidViewModel(application) {

    val callState: StateFlow<CallState> = callManagementDelegate.callState
    val callerName: StateFlow<String?> = callManagementDelegate.callerName
    val callerPhone: StateFlow<String?> = callManagementDelegate.callerPhone
    val callDurationSeconds: StateFlow<Int> = callManagementDelegate.callDurationSeconds
    val isOutgoing: StateFlow<Boolean> = callManagementDelegate.isOutgoing
    val isSimulatedCall: StateFlow<Boolean> = callManagementDelegate.isSimulatedCall

    val isHangUpButtonFocused: StateFlow<Boolean> = callManagementDelegate.isHangUpButtonFocused
    val hangUpPressCount: StateFlow<Int> = callManagementDelegate.hangUpPressCount
    val focusedCallScreenButton: StateFlow<String> = callManagementDelegate.focusedCallScreenButton

    fun startCallScanning() = callManagementDelegate.startCallScanning(viewModelScope)
    fun stopCallScanning() = callManagementDelegate.stopCallScanning()
    fun resetHangUpState() = callManagementDelegate.resetHangUpState()
    fun answerCall() = callManagementDelegate.systemCallManager.answerCall()
    fun hangUp() = callManagementDelegate.systemCallManager.hangUp()
    fun handleCallButtonPress(): Boolean = callManagementDelegate.handleCallButtonPress()
}

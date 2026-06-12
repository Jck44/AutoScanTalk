package com.andreas_kratzer.ghosttalk.ui.pages.sections

import androidx.compose.runtime.Composable
import com.andreas_kratzer.ghosttalk.core.call.CallState
import com.andreas_kratzer.ghosttalk.ui.pages.CallViewModel

@Composable
fun CallOverlayHost(
    callState: CallState,
    callerName: String?,
    callerPhone: String?,
    focusedCallScreenButton: String,
    callDurationSeconds: Int,
    isOutgoing: Boolean,
    isHangUpButtonFocused: Boolean,
    isSimulatedCall: Boolean,
    callViewModel: CallViewModel
) {
    when (callState) {
        CallState.RINGING -> {
            IncomingCallOverlay(
                callerName = callerName,
                callerPhone = callerPhone,
                focusedButton = focusedCallScreenButton,
                onAnswer = { callViewModel.answerCall() },
                onReject = { callViewModel.hangUp() },
                isSimulated = isSimulatedCall
            )
        }
        CallState.DIALING, 
        CallState.ACTIVE -> {
            ActiveCallOverlay(
                callerName = callerName,
                callerPhone = callerPhone,
                durationSeconds = callDurationSeconds,
                isDialing = callState == CallState.DIALING,
                isOutgoing = isOutgoing,
                isHangUpFocused = isHangUpButtonFocused,
                onHangUp = { callViewModel.hangUp() },
                isSimulated = isSimulatedCall
            )
        }
        else -> {}
    }
}

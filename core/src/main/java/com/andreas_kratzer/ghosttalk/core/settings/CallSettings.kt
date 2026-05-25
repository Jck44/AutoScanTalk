package com.andreas_kratzer.ghosttalk.core.settings

import kotlinx.coroutines.flow.StateFlow

interface CallSettings {
    var maxCallDurationSeconds: Int
    val maxCallDurationSecondsFlow: StateFlow<Int>

    var callDurationFeedbackIntervalSeconds: Int
    val callDurationFeedbackIntervalSecondsFlow: StateFlow<Int>

    var outgoingCallIntro: String
    val outgoingCallIntroFlow: StateFlow<String>

    var incomingCallIntro: String
    val incomingCallIntroFlow: StateFlow<String>

    // Settings when User Mode is active (Switch Scanning based)
    var incomingCallScanLimitUserModeActive: Int
    val incomingCallScanLimitUserModeActiveFlow: StateFlow<Int>

    var incomingCallAutoActionUserModeActive: String // "NONE", "ANSWER", "REJECT"
    val incomingCallAutoActionUserModeActiveFlow: StateFlow<String>

    // Settings when User Mode is NOT active (Timer based)
    var incomingCallDelayUserModeInactive: Int // seconds
    val incomingCallDelayUserModeInactiveFlow: StateFlow<Int>

    var incomingCallAutoActionUserModeInactive: String // "NONE", "ANSWER", "REJECT"
    val incomingCallAutoActionUserModeInactiveFlow: StateFlow<String>

    var callAnnouncementAsCue: Boolean
    val callAnnouncementAsCueFlow: StateFlow<Boolean>

    var autoEnableSpeakerphone: Boolean
    val autoEnableSpeakerphoneFlow: StateFlow<Boolean>

    var simulateCallsEnabled: Boolean
    val simulateCallsEnabledFlow: StateFlow<Boolean>
}

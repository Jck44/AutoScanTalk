package com.andreas_kratzer.ghosttalk.core.settings

import kotlinx.coroutines.flow.StateFlow

interface UserSettings {
    val keepScreenOnUserModeFlow: StateFlow<Boolean>
    val userModeScreenBehaviorFlow: StateFlow<String>

    var keepScreenOnUserMode: Boolean
    var userModeScreenBehavior: String
}

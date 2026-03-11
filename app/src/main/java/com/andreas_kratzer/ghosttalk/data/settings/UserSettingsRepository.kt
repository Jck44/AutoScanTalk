package com.andreas_kratzer.ghosttalk.data.settings

import android.content.SharedPreferences
import com.andreas_kratzer.ghosttalk.data.settings.SettingsConstants.KEY_KEEP_SCREEN_ON_USER_MODE
import com.andreas_kratzer.ghosttalk.data.settings.SettingsConstants.KEY_USER_MODE_SCREEN_BEHAVIOR
import kotlinx.coroutines.flow.StateFlow

class UserSettingsRepository(
    prefs: SharedPreferences,
    activeBookIdFlow: StateFlow<String>
) : BaseSettingsRepository(prefs, activeBookIdFlow) {

    private val _keepScreenOnUserMode = BooleanSetting(KEY_KEEP_SCREEN_ON_USER_MODE, true)
    private val _userModeScreenBehavior = NonNullStringSetting(KEY_USER_MODE_SCREEN_BEHAVIOR, "NORMAL")

    val keepScreenOnUserModeFlow = _keepScreenOnUserMode.flow
    val userModeScreenBehaviorFlow = _userModeScreenBehavior.flow

    var keepScreenOnUserMode: Boolean
        get() = _keepScreenOnUserMode.value
        set(value) { _keepScreenOnUserMode.value = value }

    var userModeScreenBehavior: String
        get() = _userModeScreenBehavior.value
        set(value) { _userModeScreenBehavior.value = value }

    override fun refresh() {
        _keepScreenOnUserMode.refresh()
        _userModeScreenBehavior.refresh()
    }
}

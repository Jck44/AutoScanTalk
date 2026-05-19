package com.andreas_kratzer.ghosttalk.core.data.impl.settings

import android.content.SharedPreferences
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_KEEP_SCREEN_ON_USER_MODE
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_USER_MODE_SCREEN_BEHAVIOR
import kotlinx.coroutines.flow.StateFlow

class UserSettingsRepository(
    prefs: SharedPreferences,
    activeBookIdFlow: StateFlow<String?>
) : BaseSettingsRepository(prefs, activeBookIdFlow) {

    private val _keepScreenOnUserMode = BooleanSetting(KEY_KEEP_SCREEN_ON_USER_MODE, true)
    private val _userModeScreenBehavior = NonNullStringSetting(KEY_USER_MODE_SCREEN_BEHAVIOR, "NORMAL")

    val keepScreenOnUserModeFlow = _keepScreenOnUserMode.flow
    val userModeScreenBehaviorFlow = _userModeScreenBehavior.flow

    var keepScreenOnUserMode: Boolean by _keepScreenOnUserMode
    var userModeScreenBehavior: String by _userModeScreenBehavior


    override fun refresh() {
        _keepScreenOnUserMode.refresh()
        _userModeScreenBehavior.refresh()
    }
}

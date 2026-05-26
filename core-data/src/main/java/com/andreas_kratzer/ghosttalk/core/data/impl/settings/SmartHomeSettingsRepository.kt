package com.andreas_kratzer.ghosttalk.core.data.impl.settings

import android.content.SharedPreferences
import com.andreas_kratzer.ghosttalk.core.settings.SmartHomeSettings
import kotlinx.coroutines.flow.StateFlow

class SmartHomeSettingsRepository(
    prefs: SharedPreferences,
    activeBookIdFlow: StateFlow<String?>
) : BaseSettingsRepository(prefs, activeBookIdFlow), SmartHomeSettings {

    private val _hueBridgeIp = NonNullStringSetting(SettingsConstants.KEY_HUE_BRIDGE_IP, "")
    private val _hueUsername = NonNullStringSetting(SettingsConstants.KEY_HUE_USERNAME, "")
    private val _hueBridgeFingerprint = NonNullStringSetting(SettingsConstants.KEY_HUE_BRIDGE_FINGERPRINT, "")

    override val hueBridgeIpFlow = _hueBridgeIp.flow
    override val hueUsernameFlow = _hueUsername.flow
    override val hueBridgeFingerprintFlow = _hueBridgeFingerprint.flow

    override var hueBridgeIp: String by _hueBridgeIp
    override var hueUsername: String by _hueUsername
    override var hueBridgeFingerprint: String by _hueBridgeFingerprint

    override fun refresh() {
        _hueBridgeIp.refresh()
        _hueUsername.refresh()
        _hueBridgeFingerprint.refresh()
    }
}


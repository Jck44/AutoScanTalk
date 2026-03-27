package com.andreas_kratzer.ghosttalk.core.data.impl.settings

import android.content.SharedPreferences
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_HUE_ACCESS_TOKEN
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_HUE_REFRESH_TOKEN
import com.andreas_kratzer.ghosttalk.core.settings.SmartHomeSettings
import kotlinx.coroutines.flow.StateFlow

class SmartHomeSettingsRepository(
    prefs: SharedPreferences,
    activeBookIdFlow: StateFlow<String?>
) : BaseSettingsRepository(prefs, activeBookIdFlow), SmartHomeSettings {

    private val _googleHomeProjectId = NonNullStringSetting(SettingsConstants.KEY_GOOGLE_HOME_PROJECT_ID, "")
    private val _hueBridgeIp = NonNullStringSetting(SettingsConstants.KEY_HUE_BRIDGE_IP, "")
    private val _hueUsername = NonNullStringSetting(SettingsConstants.KEY_HUE_USERNAME, "")
    private val _hueAccessToken = NonNullStringSetting(KEY_HUE_ACCESS_TOKEN, "")
    private val _hueRefreshToken = NonNullStringSetting(KEY_HUE_REFRESH_TOKEN, "")
    private val _hueClientId = NonNullStringSetting(SettingsConstants.KEY_HUE_CLIENT_ID, "")
    private val _hueClientSecret = NonNullStringSetting(SettingsConstants.KEY_HUE_CLIENT_SECRET, "")

    override val googleHomeProjectIdFlow = _googleHomeProjectId.flow
    override val hueBridgeIpFlow = _hueBridgeIp.flow
    override val hueUsernameFlow = _hueUsername.flow
    override val hueAccessTokenFlow = _hueAccessToken.flow
    override val hueRefreshTokenFlow = _hueRefreshToken.flow
    override val hueClientIdFlow = _hueClientId.flow
    override val hueClientSecretFlow = _hueClientSecret.flow

    override var googleHomeProjectId: String
        get() = _googleHomeProjectId.value
        set(value) { _googleHomeProjectId.value = value }

    override var hueBridgeIp: String
        get() = _hueBridgeIp.value
        set(value) { _hueBridgeIp.value = value }

    override var hueUsername: String
        get() = _hueUsername.value
        set(value) { _hueUsername.value = value }

    override var hueAccessToken: String
        get() = _hueAccessToken.value
        set(value) { _hueAccessToken.value = value }

    override var hueRefreshToken: String
        get() = _hueRefreshToken.value
        set(value) { _hueRefreshToken.value = value }

    override var hueClientId: String
        get() = _hueClientId.value
        set(value) { _hueClientId.value = value }

    override var hueClientSecret: String
        get() = _hueClientSecret.value
        set(value) { _hueClientSecret.value = value }



    override fun refresh() {
        _googleHomeProjectId.refresh()
        _hueBridgeIp.refresh()
        _hueUsername.refresh()
        _hueAccessToken.refresh()
        _hueRefreshToken.refresh()
        _hueClientId.refresh()
        _hueClientSecret.refresh()
    }
}

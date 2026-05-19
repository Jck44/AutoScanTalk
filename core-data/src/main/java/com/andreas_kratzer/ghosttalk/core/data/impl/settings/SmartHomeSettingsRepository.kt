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

    override var googleHomeProjectId: String by _googleHomeProjectId
    override var hueBridgeIp: String by _hueBridgeIp
    override var hueUsername: String by _hueUsername
    override var hueAccessToken: String by _hueAccessToken
    override var hueRefreshToken: String by _hueRefreshToken
    override var hueClientId: String by _hueClientId
    override var hueClientSecret: String by _hueClientSecret



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

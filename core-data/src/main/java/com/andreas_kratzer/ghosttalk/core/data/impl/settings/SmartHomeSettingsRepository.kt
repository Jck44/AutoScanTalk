package com.andreas_kratzer.ghosttalk.core.data.impl.settings

import android.content.Context
import android.content.SharedPreferences
import com.andreas_kratzer.ghosttalk.core.settings.SmartHomeSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartHomeSettingsRepository @Inject constructor(
    prefs: SharedPreferences,
    activeBookIdManager: ActiveBookIdManager,
    @ApplicationContext private val context: Context
) : BaseSettingsRepository(prefs, activeBookIdManager.activeBookIdFlow), SmartHomeSettings {

    private val _hueBridgeIp = NonNullStringSetting(SettingsConstants.KEY_HUE_BRIDGE_IP, "")
    private val _hueUsername = NonNullStringSetting(
        key = SettingsConstants.KEY_HUE_USERNAME,
        default = "",
        encrypt = { SecuritySettingsEncryptor.encryptLocal(it, context) },
        decrypt = { SecuritySettingsEncryptor.decryptLocal(it, context) }
    )
    private val _hueBridgeFingerprint = NonNullStringSetting(
        key = SettingsConstants.KEY_HUE_BRIDGE_FINGERPRINT,
        default = "",
        encrypt = { SecuritySettingsEncryptor.encryptLocal(it, context) },
        decrypt = { SecuritySettingsEncryptor.decryptLocal(it, context) }
    )
    private val _hueCachedDevices = NonNullStringSetting(SettingsConstants.KEY_HUE_CACHED_DEVICES, "")

    override val hueBridgeIpFlow = _hueBridgeIp.flow
    override val hueUsernameFlow = _hueUsername.flow
    override val hueBridgeFingerprintFlow = _hueBridgeFingerprint.flow
    override val hueCachedDevicesFlow = _hueCachedDevices.flow

    override var hueBridgeIp: String by _hueBridgeIp
    override var hueUsername: String by _hueUsername
    override var hueBridgeFingerprint: String by _hueBridgeFingerprint
    override var hueCachedDevices: String by _hueCachedDevices

    override fun refresh() {
        _hueBridgeIp.refresh()
        _hueUsername.refresh()
        _hueBridgeFingerprint.refresh()
        _hueCachedDevices.refresh()
    }
}


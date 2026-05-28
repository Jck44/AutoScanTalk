package com.andreas_kratzer.ghosttalk.core.data.impl.settings

import android.content.SharedPreferences
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_BIOMETRIC_ENABLED
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_IS_PIN_REQUIRED_FOR_DELETION
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_SECURITY_PIN
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_SECURITY_PIN_HASH
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_SECURITY_PIN_SALT
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_SECURITY_PIN_TIMEOUT_MINUTES
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_SECURITY_REQUIRED_FOR_EDIT
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_SECURITY_REQUIRED_FOR_SETTINGS
import kotlinx.coroutines.flow.StateFlow

class SecuritySettingsRepository(
    prefs: SharedPreferences,
    activeBookIdFlow: StateFlow<String?>
) : BaseSettingsRepository(prefs, activeBookIdFlow) {

    private val _securityPinHash = StringSetting(KEY_SECURITY_PIN_HASH, "", isScoped = false)
    private val _securityPinSalt = StringSetting(KEY_SECURITY_PIN_SALT, "", isScoped = false)
    private val _securityPin = StringSetting(KEY_SECURITY_PIN, "", isScoped = false)
    private val _securityPinTimeoutMinutes = LongSetting(KEY_SECURITY_PIN_TIMEOUT_MINUTES, 30L, isScoped = false)
    private val _isPinRequiredForDeletion = BooleanSetting(KEY_IS_PIN_REQUIRED_FOR_DELETION, false, isScoped = false)
    private val _isBiometricEnabled = BooleanSetting(KEY_BIOMETRIC_ENABLED, false, isScoped = false)
    private val _isSecurityRequiredForEdit = BooleanSetting(KEY_SECURITY_REQUIRED_FOR_EDIT, false, isScoped = false)
    private val _isSecurityRequiredForSettings = BooleanSetting(KEY_SECURITY_REQUIRED_FOR_SETTINGS, false, isScoped = false)

    val securityPinHashFlow = _securityPinHash.flow
    val securityPinSaltFlow = _securityPinSalt.flow
    val securityPinFlow = _securityPin.flow
    val securityPinTimeoutMinutesFlow = _securityPinTimeoutMinutes.flow
    val isPinRequiredForDeletionFlow = _isPinRequiredForDeletion.flow
    val isBiometricEnabledFlow = _isBiometricEnabled.flow
    val isSecurityRequiredForEditFlow = _isSecurityRequiredForEdit.flow
    val isSecurityRequiredForSettingsFlow = _isSecurityRequiredForSettings.flow

    var securityPinHash: String? by _securityPinHash
    var securityPinSalt: String? by _securityPinSalt
    var securityPin: String? by _securityPin
    var securityPinTimeoutMinutes: Long by _securityPinTimeoutMinutes
    var isPinRequiredForDeletion: Boolean by _isPinRequiredForDeletion
    var isBiometricEnabled: Boolean by _isBiometricEnabled
    var isSecurityRequiredForEdit: Boolean by _isSecurityRequiredForEdit
    var isSecurityRequiredForSettings: Boolean by _isSecurityRequiredForSettings


    override fun refresh() {
        _securityPinHash.refresh()
        _securityPinSalt.refresh()
        _securityPin.refresh()
        _securityPinTimeoutMinutes.refresh()
        _isPinRequiredForDeletion.refresh()
        _isBiometricEnabled.refresh()
        _isSecurityRequiredForEdit.refresh()
        _isSecurityRequiredForSettings.refresh()
    }
}

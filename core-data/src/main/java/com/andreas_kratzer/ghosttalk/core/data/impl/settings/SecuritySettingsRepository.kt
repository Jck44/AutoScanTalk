package com.andreas_kratzer.ghosttalk.core.data.impl.settings

import android.content.SharedPreferences
import com.andreas_kratzer.ghosttalk.core.SecuritySettings
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_BIOMETRIC_ENABLED
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_IS_PIN_REQUIRED_FOR_DELETION
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_SECURITY_PIN
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_SECURITY_PIN_HASH
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_SECURITY_PIN_SALT
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_SECURITY_PIN_TIMEOUT_MINUTES
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_SECURITY_REQUIRED_FOR_ANALYTICS
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_SECURITY_REQUIRED_FOR_EDIT
import com.andreas_kratzer.ghosttalk.core.data.impl.settings.SettingsConstants.KEY_SECURITY_REQUIRED_FOR_SETTINGS
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecuritySettingsRepository @Inject constructor(
    prefs: SharedPreferences,
    activeBookIdManager: ActiveBookIdManager
) : BaseSettingsRepository(prefs, activeBookIdManager.activeBookIdFlow), SecuritySettings {

    override fun getSecurityPinForBook(bookId: String): String? {
        val scopedKey = "${bookId}_${SettingsConstants.KEY_SECURITY_PIN}"
        if (prefs.contains(scopedKey)) {
            return prefs.getString(scopedKey, "")
        }
        return prefs.getString(SettingsConstants.KEY_SECURITY_PIN, "")
    }

    override fun isPinRequiredForDeletionForBook(bookId: String): Boolean {
        return isPinRequiredForDeletion
    }

    private val _securityPinHash = StringSetting(KEY_SECURITY_PIN_HASH, "", isScoped = false)
    private val _securityPinSalt = StringSetting(KEY_SECURITY_PIN_SALT, "", isScoped = false)
    private val _securityPin = StringSetting(KEY_SECURITY_PIN, "", isScoped = false)
    private val _securityPinTimeoutMinutes = LongSetting(KEY_SECURITY_PIN_TIMEOUT_MINUTES, 30L, isScoped = false)
    private val _isPinRequiredForDeletion = BooleanSetting(KEY_IS_PIN_REQUIRED_FOR_DELETION, false, isScoped = false)
    private val _isBiometricEnabled = BooleanSetting(KEY_BIOMETRIC_ENABLED, false, isScoped = false)
    private val _isSecurityRequiredForEdit = BooleanSetting(KEY_SECURITY_REQUIRED_FOR_EDIT, false, isScoped = false)
    private val _isSecurityRequiredForSettings = BooleanSetting(KEY_SECURITY_REQUIRED_FOR_SETTINGS, false, isScoped = false)
    private val _isSecurityRequiredForAnalytics = BooleanSetting(KEY_SECURITY_REQUIRED_FOR_ANALYTICS, false, isScoped = false)

    override val securityPinHashFlow = _securityPinHash.flow
    override val securityPinSaltFlow = _securityPinSalt.flow
    override val securityPinFlow = _securityPin.flow
    override val securityPinTimeoutMinutesFlow = _securityPinTimeoutMinutes.flow
    override val isPinRequiredForDeletionFlow = _isPinRequiredForDeletion.flow
    override val isBiometricEnabledFlow = _isBiometricEnabled.flow
    override val isSecurityRequiredForEditFlow = _isSecurityRequiredForEdit.flow
    override val isSecurityRequiredForSettingsFlow = _isSecurityRequiredForSettings.flow
    override val isSecurityRequiredForAnalyticsFlow = _isSecurityRequiredForAnalytics.flow

    override var securityPinHash: String? by _securityPinHash
    override var securityPinSalt: String? by _securityPinSalt
    override var securityPin: String? by _securityPin
    override var securityPinTimeoutMinutes: Long by _securityPinTimeoutMinutes
    override var isPinRequiredForDeletion: Boolean by _isPinRequiredForDeletion
    override var isBiometricEnabled: Boolean by _isBiometricEnabled
    override var isSecurityRequiredForEdit: Boolean by _isSecurityRequiredForEdit
    override var isSecurityRequiredForSettings: Boolean by _isSecurityRequiredForSettings
    override var isSecurityRequiredForAnalytics: Boolean by _isSecurityRequiredForAnalytics


    override fun refresh() {
        _securityPinHash.refresh()
        _securityPinSalt.refresh()
        _securityPin.refresh()
        _securityPinTimeoutMinutes.refresh()
        _isPinRequiredForDeletion.refresh()
        _isBiometricEnabled.refresh()
        _isSecurityRequiredForEdit.refresh()
        _isSecurityRequiredForSettings.refresh()
        _isSecurityRequiredForAnalytics.refresh()
    }
}

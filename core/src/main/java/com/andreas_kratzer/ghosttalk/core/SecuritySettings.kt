package com.andreas_kratzer.ghosttalk.core

interface SecuritySettings {
    var securityPin: String?
    var securityPinHash: String?
    var securityPinSalt: String?
    var securityPinTimeoutMinutes: Long
    var isPinRequiredForDeletion: Boolean
    var isBiometricEnabled: Boolean
    var isSecurityRequiredForEdit: Boolean
    var isSecurityRequiredForSettings: Boolean
    var isSecurityRequiredForAnalytics: Boolean

    val securityPinFlow: kotlinx.coroutines.flow.StateFlow<String?>
    val securityPinHashFlow: kotlinx.coroutines.flow.StateFlow<String?>
    val securityPinSaltFlow: kotlinx.coroutines.flow.StateFlow<String?>
    val securityPinTimeoutMinutesFlow: kotlinx.coroutines.flow.StateFlow<Long>
    val isPinRequiredForDeletionFlow: kotlinx.coroutines.flow.StateFlow<Boolean>
    val isBiometricEnabledFlow: kotlinx.coroutines.flow.StateFlow<Boolean>
    val isSecurityRequiredForEditFlow: kotlinx.coroutines.flow.StateFlow<Boolean>
    val isSecurityRequiredForSettingsFlow: kotlinx.coroutines.flow.StateFlow<Boolean>
    val isSecurityRequiredForAnalyticsFlow: kotlinx.coroutines.flow.StateFlow<Boolean>

    fun getSecurityPinForBook(bookId: String): String?
    fun isPinRequiredForDeletionForBook(bookId: String): Boolean
}

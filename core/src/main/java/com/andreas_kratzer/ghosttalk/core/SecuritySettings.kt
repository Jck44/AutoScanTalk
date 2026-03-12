package com.andreas_kratzer.ghosttalk.core

interface SecuritySettings {
    var securityPin: String?
    var securityPinHash: String?
    var securityPinSalt: String?
    val securityPinTimeoutMinutes: Long
    val isPinRequiredForDeletion: Boolean
    val isSecurityRequiredForEdit: Boolean
    val isSecurityRequiredForSettings: Boolean
}

package com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates

import com.andreas_kratzer.ghosttalk.core.SecurityManager
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.feature.settings.ui.ProfileDraftCoordinator
import kotlinx.coroutines.flow.StateFlow

class SecuritySettingsDelegate(
    private val settingsRepository: SettingsRepository,
    private val draftCoordinator: ProfileDraftCoordinator,
    private val securityManager: SecurityManager
) {
    val securityPin: StateFlow<String?> = draftCoordinator.scopedFlow(settingsRepository.securityPinFlow) { "" }
    val securityPinTimeoutMinutes: StateFlow<Long> = draftCoordinator.scopedFlow(settingsRepository.securityPinTimeoutMinutesFlow) { it.securityPinTimeoutMinutes }
    val isPinRequiredForDeletion: StateFlow<Boolean> = draftCoordinator.scopedFlow(settingsRepository.isPinRequiredForDeletionFlow) { it.isPinRequiredForDeletion }
    val isBiometricEnabled: StateFlow<Boolean> = draftCoordinator.scopedFlow(settingsRepository.isBiometricEnabledFlow) { false }
    val isSecurityRequiredForEdit: StateFlow<Boolean> = draftCoordinator.scopedFlow(settingsRepository.isSecurityRequiredForEditFlow) { it.isSecurityRequiredForEdit }
    val isSecurityRequiredForSettings: StateFlow<Boolean> = draftCoordinator.scopedFlow(settingsRepository.isSecurityRequiredForSettingsFlow) { it.isSecurityRequiredForSettings }
    val isSecurityRequiredForAnalytics: StateFlow<Boolean> = draftCoordinator.scopedFlow(settingsRepository.isSecurityRequiredForAnalyticsFlow) { it.isSecurityRequiredForAnalytics }

    fun setSecurityPin(pin: String) {
        settingsRepository.securityPin = pin
    }

    fun clearSecurityPin() {
        securityManager.clearPin()
    }

    fun setSecurityPinTimeoutMinutes(minutes: Long) = draftCoordinator.update({ 
        settingsRepository.securityPinTimeoutMinutes = minutes
    }) { it.copy(securityPinTimeoutMinutes = minutes) }

    fun setPinRequiredForDeletion(required: Boolean) = draftCoordinator.update({ 
        settingsRepository.isPinRequiredForDeletion = required
    }) { it.copy(isPinRequiredForDeletion = required) }

    fun setBiometricEnabled(enabled: Boolean) {
        settingsRepository.isBiometricEnabled = enabled
    }

    fun setSecurityRequiredForEdit(required: Boolean) = draftCoordinator.update({ 
        settingsRepository.isSecurityRequiredForEdit = required
    }) { it.copy(isSecurityRequiredForEdit = required) }

    fun setSecurityRequiredForSettings(required: Boolean) = draftCoordinator.update({ 
        settingsRepository.isSecurityRequiredForSettings = required
    }) { it.copy(isSecurityRequiredForSettings = required) }

    fun setSecurityRequiredForAnalytics(required: Boolean) = draftCoordinator.update({ 
        settingsRepository.isSecurityRequiredForAnalytics = required
    }) { it.copy(isSecurityRequiredForAnalytics = required) }

    fun lock() {
        securityManager.lock()
    }
}

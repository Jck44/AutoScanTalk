package com.andreas_kratzer.ghosttalk.feature.settings.domain

import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction
import com.andreas_kratzer.ghosttalk.core.model.DeviceActionType
import com.andreas_kratzer.ghosttalk.core.model.GeminiButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiNanoButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiSearchButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SmartPredictionButtonAction
import com.andreas_kratzer.ghosttalk.core.scanning.FeatureGuardProxy
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centrally manages feature-related visibility and availability logic for buttons.
 */
@Singleton
class FeatureGuard @Inject constructor(
    private val settingsRepository: SettingsRepository
) : FeatureGuardProxy {
    /**
     * Checks if a specific action is currently enabled based on global settings.
     */
    override fun isActionEnabled(action: ButtonAction): Boolean {
        return when (action) {
            is SmartPredictionButtonAction -> settingsRepository.isSmartPredictionEnabled
            is GeminiButtonAction -> settingsRepository.isGeminiEnabled
            is GeminiSearchButtonAction -> settingsRepository.isGeminiEnabled
            is GeminiNanoButtonAction -> settingsRepository.useLocalGenerativeAi
            is ControlDeviceButtonAction -> {
                if (action.actionType == DeviceActionType.READ_NOTIFICATIONS) {
                    settingsRepository.isNotificationReadingEnabled
                } else {
                    true
                }
            }
            else -> true
        }
    }

    /**
     * Checks if a button should be visible/active in the UI based on its action and settings.
     */
    override fun isButtonVisible(config: ButtonConfig): Boolean {
        return isActionEnabled(config.buttonAction)
    }
}

package com.andreas_kratzer.ghosttalk.domain

import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.model.ButtonAction
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.GeminiButtonAction
import com.andreas_kratzer.ghosttalk.model.SmartPredictionButtonAction
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centrally manages feature-related visibility and availability logic for buttons.
 * This class follows OOP principles to avoid scattered feature-checks throughout the codebase.
 */
@Singleton
class FeatureGuard @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    /**
     * Checks if a specific action is currently enabled based on global settings.
     */
    fun isActionEnabled(action: ButtonAction): Boolean {
        return when (action) {
            is SmartPredictionButtonAction -> settingsRepository.isSmartPredictionEnabled
            is GeminiButtonAction -> settingsRepository.isGeminiEnabled
            else -> true
        }
    }

    /**
     * Checks if a button should be visible/active in the UI based on its action and settings.
     * This usually means the action it performs must be enabled.
     */
    fun isButtonVisible(config: ButtonConfig): Boolean {
        return isActionEnabled(config.buttonAction)
    }
}

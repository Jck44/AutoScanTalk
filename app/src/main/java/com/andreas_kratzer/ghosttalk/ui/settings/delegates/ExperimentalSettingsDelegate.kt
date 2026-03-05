package com.andreas_kratzer.ghosttalk.ui.settings.delegates

import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExperimentalSettingsDelegate @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    fun setExperimentalManualSorting(enabled: Boolean) {
        settingsRepository.experimentalManualSorting = enabled
    }

    fun setSmartPredictionEnabled(enabled: Boolean) {
        settingsRepository.isSmartPredictionEnabled = enabled
    }
}

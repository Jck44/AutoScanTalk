package com.andreas_kratzer.ghosttalk.domain.settings

import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import javax.inject.Inject

enum class ExperimentalFeature {
    MANUAL_SORTING,
    SMART_PREDICTION
}

class ToggleExperimentalFeatureUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    operator fun invoke(feature: ExperimentalFeature, enabled: Boolean) {
        when (feature) {
            ExperimentalFeature.MANUAL_SORTING -> settingsRepository.experimentalManualSorting = enabled
            ExperimentalFeature.SMART_PREDICTION -> settingsRepository.isSmartPredictionEnabled = enabled
        }
    }
}
package com.andreas_kratzer.ghosttalk.feature.settings.domain

import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import javax.inject.Inject

enum class ExperimentalFeature {
    SMART_PREDICTION
}

class ToggleExperimentalFeatureUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    operator fun invoke(feature: ExperimentalFeature, enabled: Boolean) {
        when (feature) {
            ExperimentalFeature.SMART_PREDICTION -> settingsRepository.isSmartPredictionEnabled = enabled
        }
    }
}

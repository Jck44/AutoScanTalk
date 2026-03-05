package com.andreas_kratzer.ghosttalk.ui.settings.delegates

import com.andreas_kratzer.ghosttalk.domain.ExperimentalFeature
import com.andreas_kratzer.ghosttalk.domain.ToggleExperimentalFeatureUseCase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExperimentalSettingsDelegate @Inject constructor(
    private val toggleExperimentalFeatureUseCase: ToggleExperimentalFeatureUseCase
) {
    fun setExperimentalManualSorting(enabled: Boolean) {
        toggleExperimentalFeatureUseCase(ExperimentalFeature.MANUAL_SORTING, enabled)
    }

    fun setSmartPredictionEnabled(enabled: Boolean) {
        toggleExperimentalFeatureUseCase(ExperimentalFeature.SMART_PREDICTION, enabled)
    }
}

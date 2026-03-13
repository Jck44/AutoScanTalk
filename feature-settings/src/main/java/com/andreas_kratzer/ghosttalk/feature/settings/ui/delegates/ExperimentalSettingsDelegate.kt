package com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates

import com.andreas_kratzer.ghosttalk.feature.settings.domain.ExperimentalFeature
import com.andreas_kratzer.ghosttalk.feature.settings.domain.ToggleExperimentalFeatureUseCase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExperimentalSettingsDelegate @Inject constructor(
    private val toggleExperimentalFeatureUseCase: ToggleExperimentalFeatureUseCase
) {

    fun setSmartPredictionEnabled(enabled: Boolean) {
        toggleExperimentalFeatureUseCase(ExperimentalFeature.SMART_PREDICTION, enabled)
    }
}

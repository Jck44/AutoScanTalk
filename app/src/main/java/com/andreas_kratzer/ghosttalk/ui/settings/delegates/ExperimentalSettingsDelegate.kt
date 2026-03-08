package com.andreas_kratzer.ghosttalk.ui.settings.delegates

import com.andreas_kratzer.ghosttalk.domain.settings.ExperimentalFeature
import com.andreas_kratzer.ghosttalk.domain.settings.ToggleExperimentalFeatureUseCase
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

package com.andreas_kratzer.ghosttalk.domain.settings

import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.SmartPredictionButtonAction
import javax.inject.Inject

class CheckForPredictorUseCase @Inject constructor(
    private val featureGuard: FeatureGuard
) {
    operator fun invoke(page: Page?): Boolean {
        if (page == null) return false
        return page.buttonConfigs.any { 
            it != null && it.isActive && featureGuard.isActionEnabled(it.buttonAction) && it.buttonAction is SmartPredictionButtonAction
        }
    }
}

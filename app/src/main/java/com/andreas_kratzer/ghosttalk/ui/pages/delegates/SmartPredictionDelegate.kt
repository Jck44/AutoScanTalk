package com.andreas_kratzer.ghosttalk.ui.pages.delegates

import android.util.Log
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.domain.FeatureGuard
import com.andreas_kratzer.ghosttalk.domain.PredictNextActionUseCase
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.SmartPredictionButtonAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

class SmartPredictionDelegate @Inject constructor(
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val featureGuard: FeatureGuard,
    private val predictNextActionUseCase: PredictNextActionUseCase
) {
    fun init(
        currentPage: StateFlow<Page?>,
        lastActions: StateFlow<List<String>>,
        activeBookId: StateFlow<String?>,
        onPredictionsUpdated: (List<String>) -> Unit
    ) {
        scope.launch {
            combine(
                currentPage,
                lastActions,
                settingsRepository.smartPredictionDelayMillisFlow,
                settingsRepository.isSmartPredictionEnabledFlow
            ) { page, _, delayMillis, enabled -> Triple(page, delayMillis, enabled) }
                .collect { (page, delayMillis, enabled) ->
                    if (page != null && enabled) {
                        val hasPredictor = page.buttonConfigs.any { 
                            it != null && featureGuard.isActionEnabled(it.buttonAction) && it.buttonAction is SmartPredictionButtonAction
                        }
                        
                        if (hasPredictor) {
                            val bookId = activeBookId.value
                            if (bookId != null) {
                                delay(delayMillis)
                                try {
                                    val predictions = predictNextActionUseCase.predict(page, bookId)
                                    onPredictionsUpdated(predictions)
                                } catch (e: Exception) {
                                    Log.e("SmartPredictionDelegate", "Smart Prediction failed", e)
                                }
                            }
                        } else {
                            onPredictionsUpdated(emptyList())
                        }
                    }
                }
        }
    }
}

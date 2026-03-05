package com.andreas_kratzer.ghosttalk.ui.pages.delegates

import android.util.Log
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.domain.CheckForPredictorUseCase
import com.andreas_kratzer.ghosttalk.domain.PredictNextActionUseCase
import com.andreas_kratzer.ghosttalk.model.Page
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

class SmartPredictionDelegate @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val predictNextActionUseCase: PredictNextActionUseCase,
    private val checkForPredictorUseCase: CheckForPredictorUseCase
) {
    fun init(
        scope: CoroutineScope,
        currentPage: StateFlow<Page?>,
        allPages: StateFlow<List<Page>>,
        lastActions: StateFlow<List<String>>,
        activeBookId: StateFlow<String?>,
        onPredictionsUpdated: (List<String>) -> Unit
    ) {
        scope.launch {
            combine(
                currentPage,
                allPages,
                lastActions,
                settingsRepository.isSmartPredictionEnabledFlow
            ) { page, pages, _, enabled -> Triple(page, pages, enabled) }
                .collect { (page, pages, enabled) ->
                    if (page != null && enabled) {
                        val hasPredictor = checkForPredictorUseCase(page)
                        
                        if (hasPredictor) {
                            val bookId = activeBookId.value
                            if (bookId != null) {
                                try {
                                    val predictions = predictNextActionUseCase.predict(page, pages, bookId)
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

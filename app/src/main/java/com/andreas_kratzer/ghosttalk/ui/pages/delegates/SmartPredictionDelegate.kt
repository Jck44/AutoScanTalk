package com.andreas_kratzer.ghosttalk.ui.pages.delegates

import android.util.Log
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.domain.settings.CheckForPredictorUseCase
import com.andreas_kratzer.ghosttalk.domain.actions.PredictNextActionUseCase
import com.andreas_kratzer.ghosttalk.model.Page
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

class SmartPredictionDelegate @Inject constructor(
    private val updateSmartPredictionsUseCase: UpdateSmartPredictionsUseCase
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
            updateSmartPredictionsUseCase.execute(
                currentPage = currentPage,
                allPages = allPages,
                activeBookId = activeBookId
            ).collect { predictions ->
                onPredictionsUpdated(predictions)
            }
        }
    }
}

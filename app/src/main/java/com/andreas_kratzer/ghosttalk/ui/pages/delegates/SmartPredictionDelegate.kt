package com.andreas_kratzer.ghosttalk.ui.pages.delegates

import com.andreas_kratzer.ghosttalk.domain.actions.UpdateSmartPredictionsUseCase
import com.andreas_kratzer.ghosttalk.model.Page
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
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
        onPredictionsUpdated: (List<String>?) -> Unit
    ) {
        scope.launch {
            updateSmartPredictionsUseCase.execute(
                currentPage = currentPage,
                allPages = allPages,
                activeBookId = activeBookId,
                history = lastActions
            ).collect { predictions ->
                onPredictionsUpdated(predictions)
            }
        }
    }
}

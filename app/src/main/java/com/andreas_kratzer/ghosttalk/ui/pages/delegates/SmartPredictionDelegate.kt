package com.andreas_kratzer.ghosttalk.ui.pages.delegates

import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.ai.domain.UpdateSmartPredictionsUseCase
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
        isUserModeActive: StateFlow<Boolean>,
        onPredictionsUpdated: (List<String>?) -> Unit
    ) {
        scope.launch {
            updateSmartPredictionsUseCase.execute(
                currentPage = currentPage,
                allPages = allPages,
                activeBookId = activeBookId,
                history = lastActions,
                isUserModeActive = isUserModeActive
            ).collect { predictions ->
                onPredictionsUpdated(predictions)
            }
        }
    }
}

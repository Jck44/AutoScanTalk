package com.andreas_kratzer.ghosttalk.domain.actions

import android.util.Log
import com.andreas_kratzer.ghosttalk.core.actions.ActionExecutor
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.SmartPredictionButtonAction
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import com.andreas_kratzer.ghosttalk.ui.pages.ScanCoordinator
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ActivateButtonUseCase @Inject constructor(
    private val ttsHelper: TextToSpeechHelper,
    private val resolveSmartPredictionUseCase: ResolveSmartPredictionUseCase
) {
    suspend fun execute(
        index: Int,
        currentPage: Page?,
        activeBookId: String?,
        isUserModeActive: Boolean,
        smartPredictions: List<String>,
        actionExecutor: ActionExecutor,
        scanCoordinator: ScanCoordinator
    ) {
        if (actionExecutor.isExecuting.value) {
            Log.d("ActivateButtonUseCase", "Ignoring button click at index $index as ActionExecutor is currently executing.")
            return
        }

        ttsHelper.stopNotificationTTS()
        
        val page = currentPage ?: return
        val buttonConfig = page.buttonConfigs.getOrNull(index) ?: return
        
        scanCoordinator.setFocusedIndex(index)
        
        val smartAction = buttonConfig.buttonAction as? SmartPredictionButtonAction
        if (smartAction != null) {
            val predictionId = smartPredictions.getOrNull(smartAction.rank - 1)
            if (predictionId != null) {
                resolveSmartPredictionUseCase.execute(
                    predictionId,
                    currentPage,
                    activeBookId,
                    isUserModeActive,
                    actionExecutor
                )
                return
            }
        }
        
        actionExecutor.executeButtonAction(
            buttonConfig, 
            bookId = activeBookId.takeIf { isUserModeActive }
        )
    }
}

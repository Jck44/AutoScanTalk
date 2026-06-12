package com.andreas_kratzer.ghosttalk.domain.actions

import android.util.Log
import com.andreas_kratzer.ghosttalk.core.actions.ActionExecutor
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.SmartPredictionButtonAction
import com.andreas_kratzer.ghosttalk.core.scanning.ScanCoordinator
import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
import javax.inject.Inject

class ActivateButtonUseCase @Inject constructor(
    private val ttsHelper: TextToSpeechHelper,
    private val resolveSmartPredictionUseCase: ResolveSmartPredictionUseCase,
    private val bookRepository: BookRepository
) {
    suspend fun execute(
        index: Int,
        currentPage: Page?,
        activeBookId: String?,
        isUserModeActive: Boolean,
        smartPredictions: List<String>,
        actionExecutor: ActionExecutor,
        scanCoordinator: ScanCoordinator,
        isHardwareTriggered: Boolean = false,
        globalIndex: Int? = null
    ) {
        val page = currentPage ?: return
        val buttonConfig = page.buttonConfigs.getOrNull(index) ?: return

        if (actionExecutor.isExecuting.value && buttonConfig.id != actionExecutor.lastExecutedButtonId) {
            Log.d("ActivateButtonUseCase", "Ignoring button click at index $index as ActionExecutor is currently executing a different button.")
            return
        }

        ttsHelper.stopNotificationTTS()
        
        scanCoordinator.setFocusedIndex(globalIndex ?: index)
        
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
        
        
        val skipLog = if (isUserModeActive && activeBookId != null) {
            val book = bookRepository.getBookById(activeBookId)
            book?.logIgnoredActions == false
        } else {
            false
        }

        actionExecutor.executeButtonAction(
            buttonConfig, 
            bookId = activeBookId.takeIf { isUserModeActive },
            pageId = page.id.takeIf { isUserModeActive },
            rows = page.rows,
            columns = page.columns,
            index = index,
            skipLog = skipLog,
            isHardwareTriggered = isHardwareTriggered
        )
    }
}

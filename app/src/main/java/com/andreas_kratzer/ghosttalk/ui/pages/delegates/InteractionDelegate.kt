package com.andreas_kratzer.ghosttalk.ui.pages.delegates

import android.util.Log
import com.andreas_kratzer.ghosttalk.core.actions.ActionExecutor
import com.andreas_kratzer.ghosttalk.domain.ActionLogUseCase
import com.andreas_kratzer.ghosttalk.domain.FeatureGuard
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.SmartPredictionButtonAction
import com.andreas_kratzer.ghosttalk.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import com.andreas_kratzer.ghosttalk.ui.pages.ScanCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

class InteractionDelegate @Inject constructor(
    private val scope: CoroutineScope,
    private val actionExecutor: ActionExecutor,
    private val actionLogUseCase: ActionLogUseCase,
    private val ttsHelper: TextToSpeechHelper,
    private val featureGuard: FeatureGuard
) {
    lateinit var scanCoordinator: ScanCoordinator
    lateinit var pageManagementDelegate: PageManagementDelegate

    private val _isUserModeActive = MutableStateFlow(false)
    val isUserModeActive: StateFlow<Boolean> = _isUserModeActive.asStateFlow()

    private val _lastActions = MutableStateFlow<List<String>>(emptyList())
    val lastActions: StateFlow<List<String>> = _lastActions.asStateFlow()

    private val _smartPredictions = MutableStateFlow<List<String>>(emptyList())
    val smartPredictions: StateFlow<List<String>> = _smartPredictions.asStateFlow()

    fun init() {
        _lastActions.value = actionLogUseCase.loadSavedLogs()
    }

    fun setUserModeActive(isActive: Boolean) {
        _isUserModeActive.value = isActive
        if (!isActive) {
            scanCoordinator.stopScanningTemporarily()
            ttsHelper.stopNotificationTTS()
        }
    }

    fun activateButtonAtIndex(index: Int, currentPage: Page?) {
        if (actionExecutor.isExecuting.value) {
            Log.d("InteractionDelegate", "Ignoring button click at index $index as ActionExecutor is currently executing.")
            return
        }

        ttsHelper.stopNotificationTTS()
        
        val page = currentPage ?: return
        val buttonConfig = page.buttonConfigs.getOrNull(index) ?: return
        
        scanCoordinator.setFocusedIndex(index)
        
        val smartAction = buttonConfig.buttonAction as? SmartPredictionButtonAction
        if (smartAction != null) {
            val prediction = _smartPredictions.value.getOrNull(smartAction.rank - 1)
            if (prediction != null) {
                resolveSmartPrediction(prediction)
                return
            }
        }
        
        actionExecutor.executeButtonAction(buttonConfig, bookId = pageManagementDelegate.activeBookId.value)
    }

    private fun resolveSmartPrediction(prediction: String) {
        scope.launch {
            val allPages = pageManagementDelegate.unfilteredPages.value
            val targetPage = allPages.find { it.name.equals(prediction, ignoreCase = true) }
            
            if (targetPage != null) {
                actionExecutor.executeButtonAction(
                    ButtonConfig(
                        label = targetPage.name,
                        auditoryCue = null,
                        buttonAction = NavigateToPageButtonAction(targetPage.id)
                    ),
                    bookId = pageManagementDelegate.activeBookId.value
                )
            } else {
                actionExecutor.executeButtonAction(
                    ButtonConfig(
                        label = prediction,
                        auditoryCue = null,
                        buttonAction = SpeakTextButtonAction(prediction)
                    ),
                    bookId = pageManagementDelegate.activeBookId.value
                )
            }
        }
    }

    fun activateFocusedButton(currentPage: Page?) {
        val focusedIdx = scanCoordinator.focusedButtonIndex.value
        val focusedRow = scanCoordinator.focusedRowIndex.value
        if (focusedIdx != null) {
            activateButtonAtIndex(focusedIdx, currentPage)
        } else if (focusedRow != null) {
            scanCoordinator.selectCurrentRow()
        }
    }

    fun logAction(actionText: String) {
        _lastActions.update { current ->
            actionLogUseCase.formatAndAddEntry(actionText, current)
        }
    }

    fun clearActionLogs() {
        _lastActions.value = emptyList()
        actionLogUseCase.clearLogs()
    }

    fun updateSmartPredictions(predictions: List<String>) {
        _smartPredictions.value = predictions
    }
}

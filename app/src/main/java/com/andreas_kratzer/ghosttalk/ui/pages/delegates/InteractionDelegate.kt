package com.andreas_kratzer.ghosttalk.ui.pages.delegates

import android.app.Application
import android.content.Intent
import android.util.Log
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.actions.ActionExecutor
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.domain.ActionLogUseCase
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.SmartPredictionButtonAction
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import com.andreas_kratzer.ghosttalk.ui.pages.ScanCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

class InteractionDelegate @Inject constructor(
    private val application: Application,
    private val pageRepository: PageRepository,
    private val settingsRepository: SettingsRepository,
    private val actionLogUseCase: ActionLogUseCase,
    private val ttsHelper: TextToSpeechHelper
) {
    private lateinit var scope: CoroutineScope
    private lateinit var actionExecutor: ActionExecutor
    lateinit var scanCoordinator: ScanCoordinator

    private val _lastActions = MutableStateFlow<List<String>>(emptyList())
    val lastActions: StateFlow<List<String>> = _lastActions.asStateFlow()

    private val _authRecoverIntent = MutableSharedFlow<Intent>()
    val authRecoverIntent: SharedFlow<Intent> = _authRecoverIntent.asSharedFlow()

    private val _isUserModeActive = MutableStateFlow(false)
    val isUserModeActive: StateFlow<Boolean> = _isUserModeActive.asStateFlow()

    private var _smartPredictions = MutableStateFlow<List<String>>(emptyList())

    // Callback to PageViewModel to load a page
    private var onPageLoadRequested: (Page) -> Unit = {}

    fun init(
        scope: CoroutineScope, 
        actionExecutor: ActionExecutor,
        onPageLoadRequested: (Page) -> Unit, 
        smartPredictions: MutableStateFlow<List<String>>
    ) {
        this.scope = scope
        this.actionExecutor = actionExecutor
        this.onPageLoadRequested = onPageLoadRequested
        this._smartPredictions = smartPredictions
        _lastActions.value = actionLogUseCase.loadSavedLogs()

        scope.launch {
            actionExecutor.events.collect { event ->
                when (event) {
                    is ActionExecutor.ExecutionEvent.NavigateToPage -> {
                        scope.launch {
                            val page = pageRepository.getPageById(event.pageId)
                            if (page != null) {
                                val idSuffix = if (settingsRepository.showPageIdInLog) " (ID: ${event.pageId})" else ""
                                logAction("Navigiert zu Seite: ${page.name}$idSuffix")
                                onPageLoadRequested(page)
                            } else {
                                val idSuffix = if (settingsRepository.showPageIdInLog) " mit ID '${event.pageId}'" else ""
                                logAction("Fehler: Seite$idSuffix nicht gefunden.")
                                ttsHelper.speak(application.getString(R.string.error_page_not_found)) {}
                            }
                        }
                    }
                    is ActionExecutor.ExecutionEvent.Log -> logAction(event.message)
                    is ActionExecutor.ExecutionEvent.Error -> logAction("Fehler: ${event.message}")
                    is ActionExecutor.ExecutionEvent.RecoverableAuthError -> _authRecoverIntent.emit(event.intent)
                }
            }
        }
    }

    fun setUserModeActive(isActive: Boolean) {
        _isUserModeActive.value = isActive
        if (!isActive) {
            scanCoordinator.stopScanningTemporarily()
            ttsHelper.stopNotificationTTS()
        }
    }

    fun activateButtonAtIndex(index: Int, currentPage: Page?, activeBookId: String?) {
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
            val predictionId = _smartPredictions.value.getOrNull(smartAction.rank - 1)
            if (predictionId != null) {
                resolveSmartPrediction(predictionId, currentPage, activeBookId)
                return
            }
        }
        
        actionExecutor.executeButtonAction(buttonConfig, bookId = activeBookId)
    }

    private fun resolveSmartPrediction(predictionId: String, currentPage: Page?, activeBookId: String?) {
        scope.launch {
            val matchingButton = currentPage?.buttonConfigs?.find { it?.id == predictionId }
            if (matchingButton != null) {
                actionExecutor.executeButtonAction(matchingButton, bookId = activeBookId)
                return@launch
            }

            val targetPage = pageRepository.getPageById(predictionId)
            if (targetPage != null) {
                actionExecutor.executeButtonAction(
                    ButtonConfig(
                        label = targetPage.name,
                        auditoryCue = null,
                        buttonAction = NavigateToPageButtonAction(targetPage.id)
                    ),
                    bookId = activeBookId
                )
            }
        }
    }

    fun activateFocusedButton(currentPage: Page?, activeBookId: String?) {
        val focusedIdx = scanCoordinator.focusedButtonIndex.value
        val focusedRow = scanCoordinator.focusedRowIndex.value
        if (focusedIdx != null) {
            activateButtonAtIndex(focusedIdx, currentPage, activeBookId)
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
}

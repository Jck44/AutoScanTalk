package com.andreas_kratzer.ghosttalk.ui.pages.delegates

import android.app.Application
import android.content.Intent
import com.andreas_kratzer.ghosttalk.core.actions.ActionExecutor
import com.andreas_kratzer.ghosttalk.core.data.AppStateRepository
import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.model.ActionLogEntry
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.scanning.ScanCoordinator
import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
import com.andreas_kratzer.ghosttalk.domain.actions.ActionLogUseCase
import com.andreas_kratzer.ghosttalk.domain.actions.ActivateButtonUseCase
import com.andreas_kratzer.ghosttalk.domain.actions.HandleActionExecutionEventUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

class InteractionDelegate @Inject constructor(
    private val application: Application,
    val actionLogUseCase: ActionLogUseCase,
    private val ttsHelper: TextToSpeechHelper,
    private val activateButtonUseCase: ActivateButtonUseCase,
    private val handleActionExecutionEventUseCase: HandleActionExecutionEventUseCase,
    private val locationExecutor: com.andreas_kratzer.ghosttalk.domain.executors.LocationExecutor,
    private val appStateRepository: AppStateRepository,
    private val bookRepository: BookRepository
) {
    private lateinit var scope: CoroutineScope
    private lateinit var actionExecutor: ActionExecutor
    lateinit var scanCoordinator: ScanCoordinator

    private val _lastActions = MutableStateFlow<List<ActionLogEntry>>(emptyList())
    val lastActions: StateFlow<List<ActionLogEntry>> = _lastActions.asStateFlow()

    private val _authRecoverIntent = MutableSharedFlow<Intent>()
    val authRecoverIntent = _authRecoverIntent.asSharedFlow()

    // Now pointing to the global repository
    val isUserModeActive: StateFlow<Boolean> = appStateRepository.isUserModeActive

    private var _smartPredictions = MutableStateFlow<List<String>?>(null)

    // Callback to PageViewModel to load a page
    private var onPageLoadRequested: (Page) -> Unit = {}

    fun init(
        scope: CoroutineScope, 
        actionExecutor: ActionExecutor,
        onPageLoadRequested: (Page) -> Unit, 
        smartPredictions: MutableStateFlow<List<String>?>,
        currentBookIdFlow: StateFlow<String?>
    ) {
        this.scope = scope
        this.actionExecutor = actionExecutor
        this.onPageLoadRequested = onPageLoadRequested
        this._smartPredictions = smartPredictions
        
        scope.launch {
            _lastActions.value = actionLogUseCase.loadSavedLogEntries()
        }

        scope.launch {
            currentBookIdFlow.collect { bookId ->
                // Optionally we could reload logs if we separate logs by book ID in persistence,
                // but for now they are global but limited by the book's setting when ADDING.
            }
        }

        scope.launch {
            actionExecutor.events.collect { event ->
                val effect = handleActionExecutionEventUseCase.execute(event) ?: return@collect
                when (effect) {
                    is HandleActionExecutionEventUseCase.Effect.LoadPage -> {
                        scope.launch {
                            val bookId = appStateRepository.activeBookId.value // Or pass it via event
                            logAction(effect.logMessage, bookId)
                        }
                        onPageLoadRequested(effect.page)
                    }
                    is HandleActionExecutionEventUseCase.Effect.LogAction -> {
                        scope.launch {
                            val bookId = appStateRepository.activeBookId.value
                            logAction(effect.message, bookId)
                        }
                    }
                    is HandleActionExecutionEventUseCase.Effect.SpeakError -> {
                        scope.launch {
                            val bookId = appStateRepository.activeBookId.value
                            logAction(effect.logMessage, bookId)
                        }
                        ttsHelper.speak(application.getString(effect.messageResId)) {}
                    }
                    is HandleActionExecutionEventUseCase.Effect.EmitAuthIntent -> {
                        _authRecoverIntent.emit(effect.intent)
                    }
                }
            }
        }
    }

    fun setUserModeActive(isActive: Boolean) {
        appStateRepository.setUserModeActive(isActive)
        if (isActive) {
            scope.launch {
                locationExecutor.refreshLocation()
            }
        } else {
            ttsHelper.stopNotificationTTS()
        }
    }

    fun activateButtonAtIndex(index: Int, currentPage: Page?, activeBookId: String?) {
        scope.launch {
            activateButtonUseCase.execute(
                index = index,
                currentPage = currentPage,
                activeBookId = activeBookId,
                isUserModeActive = isUserModeActive.value,
                smartPredictions = _smartPredictions.value ?: emptyList(),
                actionExecutor = actionExecutor,
                scanCoordinator = scanCoordinator
            )
        }
    }

    fun activateFocusedButton(currentPage: Page?, activeBookId: String?) {
        if (scanCoordinator.isStoppedDueToLimit.value) {
            scanCoordinator.restartScanning()
            return
        }
        val focusedIdx = scanCoordinator.focusedButtonIndex.value
        val focusedRow = scanCoordinator.focusedRowIndex.value
        if (focusedIdx != null) {
            activateButtonAtIndex(focusedIdx, currentPage, activeBookId)
        } else if (focusedRow != null) {
            scanCoordinator.selectCurrentRow()
        }
    }

    fun logAction(actionText: String, bookId: String?) {
        scope.launch {
            val limit = if (bookId != null) {
                bookRepository.getBookById(bookId)?.actionLogLimit ?: 100
            } else {
                100
            }
            _lastActions.update { current ->
                actionLogUseCase.formatAndAddEntry(actionText, current, limit)
            }
        }
    }

    fun clearActionLogs() {
        _lastActions.value = emptyList()
        actionLogUseCase.clearLogs()
    }
}

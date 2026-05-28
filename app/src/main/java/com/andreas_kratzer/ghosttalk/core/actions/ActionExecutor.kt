package com.andreas_kratzer.ghosttalk.core.actions

import com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.di.ApplicationScope
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActionExecutor @Inject constructor(
    @param:ApplicationScope private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val buttonUsageRepository: ButtonUsageRepository,
    private val handlers: Set<@JvmSuppressWildcards ActionHandler>,
    private val actionCoordinator: ActionCoordinator,
    private val ttsHelper: TextToSpeechHelper
) : ScannerActionProvider {
    private var timeProvider: () -> Long = { System.currentTimeMillis() }
    
    internal fun setTimeProviderForTest(provider: () -> Long) {
        this.timeProvider = provider
    }

    private val _isExecuting = MutableStateFlow(false)
    override val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    // Delegate events to the coordinator
    val events: SharedFlow<ActionExecutionEvent> = actionCoordinator.events

    private var lastExecutionTime = -1L
    private var activeExecutionId = 0
    
    var lastExecutedButtonId: String? = null
        private set

    private fun log(message: String, action: com.andreas_kratzer.ghosttalk.core.model.ButtonAction? = null, label: String? = null) {
        actionCoordinator.log(message, action, label)
    }

    fun executeButtonAction(
        buttonConfig: ButtonConfig, 
        bookId: String? = null,
        pageId: String? = null,
        rows: Int = 1,
        columns: Int = 1,
        index: Int = -1,
        skipLog: Boolean = false
    ) {
        val currentTime = timeProvider()
        val holdingTime = settingsRepository.holdingTimeMillis
        
        synchronized(this) {
            if (lastExecutionTime != -1L && currentTime - lastExecutionTime < holdingTime) {
                if (!skipLog) {
                    log("Aktion ignoriert (Haltezeit aktiv: ${holdingTime}ms)", buttonConfig.buttonAction)
                }
                return
            }
            
            if (_isExecuting.value) {
                if (buttonConfig.id == lastExecutedButtonId) {
                    stopActions(skipLog)
                    return
                }
                if (!skipLog) {
                    log("Aktion ignoriert (Aktion läuft bereits)", buttonConfig.buttonAction)
                }
                return
            }

            lastExecutionTime = currentTime
            lastExecutedButtonId = buttonConfig.id
            _isExecuting.value = true
        }

        // Interrupt any ongoing scanner cues or previous actions
        ttsHelper.stopAll()
        
        val currentExecutionId = ++activeExecutionId

        if (bookId != null && index != -1) {
            scope.launch {
                try {
                    buttonUsageRepository.recordUsage(bookId, pageId ?: "", buttonConfig, rows, columns, index)
                } catch (_: Exception) { }
            }
        }

        val action = buttonConfig.buttonAction
        val handler = handlers.find { it.canHandle(action) }
        
        if (handler != null) {
            try {
                handler.handle(
                    buttonConfig,
                    action,
                    currentExecutionId,
                    ::finishExecution
                )
            } catch (e: Exception) {
                actionCoordinator.error("Handler execution failed: ${e.message}", e)
                finishExecution(currentExecutionId)
            }
        } else {
            log("Kein Handler für Aktion gefunden: ${action::class.simpleName}")
            finishExecution(currentExecutionId)
        }
    }

    private fun finishExecution(executionId: Int) {
        if (executionId == activeExecutionId) {
            _isExecuting.value = false
        }
    }

    /**
     * Stoppt die aktuelle Aktion und setzt den Ausführungsstatus zurück.
     * Wird z.B. bei einem Seitenwechsel aufgerufen.
     */
    fun stopActions(skipLog: Boolean = false) {
        if (!skipLog) {
            log("Stoppe alle laufenden Aktionen (z.B. wegen Seitenwechsel)")
        }
        // Incremenet execution ID to orphan ANY current callbacks, just in case
        activeExecutionId++
        _isExecuting.value = false
        lastExecutedButtonId = null
        
        // Actually tell the TTS helper to stop audio
        ttsHelper.stopAll()
    }

    internal fun setExecutingStateForTest(executing: Boolean) {
        _isExecuting.value = executing
    }
}

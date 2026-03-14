package com.andreas_kratzer.ghosttalk.core.actions

import com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.di.ApplicationScope
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
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
    private val actionCoordinator: ActionCoordinator
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

    private fun log(message: String) {
        actionCoordinator.log(message)
    }

    fun executeButtonAction(
        buttonConfig: ButtonConfig, 
        bookId: String? = null,
        rows: Int = 1,
        columns: Int = 1,
        index: Int = -1
    ) {
        val currentTime = timeProvider()
        val holdingTime = settingsRepository.holdingTimeMillis
        
        if (lastExecutionTime != -1L && currentTime - lastExecutionTime < holdingTime) {
            log("Aktion ignoriert (Haltezeit aktiv: ${holdingTime}ms)")
            return
        }
        
        if (_isExecuting.value) {
            log("Aktion ignoriert (Aktion läuft bereits)")
            return
        }
        
        lastExecutionTime = currentTime
        val currentExecutionId = ++activeExecutionId
        _isExecuting.value = true

        if (bookId != null && index != -1) {
            scope.launch {
                try {
                    buttonUsageRepository.recordUsage(bookId, buttonConfig, rows, columns, index)
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

    internal fun setExecutingStateForTest(executing: Boolean) {
        _isExecuting.value = executing
    }
}

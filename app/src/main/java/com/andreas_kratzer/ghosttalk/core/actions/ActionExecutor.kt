package com.andreas_kratzer.ghosttalk.core.actions

import android.app.Application
import com.andreas_kratzer.ghosttalk.core.di.ApplicationScope
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.andreas_kratzer.ghosttalk.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.domain.genai.GeminiUseCase
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActionExecutor @Inject constructor(
    private val application: Application,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val logger: Logger,
    private val localIntentRouter: com.andreas_kratzer.ghosttalk.domain.executors.LocalIntentRouter,
    private val weatherExecutor: com.andreas_kratzer.ghosttalk.domain.executors.WeatherExecutor,
    private val buttonUsageRepository: ButtonUsageRepository,
    private val geminiUseCaseLazy: dagger.Lazy<GeminiUseCase>,
    private val ttsHelperLazy: dagger.Lazy<TextToSpeechHelper>
) {
    private var timeProvider: () -> Long = { System.currentTimeMillis() }
    
    internal fun setTimeProviderForTest(provider: () -> Long) {
        this.timeProvider = provider
    }
    sealed class ExecutionEvent {
        data class NavigateToPage(val pageId: String) : ExecutionEvent()
        data class Log(val message: String) : ExecutionEvent()
        data class Error(val message: String) : ExecutionEvent()
        data class RecoverableAuthError(val intent: android.content.Intent) : ExecutionEvent()
    }

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    private val _events = MutableSharedFlow<ExecutionEvent>()
    val events: SharedFlow<ExecutionEvent> = _events.asSharedFlow()

    private var lastExecutionTime = -1L
    private var activeExecutionId = 0

    // Proxy for TTS to be used by core handlers
    private val actionTtsProxy = object : ActionTtsProxy {
        override val isReady: Boolean get() = ttsHelperLazy.get().isReady
        override var isReadingNotification: Boolean 
            get() = ttsHelperLazy.get().isReadingNotification
            set(value) { ttsHelperLazy.get().isReadingNotification = value }
        
        override fun speakRouted(text: String, deviceAddress: String?, queueMode: Int, isForCues: Boolean, onDone: (() -> Unit)?) {
            ttsHelperLazy.get().speakRouted(text, deviceAddress, queueMode, isForCues, onDone)
        }
    }

    private val controlDeviceTtsProxy = object : ControlDeviceTtsProxy {
        override val isReady: Boolean get() = ttsHelperLazy.get().isReady
        override var isReadingNotification: Boolean 
            get() = ttsHelperLazy.get().isReadingNotification
            set(value) { ttsHelperLazy.get().isReadingNotification = value }
        
        override fun speakRouted(text: String, deviceAddress: String?, onDone: (() -> Unit)?) {
            ttsHelperLazy.get().speakRouted(text, deviceAddress, onDone = onDone)
        }
    }

    internal var handlers: List<ActionHandler> = listOf(
        SpeechActionHandler(settingsRepository, dagger.Lazy { actionTtsProxy }, ::log),
        NavigationActionHandler(scope, settingsRepository, ttsHelperLazy, ::emitEvent, ::log),
        ControlDeviceActionHandler(
            application, 
            settingsRepository, 
            dagger.Lazy { controlDeviceTtsProxy }, 
            ::log,
            { id, args -> application.getString(id, *args) }
        ),
        WeatherActionHandler(application, settingsRepository, dagger.Lazy { actionTtsProxy }, weatherExecutor, scope, ::log),
        GeminiActionHandler(
            scope, 
            settingsRepository, 
            geminiUseCaseLazy, 
            localIntentRouter, 
            dagger.Lazy { actionTtsProxy }, 
            ::emitEvent, 
            ::log, 
            ::error,
            { id, args -> application.getString(id, *args) }
        ),
        FrequentActionHandler(::log),
        SmartPredictionActionHandler(::log)
    )

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
            handler.handle(
                buttonConfig,
                action,
                currentExecutionId,
                ::finishExecution
            )
        } else {
            log("Kein Handler für Aktion gefunden: ${action::class.simpleName}")
            logger.d("ActionExecutor", "No handler for ${action::class.simpleName}")
            finishExecution(currentExecutionId)
        }
    }

    private fun finishExecution(executionId: Int) {
        if (executionId == activeExecutionId) {
            _isExecuting.value = false
        }
    }

    private fun log(message: String) {
        logger.d("ActionExecutor", "Log: $message")
        scope.launch { _events.emit(ExecutionEvent.Log(message)) }
    }

    private fun error(message: String, throwable: Throwable? = null) {
        logger.e("ActionExecutor", message, throwable)
        scope.launch { _events.emit(ExecutionEvent.Log("Error: $message")) }
    }

    private suspend fun emitEvent(event: ExecutionEvent) {
        _events.emit(event)
    }
    internal fun setExecutingStateForTest(executing: Boolean) {
        _isExecuting.value = executing
    }
}

package com.andreas_kratzer.ghosttalk.core.actions

import android.content.Context
import com.andreas_kratzer.ghosttalk.core.analytics.FirebaseAnalyticsManager
import com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.di.ApplicationScope
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val ttsHelper: TextToSpeechHelper,
    private val scanCoordinatorProvider: javax.inject.Provider<com.andreas_kratzer.ghosttalk.core.scanning.ScanCoordinator>,
    private val firebaseAnalyticsManager: FirebaseAnalyticsManager,
    @param:ApplicationContext private val context: Context? = null
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
        skipLog: Boolean = false,
        isHardwareTriggered: Boolean = false
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
            val onlyHardware = settingsRepository.onlyRecordHardwareStats
            val isScanningActive = try {
                scanCoordinatorProvider.get().isScanning.value
            } catch (_: Exception) {
                false
            }
            val isTouchIntervention = isScanningActive && !isHardwareTriggered

            if (!onlyHardware || isHardwareTriggered || isTouchIntervention) {
                val reactionTimeMs = try {
                    scanCoordinatorProvider.get().getLastFocusDuration(index)
                } catch (_: Exception) {
                    null
                }
                
                // Heuristic for late/accidental clicks:
                // Pressed button within threshold milliseconds since last focus change OR predecessor speech audio is still playing.
                var isAccidental = false
                var intendedButtonId: String? = null
                try {
                    val scanCoord = scanCoordinatorProvider.get()
                    val threshold = settingsRepository.lateClickThresholdMillis
                    val timeSinceFocus = scanCoord.getTimeSinceLastFocusChangeMs()
                    val isTtsSpeaking = ttsHelper.isSpeaking()
                    val prevFocused = scanCoord.getPreviousFocusedButton()
                    
                    intendedButtonId = prevFocused
                    
                    android.util.Log.d("ActionExecutorHeuristic", "Heuristic calculation: threshold=$threshold, timeSinceFocus=$timeSinceFocus, isTtsSpeaking=$isTtsSpeaking, prevFocused=$prevFocused, currentButtonId=${buttonConfig.id}")
                    
                    if (timeSinceFocus <= threshold || isTtsSpeaking) {
                        if (intendedButtonId != null && intendedButtonId != buttonConfig.id) {
                            isAccidental = true
                        }
                    }
                    android.util.Log.d("ActionExecutorHeuristic", "Heuristic result: isAccidental=$isAccidental, intendedButtonId=$intendedButtonId")
                } catch (e: Exception) {
                    android.util.Log.e("ActionExecutorHeuristic", "Error calculating heuristic", e)
                }

                val scanCycles = try {
                    scanCoordinatorProvider.get().currentCycleCount.value
                } catch (_: Exception) {
                    null
                }

                scope.launch {
                    try {
                        buttonUsageRepository.recordUsage(
                            bookId = bookId,
                            pageId = pageId ?: "",
                            buttonConfig = buttonConfig,
                            rows = rows,
                            columns = columns,
                            indexInPage = index,
                            reactionTimeMs = reactionTimeMs,
                            isTouchIntervention = isTouchIntervention,
                            isHardwareTriggered = isHardwareTriggered,
                            scanCyclesBeforeClick = scanCycles,
                            isAccidental = isAccidental,
                            intendedButtonId = intendedButtonId
                        )
                    } catch (_: Exception) { }
                }
            }
        }


        val action = buttonConfig.buttonAction
        
        // Check for required runtime permissions
        val ctx = context
        if (ctx != null) {
            val required = getRequiredPermissions(action)
            if (required.isNotEmpty()) {
                val missing = required.filter {
                    androidx.core.content.ContextCompat.checkSelfPermission(ctx, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
                }
                if (missing.isNotEmpty()) {
                    log("Fehlende Berechtigungen: $missing. Spreche Warnung per TTS...", action, buttonConfig.label)
                    
                    val resId = when {
                        missing.contains(android.Manifest.permission.CAMERA) -> com.andreas_kratzer.ghosttalk.R.string.error_camera_permission_missing
                        missing.contains(android.Manifest.permission.READ_CALENDAR) -> com.andreas_kratzer.ghosttalk.R.string.error_calendar_permission_missing
                        missing.contains(android.Manifest.permission.SEND_SMS) -> com.andreas_kratzer.ghosttalk.R.string.error_sms_permission_missing
                        missing.contains(android.Manifest.permission.CALL_PHONE) -> com.andreas_kratzer.ghosttalk.R.string.error_phone_permission_missing
                        missing.contains(android.Manifest.permission.ACCESS_FINE_LOCATION) || 
                                missing.contains(android.Manifest.permission.ACCESS_COARSE_LOCATION) -> com.andreas_kratzer.ghosttalk.R.string.error_location_permission_missing
                        else -> -1
                    }

                    if (resId != -1) {
                        val message = ctx.getString(resId)
                        val targetDeviceAddress = if (buttonConfig.playActionAsAuditoryCue) {
                            settingsRepository.cuesAudioDeviceAddress
                        } else {
                            settingsRepository.ttsAudioDeviceAddress
                        }
                        ttsHelper.speakRouted(message, targetDeviceAddress) {
                            finishExecution(currentExecutionId)
                        }
                    } else {
                        finishExecution(currentExecutionId)
                    }
                    return
                }
            }
        }

        val handler = handlers.find { it.canHandle(action) }
        
        if (handler != null) {
            val featureType = when (action) {
                is com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction -> "tts_speak"
                is com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction,
                is com.andreas_kratzer.ghosttalk.core.model.NavigateBackButtonAction,
                is com.andreas_kratzer.ghosttalk.core.model.NavigateToStartPageButtonAction -> "navigation"
                is com.andreas_kratzer.ghosttalk.core.model.WeatherButtonAction -> "weather"
                is com.andreas_kratzer.ghosttalk.core.model.GeminiVisionButtonAction -> "gemini_vision"
                is com.andreas_kratzer.ghosttalk.core.model.SmartHomeButtonAction -> "smarthome_hue"
                is com.andreas_kratzer.ghosttalk.core.model.PlayMediaButtonAction -> "play_media"
                is com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction -> "control_device"
                is com.andreas_kratzer.ghosttalk.core.model.FrequentActionButtonAction -> "smart_prediction"
                else -> action::class.java.simpleName.lowercase().replace("buttonaction", "").replace("action", "")
            }
            firebaseAnalyticsManager.logFeatureUsed(featureType)

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

    private fun getRequiredPermissions(action: com.andreas_kratzer.ghosttalk.core.model.ButtonAction): List<String> {
        return when (action) {
            is com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction -> {
                when (action.actionType) {
                    com.andreas_kratzer.ghosttalk.core.model.DeviceActionType.READ_CALENDAR_ENTRIES -> listOf(android.Manifest.permission.READ_CALENDAR)
                    com.andreas_kratzer.ghosttalk.core.model.DeviceActionType.SEND_MESSAGE,
                    com.andreas_kratzer.ghosttalk.core.model.DeviceActionType.SEND_LAST_SPOKEN_SMS -> listOf(android.Manifest.permission.SEND_SMS)
                    com.andreas_kratzer.ghosttalk.core.model.DeviceActionType.START_CALL -> {
                        if (settingsRepository.simulateCallsEnabled) emptyList()
                        else listOf(android.Manifest.permission.CALL_PHONE)
                    }
                    else -> emptyList()
                }
            }
            is com.andreas_kratzer.ghosttalk.core.model.WeatherButtonAction -> {
                listOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            is com.andreas_kratzer.ghosttalk.core.model.GeminiVisionButtonAction -> {
                listOf(android.Manifest.permission.CAMERA)
            }
            else -> emptyList()
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

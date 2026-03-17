package com.andreas_kratzer.ghosttalk.core.scanning

import android.util.Log
import com.andreas_kratzer.ghosttalk.core.actions.ScannerActionProvider
import com.andreas_kratzer.ghosttalk.core.di.ApplicationScope
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.settings.ScanningSettings
import com.andreas_kratzer.ghosttalk.core.settings.FeatureSettings
import com.andreas_kratzer.ghosttalk.core.ai.domain.CheckForPredictorUseCase
import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates scanning lifecycle: start, stop, pause, resume.
 */
@Singleton
class ScanCoordinator @Inject constructor(
    @param:ApplicationScope private val scope: CoroutineScope,
    private val scannerEngine: ScannerEngine,
    private val scanningSettings: ScanningSettings,
    private val featureSettings: FeatureSettings,
    private val actionProvider: ScannerActionProvider,
    private val checkForPredictorUseCase: CheckForPredictorUseCase,
    private val ttsHelper: TextToSpeechHelper
) {
    // These streams are now initialized later or passed when needed, 
    // as a Singleton cannot hold pointers to UI-specific hot-flows from birth.
    private var currentPage: StateFlow<Page?>? = null
    private var isUserModeActive: StateFlow<Boolean>? = null
    private var resolvedPage: StateFlow<Page?>? = null
    private var isSmartPredictionLoading: StateFlow<Boolean>? = null
    private var smartPredictions: StateFlow<List<String>?>? = null
    private data class Data(
        val isExecuting: Boolean,
        val resolvedPage: Page?,
        val rawPage: Page?,
        val isActive: Boolean,
        val isLoading: Boolean,
        val predictions: List<String>?
    )
    val focusedButtonIndex: StateFlow<Int?> = scannerEngine.focusedButtonIndex
    val focusedRowIndex: StateFlow<Int?> = scannerEngine.focusedRowIndex
    val isScanning: StateFlow<Boolean> = scannerEngine.isScanning
    
    private val _currentCycleCount = MutableStateFlow(0)
    val currentCycleCount: StateFlow<Int> = _currentCycleCount.asStateFlow()
    
    private val _isStoppedDueToLimit = MutableStateFlow(false)
    val isStoppedDueToLimit: StateFlow<Boolean> = _isStoppedDueToLimit.asStateFlow()
    
    private var scanCycleLimitEnabled = false
    private var scanCycleLimit = 2

    private var lastCuePageId: String? = null
    fun init(
        currentPage: StateFlow<Page?>,
        isUserModeActive: StateFlow<Boolean>,
        resolvedPage: StateFlow<Page?>,
        isSmartPredictionLoading: StateFlow<Boolean>,
        smartPredictions: StateFlow<List<String>?>
    ) {
        this.currentPage = currentPage
        this.isUserModeActive = isUserModeActive
        this.resolvedPage = resolvedPage
        this.isSmartPredictionLoading = isSmartPredictionLoading
        this.smartPredictions = smartPredictions

        // Scanning trigger: observe ActionProvider, resolved page, raw page, user mode state, and Gemini status in a single combine
        scope.launch {
            combine(
                isUserModeActive,
                actionProvider.isExecuting,
                currentPage,
                resolvedPage,
                isSmartPredictionLoading,
                smartPredictions
            ) { array ->
                Data(
                    isExecuting = array[1] as Boolean,
                    resolvedPage = array[3] as? Page,
                    rawPage = array[2] as? Page,
                    isActive = array[0] as Boolean,
                    isLoading = array[4] as Boolean,
                    predictions = (array[5] as? List<*>)?.filterIsInstance<String>()
                )
            }.collect { data ->
                if (!data.isActive) {
                    Log.d("ScanCoordinator", "User mode deactivated. Stopping scan.")
                    stopScanning()
                    return@collect
                }

                if (data.isExecuting) {
                    Log.d("ScanCoordinator", "ActionExecutor is executing. Pausing scan.")
                    stopScanningTemporarily()
                    return@collect
                }

                val rawPage = data.rawPage
                if (rawPage != null && checkForPredictorUseCase(rawPage)) {
                    val isWaiting = isWaitingForPredictions(
                        isLoading = data.isLoading,
                        predictions = data.predictions,
                        rawPage = data.rawPage,
                        resPage = data.resolvedPage
                    )

                    if (isWaiting) {
                        if (rawPage.id != lastCuePageId && featureSettings.isSmartPredictionEnabled) {
                            Log.d("ScanCoordinator", "Page ${rawPage.name} has predictor and is waiting. Speaking cue.")
                            ttsHelper.speak("Befrage Gemini Nano")
                            lastCuePageId = rawPage.id
                        }
                        Log.d("ScanCoordinator", "Waiting for Gemini prediction. Pausing scan.")
                        stopScanningTemporarily()
                        return@collect
                    }
                }

                if (data.resolvedPage != null) {
                    resumeScanningIfEnabled()
                }
            }
        }

        // React to scan delay changes
        scope.launch {
            scanningSettings.scanDelayFlow.collect { delay -> setScanDelay(delay) }
        }

        // React to cycle completions
        scope.launch {
            scannerEngine.onCycleCompleted.collect {
                _currentCycleCount.value += 1
                Log.d("ScanCoordinator", "Cycle completed. Count: ${_currentCycleCount.value}")
                
                if (scanCycleLimitEnabled && _currentCycleCount.value >= scanCycleLimit) {
                    Log.d("ScanCoordinator", "Cycle limit reached ($scanCycleLimit). Stopping scan.")
                    _isStoppedDueToLimit.value = true
                    stopScanning()
                }
            }
        }
    }

    private fun isWaitingForPredictions(
        isLoading: Boolean, 
        predictions: List<String>?, 
        rawPage: Page?, 
        resPage: Page?
    ): Boolean {
        if (rawPage == null) return false
        val hasPredictor = checkForPredictorUseCase(rawPage)
        if (!hasPredictor) return false

        // 1. We are waiting if we are actively loading OR predictions haven't arrived yet (null)
        val isWaitingForModel = isLoading || predictions == null
        
        // 2. We are also waiting if predictions HAVE arrived but are NOT yet reflected in resolvedPage
        // (i.e. resolvedPage still has predictors)
        val hasPredictorsInResolved = checkForPredictorUseCase(resPage)
        val isWaitingForResolution = predictions != null && hasPredictorsInResolved
        
        Log.d("ScanCoordinator", "isWaiting: model=$isWaitingForModel (loading=$isLoading, pred=${predictions != null}), res=$isWaitingForResolution (hasPred=$hasPredictorsInResolved)")
        
        return isWaitingForModel || isWaitingForResolution
    }

    fun resumeScanningIfEnabled() {
        if (isUserModeActive?.value != true) {
            Log.d("ScanCoordinator", "resumeScanningIfEnabled: User mode inactive, skipping.")
            return
        }
        if (actionProvider.isExecuting.value) {
            Log.d("ScanCoordinator", "resumeScanningIfEnabled: ActionProvider busy, skipping.")
            return
        }

        val loading = isSmartPredictionLoading?.value ?: false
        val preds = smartPredictions?.value
        val currentP = currentPage?.value
        val resP = resolvedPage?.value

        if (isWaitingForPredictions(
            isLoading = loading,
            predictions = preds,
            rawPage = currentP,
            resPage = resP
        )) {
            Log.d("ScanCoordinator", "resumeScanningIfEnabled: Waiting for predictions/resolution, skipping.")
            return
        }

        if (scannerEngine.isScanning.value) {
            Log.d("ScanCoordinator", "resumeScanningIfEnabled: Already scanning, skipping.")
            return
        }

        if (scanningSettings.autoStartScanning) {
            val currentIndex = focusedButtonIndex.value ?: 0
            val startIndex = if (scanningSettings.resumeScanningFromStart) {
                0
            } else {
                currentIndex
            }
            startScanning(startIndex)
        }
    }

    fun startScanning(startIndex: Int = 0) {
        if (isUserModeActive?.value != true) {
            Log.d("ScanCoordinator", "startScanning: User mode inactive, skipping.")
            return
        }
        if (_isStoppedDueToLimit.value) {
            Log.d("ScanCoordinator", "startScanning: Stopped due to limit, skipping.")
            return
        }
        val page = resolvedPage?.value ?: return
        scannerEngine.startScanning(
            buttonConfigs = page.buttonConfigs,
            startIndex = startIndex,
            pattern = page.scanPattern ?: scanningSettings.defaultScanPattern,
            rows = page.rows,
            columns = page.columns,
            rowNames = page.rowNames,
            pageId = page.id
        )
    }

    fun setScanLimitSettings(enabled: Boolean, limit: Int) {
        Log.d("ScanCoordinator", "setScanLimitSettings: enabled=$enabled, limit=$limit")
        scanCycleLimitEnabled = enabled
        scanCycleLimit = limit
        
        // If limit was just enabled and current count already exceeds it, stop
        if (enabled && _currentCycleCount.value >= limit && scannerEngine.isScanning.value) {
            _isStoppedDueToLimit.value = true
            stopScanning()
        }
    }

    fun setCycleCount(count: Int) {
        _currentCycleCount.value = count
    }

    fun restartScanning() {
        Log.d("ScanCoordinator", "restartScanning: Resetting count and restarting.")
        _currentCycleCount.value = 0
        _isStoppedDueToLimit.value = false
        startScanning(0)
    }

    fun setScanDelay(delayMillis: Long) {
        scannerEngine.scanDelayMillis = delayMillis
        if (scanningSettings.autoStartScanning && isUserModeActive?.value == true) {
            startScanning()
        }
    }

    fun stopScanningTemporarily() {
        scannerEngine.pauseScanning()
    }

    fun stopScanning() {
        scannerEngine.stopScanning()
    }

    fun selectCurrentRow() {
        _currentCycleCount.value = 0 // Reset for buttons-in-row phase
        _isStoppedDueToLimit.value = false
        scannerEngine.selectCurrentRow()
    }

    fun setFocusedIndex(index: Int?) {
        scannerEngine.setFocusedIndex(index)
    }

    fun setFocusedRowIndex(index: Int?) {
        scannerEngine.setFocusedRowIndex(index)
    }

    fun onPageChanged(isSamePage: Boolean) {
        if (isSamePage) {
            scannerEngine.pauseScanning()
        } else {
            scannerEngine.stopScanning()
            _currentCycleCount.value = 0
            _isStoppedDueToLimit.value = false
        }
        lastCuePageId = null // Ensure cue is spoken again on explicit re-navigation
    }

    fun clear() {
        scannerEngine.clear()
    }
}

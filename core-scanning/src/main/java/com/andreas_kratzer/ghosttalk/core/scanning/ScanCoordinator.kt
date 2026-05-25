package com.andreas_kratzer.ghosttalk.core.scanning

import com.andreas_kratzer.ghosttalk.core.actions.CallActionProxy
import com.andreas_kratzer.ghosttalk.core.actions.ScannerActionProvider
import com.andreas_kratzer.ghosttalk.core.actions.ScannerController
import com.andreas_kratzer.ghosttalk.core.ai.domain.CheckForPredictorUseCase
import com.andreas_kratzer.ghosttalk.core.di.ApplicationScope
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.settings.FeatureSettings
import com.andreas_kratzer.ghosttalk.core.settings.ScanningSettings
import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanCoordinator @Inject constructor(
    @param:ApplicationScope private val scope: CoroutineScope,
    private val scannerEngine: ScannerEngine,
    private val scanningSettings: ScanningSettings,
    private val featureSettings: FeatureSettings,
    private val actionProvider: ScannerActionProvider,
    private val checkForPredictorUseCase: CheckForPredictorUseCase,
    private val ttsHelper: TextToSpeechHelper,
    private val callActionProxy: CallActionProxy
) : ScannerController {
    private var currentPage: StateFlow<Page?>? = null
    private var isUserModeActive: StateFlow<Boolean>? = null
    private var resolvedPage: StateFlow<Page?>? = null
    private var isSmartPredictionLoading: StateFlow<Boolean>? = null
    private var smartPredictions: StateFlow<List<String>?>? = null
    private var observeJob: kotlinx.coroutines.Job? = null

    private data class Data(
        val isExecuting: Boolean,
        val resolvedPage: Page?,
        val rawPage: Page?,
        val isActive: Boolean,
        val isLoading: Boolean,
        val predictions: List<String>?,
        val isInCall: Boolean
    )

    val focusedButtonIndex: StateFlow<Int?> = scannerEngine.focusedButtonIndex
    val focusedRowIndex: StateFlow<Int?> = scannerEngine.focusedRowIndex
    val isScanning: StateFlow<Boolean> = scannerEngine.isScanning
    
    private val _currentCycleCount = MutableStateFlow(0)
    val currentCycleCount: StateFlow<Int> = _currentCycleCount.asStateFlow()
    
    private val _isStoppedDueToLimit = MutableStateFlow(false)
    val isStoppedDueToLimit: StateFlow<Boolean> = _isStoppedDueToLimit.asStateFlow()

    private val _isPausedManually = MutableStateFlow(false)
    override val isPausedManually: StateFlow<Boolean> = _isPausedManually.asStateFlow()
    
    private var scanCycleLimitEnabled = false
    private var scanCycleLimit = 2

    private var lastCuePageId: String? = null

    private fun debugLog(message: String) {
        println("ScanCoordinator: $message")
    }

    init {
        scope.launch {
            scannerEngine.onCycleCompleted.collect {
                handleCycleCompleted()
            }
        }
        scope.launch {
            scanningSettings.scanDelayFlow.collect { delay ->
                scannerEngine.scanDelayMillis = delay
            }
        }
    }

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

        observeJob?.cancel()
        observeJob = scope.launch {
            combine(
                isUserModeActive,
                actionProvider.isExecuting,
                currentPage,
                resolvedPage,
                isSmartPredictionLoading,
                smartPredictions,
                isPausedManually,
                callActionProxy.isInCall
            ) { array ->
                Data(
                    isExecuting = array[1] as Boolean,
                    resolvedPage = array[3] as? Page,
                    rawPage = array[2] as? Page,
                    isActive = array[0] as Boolean,
                    isLoading = array[4] as Boolean,
                    predictions = (array[5] as? List<*>)?.filterIsInstance<String>(),
                    isInCall = array[7] as Boolean
                )
            }.collect { data ->
                if (!data.isActive || _isPausedManually.value || data.isInCall) {
                    if (!data.isActive) {
                        debugLog("User mode deactivated. Stopping scan.")
                        stopScanning()
                        _isStoppedDueToLimit.value = false
                        _currentCycleCount.value = 0
                        _isPausedManually.value = false
                    } else if (data.isInCall) {
                        debugLog("In a call. Stopping scan.")
                        stopScanningTemporarily()
                    } else {
                        debugLog("Scanning is manually paused.")
                        stopScanningTemporarily()
                    }
                    return@collect
                }

                if (data.isExecuting) {
                    debugLog("ActionExecutor is executing. Pausing scan.")
                    stopScanningTemporarily()
                    return@collect
                }

                val rawPage = data.rawPage
                if (rawPage != null && checkForPredictorUseCase(rawPage)) {
                    val isWaitingData = isWaitingForPredictions(
                        isLoading = data.isLoading,
                        predictions = data.predictions,
                        pageId = rawPage.id
                    )
                    
                    // Even if data arrived, wait if the resolved page still has placeholders
                    val isWaitingResolution = data.resolvedPage != null && checkForPredictorUseCase(data.resolvedPage)
                    
                    if (lastCuePageId != rawPage.id) {
                        lastCuePageId = rawPage.id
                    }

                    if (isWaitingData || isWaitingResolution) {
                        stopScanningTemporarily()
                        return@collect
                    }
                }

                if (data.resolvedPage != null) {
                    resumeScanningIfEnabled()
                }
            }
        }
    }

    private fun handleCycleCompleted() {
        if (!scanCycleLimitEnabled) return
        
        _currentCycleCount.value++
        if (_currentCycleCount.value >= scanCycleLimit) {
            debugLog("Cycle limit ($scanCycleLimit) reached. Stopping scan.")
            _isStoppedDueToLimit.value = true
            stopScanning()
        }
    }

    fun stopScanningTemporarily() {
        scannerEngine.pauseScanning()
    }

    fun stopScanning() {
        scannerEngine.stopScanning()
    }

    fun restartScanning() {
        _currentCycleCount.value = 0
        _isStoppedDueToLimit.value = false
        resumeScanningIfEnabled()
    }

    fun resumeScanningIfEnabled() {
        if (!scanningSettings.autoStartScanning) return
        if (_isStoppedDueToLimit.value) return
        if (_isPausedManually.value) return
        if (actionProvider.isExecuting.value) return
        if (callActionProxy.isInCall.value) return
        
        val page = resolvedPage?.value ?: return
        
        // Don't start if we are waiting for predictions
        val currentP = currentPage?.value
        if (currentP != null && checkForPredictorUseCase(currentP)) {
            val isWaitingData = isWaitingForPredictions(
                isLoading = isSmartPredictionLoading?.value ?: false,
                predictions = smartPredictions?.value,
                pageId = currentP.id
            )
            val isWaitingResolution = checkForPredictorUseCase(page)
            
            if (isWaitingData || isWaitingResolution) return
        }

        val startIndex = if (scanningSettings.resumeScanningFromStart) {
            0
        } else {
            scannerEngine.focusedButtonIndex.value ?: scannerEngine.focusedRowIndex.value ?: 0
        }

        val pattern = page.scanPattern?.takeIf { it != "default" } ?: scanningSettings.defaultScanPattern
        val normalizedPattern = if (pattern == "row_column") "row_by_row" else pattern

        scannerEngine.startScanning(
            buttonConfigs = page.buttonConfigs,
            startIndex = startIndex, 
            pattern = normalizedPattern,
            rows = page.rows,
            columns = page.columns,
            rowNames = page.rowNames,
            pageId = page.id
        )
    }

    private fun isWaitingForPredictions(isLoading: Boolean, predictions: List<String>?, pageId: String): Boolean {
        return lastCuePageId != pageId || isLoading || predictions == null
    }

    fun setFocusedIndex(index: Int?) {
        scannerEngine.setFocusedIndex(index)
    }

    fun setFocusedRowIndex(index: Int?) {
        scannerEngine.setFocusedRowIndex(index)
    }

    fun selectCurrentRow() {
        scannerEngine.selectCurrentRow()
    }

    fun clear() {
        observeJob?.cancel()
        scannerEngine.clear()
        _currentCycleCount.value = 0
        _isStoppedDueToLimit.value = false
        lastCuePageId = null
    }

    fun onPageChanged(isSamePage: Boolean = false) {
        if (!isSamePage) {
            lastCuePageId = null
            _isStoppedDueToLimit.value = false
            _currentCycleCount.value = 0
            _isPausedManually.value = false
            stopScanning()
        }
    }

    fun setCycleCount(count: Int) {
        _currentCycleCount.value = count
    }

    fun setScanLimitSettings(enabled: Boolean, limit: Int) {
        this.scanCycleLimitEnabled = enabled
        this.scanCycleLimit = limit
    }

    fun startScanning(startIndex: Int = 0) {
        val page = resolvedPage?.value ?: return
        val pattern = page.scanPattern?.takeIf { it != "default" } ?: scanningSettings.defaultScanPattern
        val normalizedPattern = if (pattern == "row_column") "row_by_row" else pattern

        scannerEngine.startScanning(
            buttonConfigs = page.buttonConfigs,
            startIndex = startIndex,
            pattern = normalizedPattern,
            rows = page.rows,
            columns = page.columns,
            rowNames = page.rowNames,
            pageId = page.id
        )
    }

    override fun togglePause() {
        val newState = !_isPausedManually.value
        _isPausedManually.value = newState
        debugLog("Toggle pause manually. New state: $newState")
        if (newState) {
            stopScanningTemporarily()
        } else {
            resumeScanningIfEnabled()
        }
    }
}

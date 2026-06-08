package com.andreas_kratzer.ghosttalk.core.scanning

import com.andreas_kratzer.ghosttalk.core.actions.CallActionProxy
import com.andreas_kratzer.ghosttalk.core.actions.ScannerActionProvider
import com.andreas_kratzer.ghosttalk.core.actions.ScannerController
import com.andreas_kratzer.ghosttalk.core.di.ApplicationScope
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.settings.ScanningSettings
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
    private val actionProvider: ScannerActionProvider,
    private val callActionProxy: CallActionProxy
) : ScannerController {
    private var currentPage: StateFlow<Page?>? = null
    private var isUserModeActive: StateFlow<Boolean>? = null
    private var resolvedPage: StateFlow<Page?>? = null
    private var isSmartPredictionLoading: StateFlow<Boolean>? = null
    private var smartPredictions: StateFlow<List<String>?>? = null
    private var staticRowPage: StateFlow<Page?>? = null
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

    private val _isPausedForNotification = MutableStateFlow(false)
    override val isPausedForNotification: StateFlow<Boolean> = _isPausedForNotification.asStateFlow()

    override fun pauseForNotification() {
        _isPausedForNotification.value = true
        debugLog("pauseForNotification() called")
        stopScanningTemporarily()
    }

    override fun resumeFromNotification() {
        _isPausedForNotification.value = false
        debugLog("resumeFromNotification() called")
        resumeScanningIfEnabled()
    }
    
    private var scanCycleLimitEnabled = false
    private var scanCycleLimit = 2

    private var lastCuePageId: String? = null

    private fun debugLog(message: String) {
        println("ScanCoordinator: $message")
    }

    private var lastFocusTimestamp = -1L
    private var lastFocusedIndex: Int? = null

    // For reaction-time and late click heuristic tracking
    private var _previousFocusedButtonId: String? = null

    private var _lastFocusChangeTime: Long = -1L

    fun getPreviousFocusedButton(): String? = _previousFocusedButtonId
    fun getTimeSinceLastFocusChangeMs(): Long {
        if (_lastFocusChangeTime == -1L) return Long.MAX_VALUE
        return System.currentTimeMillis() - _lastFocusChangeTime
    }

    fun getLastFocusDuration(index: Int): Long? {
        val focusTime = lastFocusTimestamp
        if (focusTime != -1L && lastFocusedIndex == index) {
            return System.currentTimeMillis() - focusTime
        }
        return null
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
        scope.launch {
            focusedButtonIndex.collect { index ->
                if (index != null) {
                    val prevId = lastFocusedIndex?.let { prevIdx ->
                        resolvedPage?.value?.buttonConfigs?.getOrNull(prevIdx)?.id
                    }
                    if (prevId != null) {
                        _previousFocusedButtonId = prevId
                    }
                    lastFocusTimestamp = System.currentTimeMillis()
                    _lastFocusChangeTime = lastFocusTimestamp
                    lastFocusedIndex = index
                } else {
                    lastFocusTimestamp = -1L
                    lastFocusedIndex = null
                }
            }
        }
    }


    fun init(
        currentPage: StateFlow<Page?>,
        isUserModeActive: StateFlow<Boolean>,
        resolvedPage: StateFlow<Page?>,
        isSmartPredictionLoading: StateFlow<Boolean>,
        smartPredictions: StateFlow<List<String>?>,
        staticRowPage: StateFlow<Page?> = MutableStateFlow(null)
    ) {
        this.currentPage = currentPage
        this.isUserModeActive = isUserModeActive
        this.resolvedPage = resolvedPage
        this.isSmartPredictionLoading = isSmartPredictionLoading
        this.smartPredictions = smartPredictions
        this.staticRowPage = staticRowPage

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
                isPausedForNotification,
                callActionProxy.isInCall,
                this@ScanCoordinator.staticRowPage ?: MutableStateFlow(null)
            ) { array ->
                Data(
                    isExecuting = array[1] as Boolean,
                    resolvedPage = array[3] as? Page,
                    rawPage = array[2] as? Page,
                    isActive = array[0] as Boolean,
                    isLoading = array[4] as Boolean,
                    predictions = (array[5] as? List<*>)?.filterIsInstance<String>(),
                    isInCall = array[8] as Boolean
                )
            }.collect { data ->
                val pausedForNotif = _isPausedForNotification.value
                if (!data.isActive || _isPausedManually.value || data.isInCall || pausedForNotif) {
                    if (!data.isActive) {
                        debugLog("User mode deactivated. Stopping scan.")
                        stopScanning()
                        _isStoppedDueToLimit.value = false
                        _currentCycleCount.value = 0
                        _isPausedManually.value = false
                    } else if (data.isInCall) {
                        debugLog("In a call. Stopping scan.")
                        stopScanningTemporarily()
                    } else if (pausedForNotif) {
                        debugLog("Scanning is paused for notification auto-read.")
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
                if (rawPage != null && hasUnresolvedDynamicButtons(rawPage)) {
                    val hasPredictions = rawPage.buttonConfigs.any { it != null && it.isActive && it.buttonAction is com.andreas_kratzer.ghosttalk.core.model.SmartPredictionButtonAction }
                    val isWaitingData = if (hasPredictions) {
                        isWaitingForPredictions(
                            isLoading = data.isLoading,
                            predictions = data.predictions,
                            pageId = rawPage.id
                        )
                    } else {
                        false
                    }
                    
                    // Even if data arrived, wait if the resolved page still has placeholders
                    val isWaitingResolution = hasUnresolvedDynamicButtons(data.resolvedPage)
                    
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
        if (_isPausedForNotification.value) return
        if (actionProvider.isExecuting.value) return
        if (callActionProxy.isInCall.value) return
        
        val page = resolvedPage?.value ?: return
        
        // Don't start if we are waiting for predictions or resolution
        val currentP = currentPage?.value
        if (currentP != null && hasUnresolvedDynamicButtons(currentP)) {
            val hasPredictions = currentP.buttonConfigs.any { it != null && it.isActive && it.buttonAction is com.andreas_kratzer.ghosttalk.core.model.SmartPredictionButtonAction }
            val isWaitingData = if (hasPredictions) {
                isWaitingForPredictions(
                    isLoading = isSmartPredictionLoading?.value ?: false,
                    predictions = smartPredictions?.value,
                    pageId = currentP.id
                )
            } else {
                false
            }
            val isWaitingResolution = hasUnresolvedDynamicButtons(page)
            
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
            pageId = page.id,
            staticRowPage = staticRowPage?.value,
            staticRowPattern = staticRowPage?.value?.let {
                val pat = it.scanPattern?.takeIf { p -> p != "default" } ?: scanningSettings.defaultScanPattern
                if (pat == "row_column") "row_by_row" else pat
            } ?: "linear"
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
            pageId = page.id,
            staticRowPage = staticRowPage?.value,
            staticRowPattern = staticRowPage?.value?.let {
                val pat = it.scanPattern?.takeIf { p -> p != "default" } ?: scanningSettings.defaultScanPattern
                if (pat == "row_column") "row_by_row" else pat
            } ?: "linear"
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

    private fun hasUnresolvedDynamicButtons(page: Page?): Boolean {
        if (page == null) return false
        return page.buttonConfigs.any { config ->
            if (config == null || !config.isActive) return@any false
            val action = config.buttonAction
            action is com.andreas_kratzer.ghosttalk.core.model.FrequentActionButtonAction ||
            action is com.andreas_kratzer.ghosttalk.core.model.PreviousActionButtonAction ||
            action is com.andreas_kratzer.ghosttalk.core.model.SmartPredictionButtonAction
        }
    }
}

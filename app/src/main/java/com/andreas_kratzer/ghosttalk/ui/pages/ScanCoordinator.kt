package com.andreas_kratzer.ghosttalk.ui.pages

import android.util.Log
import com.andreas_kratzer.ghosttalk.core.actions.ActionExecutor
import com.andreas_kratzer.ghosttalk.core.scanning.ScannerEngine
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.domain.settings.CheckForPredictorUseCase
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Coordinates scanning lifecycle: start, stop, pause, resume.
 */
class ScanCoordinator(
    private val scope: CoroutineScope,
    private val scannerEngine: ScannerEngine,
    private val settingsRepository: SettingsRepository,
    private val actionExecutor: ActionExecutor,
    private val currentPage: StateFlow<Page?>,
    private val isUserModeActive: StateFlow<Boolean>,
    private val resolvedPage: StateFlow<Page?>,
    private val isSmartPredictionLoading: StateFlow<Boolean>,
    private val checkForPredictorUseCase: CheckForPredictorUseCase,
    private val ttsHelper: TextToSpeechHelper,
    private val smartPredictions: StateFlow<List<String>?>
) {
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

    private var lastCuePageId: String? = null
    fun init() {
        val geminiStatusFlow = combine(isSmartPredictionLoading, smartPredictions) { loading, predictions -> 
            loading to predictions 
        }

        // Scanning trigger: observe ActionExecutor, resolved page, raw page, user mode state, and Gemini status
        scope.launch {
            combine(
                actionExecutor.isExecuting,
                resolvedPage,
                currentPage,
                isUserModeActive,
                geminiStatusFlow
            ) { isExecuting, resPage, rawPage, isActive, (isLoading, predictions) -> 
                Data(isExecuting, resPage, rawPage, isActive, isLoading, predictions) 
            }.collect { data ->
                if (!data.isActive) {
                    Log.d("ScanCoordinator", "User mode inactive, ignoring state change.")
                    return@collect
                }
                
                val rawPage = data.rawPage ?: return@collect
                val hasPredictor = checkForPredictorUseCase(rawPage)
                
                if (hasPredictor) {
                    val isWaiting = isWaitingForPredictions(
                        isLoading = data.isLoading,
                        predictions = data.predictions,
                        rawPage = data.rawPage,
                        resPage = data.resolvedPage
                    )
                    
                    if (isWaiting) {
                        if (rawPage.id != lastCuePageId && settingsRepository.isSmartPredictionEnabled) {
                            Log.d("ScanCoordinator", "Page ${rawPage.name} has predictor and is waiting. Speaking cue.")
                            ttsHelper.speak("Befrage Gemini Nano")
                            lastCuePageId = rawPage.id
                        }
                        Log.d("ScanCoordinator", "Waiting for Gemini prediction. Pausing scan.")
                        stopScanningTemporarily()
                        return@collect
                    }
                }

                if (data.isExecuting) {
                    Log.d("ScanCoordinator", "ActionExecutor is executing. Pausing scan.")
                    stopScanningTemporarily()
                } else if (data.resolvedPage != null) {
                    Log.d("ScanCoordinator", "Ready to resume on page ${data.resolvedPage.id}")
                    resumeScanningIfEnabled()
                }
            }
        }

        // React to scan delay changes
        scope.launch {
            settingsRepository.scanDelayFlow.collect { delay -> setScanDelay(delay) }
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
        if (actionExecutor.isExecuting.value) {
            Log.d("ScanCoordinator", "resumeScanningIfEnabled: ActionExecutor busy, skipping.")
            return
        }

        if (isWaitingForPredictions(
            isLoading = isSmartPredictionLoading.value,
            predictions = smartPredictions.value,
            rawPage = currentPage.value,
            resPage = resolvedPage.value
        )) {
            Log.d("ScanCoordinator", "resumeScanningIfEnabled: Waiting for predictions/resolution, skipping.")
            return
        }

        if (settingsRepository.autoStartScanning) {
            val startIndex = if (settingsRepository.resumeScanningFromStart) {
                0
            } else {
                focusedButtonIndex.value ?: 0
            }
            startScanning(startIndex)
        }
    }

    fun startScanning(startIndex: Int = 0) {
        val page = resolvedPage.value ?: return
        scannerEngine.startScanning(
            buttonConfigs = page.buttonConfigs,
            startIndex = startIndex,
            pattern = page.scanPattern ?: settingsRepository.defaultScanPattern,
            rows = page.rows,
            columns = page.columns,
            rowNames = page.rowNames,
            pageId = page.id
        )
    }

    fun setScanDelay(delayMillis: Long) {
        scannerEngine.scanDelayMillis = delayMillis
        if (settingsRepository.autoStartScanning) {
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
        scannerEngine.selectCurrentRow()
    }

    fun setFocusedIndex(index: Int?) {
        scannerEngine.setFocusedIndex(index)
    }

    fun onPageChanged(isSamePage: Boolean) {
        if (isSamePage) {
            scannerEngine.pauseScanning()
        } else {
            scannerEngine.stopScanning()
        }
        lastCuePageId = null // Ensure cue is spoken again on explicit re-navigation
    }

    fun clear() {
        scannerEngine.clear()
    }
}

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
import kotlinx.coroutines.flow.StateFlow
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

        val geminiStatusFlow = combine(isSmartPredictionLoading, smartPredictions) { loading, predictions -> 
            loading to predictions 
        }

        // Scanning trigger: observe ActionProvider, resolved page, raw page, user mode state, and Gemini status
        scope.launch {
            combine(
                actionProvider.isExecuting,
                resolvedPage,
                currentPage,
                isUserModeActive,
                geminiStatusFlow
            ) { isExecuting, resPage, rawPage, isActive, (isLoading, predictions) -> 
                Data(isExecuting, resPage, rawPage, isActive, isLoading, predictions) 
            }.collect { data ->
                if (!data.isActive) {
                    Log.d("ScanCoordinator", "User mode inactive, stopping scan.")
                    stopScanning()
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
            scanningSettings.scanDelayFlow.collect { delay -> setScanDelay(delay) }
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
            val startIndex = if (scanningSettings.resumeScanningFromStart) {
                0
            } else {
                focusedButtonIndex.value ?: 0
            }
            startScanning(startIndex)
        }
    }

    fun startScanning(startIndex: Int = 0) {
        if (isUserModeActive?.value != true) {
            Log.d("ScanCoordinator", "startScanning: User mode inactive, skipping.")
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

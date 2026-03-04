package com.andreas_kratzer.ghosttalk.core.scanning

import android.util.Log
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import com.andreas_kratzer.ghosttalk.domain.FeatureGuard
import com.andreas_kratzer.ghosttalk.data.SettingsRepository

import com.andreas_kratzer.ghosttalk.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScannerEngine @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val featureGuard: FeatureGuard,
    private val feedbackProvider: ScannerFeedbackProvider
) {
    private val _focusedButtonIndex = MutableStateFlow<Int?>(null)
    val focusedButtonIndex: StateFlow<Int?> = _focusedButtonIndex.asStateFlow()

    private val _focusedRowIndex = MutableStateFlow<Int?>(null)
    val focusedRowIndex: StateFlow<Int?> = _focusedRowIndex.asStateFlow()

    private var currentButtonConfigs: List<ButtonConfig?> = emptyList()
    private var currentColumns: Int = 4
    private var currentRowNames: List<String> = emptyList()

    private var currentPattern: String = "linear"
    private var currentStartIndex: Int = 0
    private var currentPageId: String? = null

    private var scanJob: Job? = null
    var scanDelayMillis: Long = 1000L

    private val linearStrategy = LinearScanStrategy()
    private val rowByRowStrategy = RowByRowScanStrategy()

    fun startScanning(
        buttonConfigs: List<ButtonConfig?>, 
        startIndex: Int = 0, 
        pattern: String = "linear", 
        columns: Int = 4, 
        rowNames: List<String> = emptyList(),
        pageId: String? = null
    ) {
        // Check if we are already scanning with the same parameters
        if (scanJob?.isActive == true &&
            currentPageId == pageId &&
            currentButtonConfigs == buttonConfigs &&
            currentStartIndex == startIndex &&
            currentPattern == pattern &&
            currentColumns == columns &&
            currentRowNames == rowNames
        ) {
            Log.d("ScannerEngine", "startScanning: Idempotency triggered. Already scanning Page $pageId with same config. Skipping restart.")
            return
        }

        if (scanJob?.isActive == true) {
            Log.d("ScannerEngine", "startScanning: Cancelling existing scan job for Page $currentPageId to start $pageId.")
        } else {
            Log.d("ScannerEngine", "startScanning: Starting new scan job für $pageId.")
        }

        scanJob?.cancel()
        scanJob = null
        currentButtonConfigs = buttonConfigs
        currentColumns = columns
        currentRowNames = rowNames
        currentPattern = pattern
        currentStartIndex = startIndex
        currentPageId = pageId

        scanJob = scope.launch {
            if (pattern == "row_by_row") {
                rowByRowStrategy.executeScan(
                    buttonConfigs = buttonConfigs,
                    columns = columns,
                    rowNames = rowNames,
                    startIndex = startIndex,
                    focusedButtonIndex = _focusedButtonIndex,
                    focusedRowIndex = _focusedRowIndex,
                    onSpeakCue = { handleSpeakCue(it) },
                    delayMillis = scanDelayMillis,
                    featureGuard = featureGuard
                )
            } else if (pattern == "linear") {
                linearStrategy.executeScan(
                    buttonConfigs = buttonConfigs,
                    columns = columns,
                    rowNames = rowNames,
                    startIndex = startIndex,
                    focusedButtonIndex = _focusedButtonIndex,
                    focusedRowIndex = _focusedRowIndex,
                    onSpeakCue = { handleSpeakCue(it) },
                    delayMillis = scanDelayMillis,
                    featureGuard = featureGuard
                )
            }
        }
    }

    private suspend fun handleSpeakCue(text: String) {
        feedbackProvider.speakCue(text)
    }

    fun selectCurrentRow() {
        val currentRow = _focusedRowIndex.value ?: return
        scanJob?.cancel()
        
        scanJob = scope.launch {
            rowByRowStrategy.executeButtonScanInRow(
                buttonConfigs = currentButtonConfigs,
                columns = currentColumns,
                rowIndex = currentRow,
                focusedButtonIndex = _focusedButtonIndex,
                onSpeakCue = { handleSpeakCue(it) },
                delayMillis = scanDelayMillis,
                featureGuard = featureGuard
            )
        }
    }

    fun pauseScanning() {
        Log.d("ScannerEngine", "pauseScanning: Pausing scan job.")
        scanJob?.cancel()
        scanJob = null
    }

    fun stopScanning() {
        Log.d("ScannerEngine", "stopScanning: Stopping scan job.")
        scanJob?.cancel()
        scanJob = null
        // We do NOT clear currentButtonConfigs here, so that resumeScanning (idempotency)
        // works correctly if called with the same parameters.
        _focusedButtonIndex.value = null
        _focusedRowIndex.value = null
    }

    fun setFocusedIndex(index: Int?) {
        _focusedButtonIndex.value = index
    }
    
    fun clear() {
        scanJob?.cancel()
        _focusedButtonIndex.value = null
        _focusedRowIndex.value = null
    }
}

package com.andreas_kratzer.ghosttalk.core.scanning

import com.andreas_kratzer.ghosttalk.core.di.ApplicationScope
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScannerEngine @Inject constructor(
    @param:ApplicationScope private val scope: CoroutineScope,
    private val featureGuard: FeatureGuardProxy,
    private val feedbackProvider: ScannerFeedbackProvider
) {
    private val _focusedButtonIndex = MutableStateFlow<Int?>(null)
    val focusedButtonIndex: StateFlow<Int?> = _focusedButtonIndex.asStateFlow()

    private val _focusedRowIndex = MutableStateFlow<Int?>(null)
    val focusedRowIndex: StateFlow<Int?> = _focusedRowIndex.asStateFlow()

    private var currentButtonConfigs: List<ButtonConfig?> = emptyList()
    private var currentRows: Int = 4
    private var currentColumns: Int = 4
    private var currentRowNames: List<String> = emptyList()

    private var currentPattern: String = "linear"
    private var currentStartIndex: Int = 0
    private var currentPageId: String? = null

    private var scanJob: Job? = null
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    var scanDelayMillis: Long = 1000L

    private val linearStrategy = LinearScanStrategy()
    private val rowByRowStrategy = RowByRowScanStrategy()

    fun startScanning(
        buttonConfigs: List<ButtonConfig?>, 
        startIndex: Int = 0, 
        pattern: String = "linear", 
        rows: Int = 4,
        columns: Int = 4, 
        rowNames: List<String> = emptyList(),
        pageId: String? = null
    ) {
        if (scanJob?.isActive == true &&
            currentPageId == pageId &&
            currentButtonConfigs == buttonConfigs &&
            (currentStartIndex == startIndex || _focusedButtonIndex.value == startIndex || _focusedRowIndex.value == startIndex) &&
            currentPattern == pattern &&
            currentRows == rows &&
            currentColumns == columns &&
            currentRowNames == rowNames
        ) {
            return
        }

        scanJob?.cancel()
        scanJob = null
        currentButtonConfigs = buttonConfigs
        currentRows = rows
        currentColumns = columns
        currentRowNames = rowNames
        currentPattern = pattern
        currentStartIndex = startIndex
        currentPageId = pageId

        _isScanning.value = true
        scanJob = scope.launch {
            try {
                if (pattern == "row_by_row") {
                    rowByRowStrategy.executeScan(
                        buttonConfigs = buttonConfigs,
                        rows = rows,
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
                        rows = rows,
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
            } finally {
                _isScanning.value = false
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
                rows = currentRows,
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
        scanJob?.cancel()
        scanJob = null
        _isScanning.value = false
    }

    fun stopScanning() {
        scanJob?.cancel()
        scanJob = null
        _isScanning.value = false
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

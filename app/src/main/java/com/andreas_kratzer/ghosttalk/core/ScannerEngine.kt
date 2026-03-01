package com.andreas_kratzer.ghosttalk.core

import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.andreas_kratzer.ghosttalk.core.util.AppLogger

class ScannerEngine(
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    var ttsHelper: TextToSpeechHelper? = null,
    private val logger: Logger = AppLogger
) {
    private val _focusedButtonIndex = MutableStateFlow<Int?>(null)
    val focusedButtonIndex: StateFlow<Int?> = _focusedButtonIndex.asStateFlow()

    private val _focusedRowIndex = MutableStateFlow<Int?>(null)
    val focusedRowIndex: StateFlow<Int?> = _focusedRowIndex.asStateFlow()

    private var currentButtonConfigs: List<ButtonConfig?> = emptyList()
    private var currentColumns: Int = 4
    private var currentRowNames: List<String> = emptyList()

    private var scanJob: Job? = null
    var scanDelayMillis: Long = 1000L
    var isAutoScanningEnabled: Boolean = false

    private val linearStrategy = LinearScanStrategy()
    private val rowByRowStrategy = RowByRowScanStrategy()

    fun startScanning(
        buttonConfigs: List<ButtonConfig?>, 
        startIndex: Int = 0, 
        pattern: String = "linear", 
        columns: Int = 4, 
        rowNames: List<String> = emptyList()
    ) {
        scanJob?.cancel()
        currentButtonConfigs = buttonConfigs
        currentColumns = columns
        currentRowNames = rowNames
        
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
                    delayMillis = scanDelayMillis
                )
            } else {
                linearStrategy.executeScan(
                    buttonConfigs = buttonConfigs,
                    columns = columns,
                    rowNames = rowNames,
                    startIndex = startIndex,
                    focusedButtonIndex = _focusedButtonIndex,
                    focusedRowIndex = _focusedRowIndex,
                    onSpeakCue = { handleSpeakCue(it) },
                    delayMillis = scanDelayMillis
                )
            }
        }
    }

    private suspend fun handleSpeakCue(text: String) {
        var retries = 0
        while (ttsHelper?.isReady != true && retries < 20) {
            delay(100)
            retries++
        }

        if (ttsHelper?.isReady == true) {
            ttsHelper?.speakRouted(
                text = text, 
                deviceAddress = settingsRepository.cuesAudioDeviceAddress,
                queueMode = android.speech.tts.TextToSpeech.QUEUE_FLUSH
            )
        }
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
                focusedRowIndex = _focusedRowIndex,
                onSpeakCue = { handleSpeakCue(it) },
                delayMillis = scanDelayMillis
            )
        }
    }

    fun pauseScanning() {
        scanJob?.cancel()
    }

    fun stopScanning() {
        scanJob?.cancel()
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

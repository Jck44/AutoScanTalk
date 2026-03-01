package com.example.gostalk.core

import com.example.gostalk.model.AuditoryCue
import com.example.gostalk.model.ButtonConfig
import com.example.gostalk.tts.TextToSpeechHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import com.example.gostalk.data.SettingsRepository

class ScannerEngine(
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    var ttsHelper: TextToSpeechHelper? = null
) {
    private val _focusedButtonIndex = MutableStateFlow<Int?>(null)
    val focusedButtonIndex: StateFlow<Int?> = _focusedButtonIndex.asStateFlow()

    private val _focusedRowIndex = MutableStateFlow<Int?>(null)
    val focusedRowIndex: StateFlow<Int?> = _focusedRowIndex.asStateFlow()

    private var currentButtonConfigs: List<ButtonConfig?> = emptyList()
    private var currentColumns: Int = 4

    private var scanJob: Job? = null
    var scanDelayMillis: Long = 1000L
    var isAutoScanningEnabled: Boolean = false

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
        
        if (pattern == "row_by_row") {
            _focusedButtonIndex.value = null
            startRowScanning(buttonConfigs, columns, rowNames, startIndex)
        } else {
            _focusedRowIndex.value = null
            startButtonScanning(buttonConfigs, startIndex, null) // null implies all rows
        }
    }

    private fun startRowScanning(
        buttonConfigs: List<ButtonConfig?>, 
        columns: Int, 
        rowNames: List<String>, 
        startRow: Int
    ) {
        // Find which rows have at least one active button
        val activeRows = mutableListOf<Int>()
        val totalRows = (buttonConfigs.size + columns - 1) / columns
        
        for (r in 0 until totalRows) {
            val startIdx = r * columns
            val endIdx = minOf(startIdx + columns, buttonConfigs.size)
            var hasActive = false
            for (i in startIdx until endIdx) {
                val btn = buttonConfigs[i]
                if (btn != null && btn.isActive) {
                    hasActive = true
                    break
                }
            }
            if (hasActive) {
                activeRows.add(r)
            }
        }

        if (activeRows.isEmpty()) {
            _focusedRowIndex.value = null
            return
        }

        val startingPosition = activeRows.indexOfFirst { it >= startRow }.coerceAtLeast(0)

        scanJob = scope.launch {
            for (i in startingPosition until activeRows.size) {
                val rowIndex = activeRows[i]
                _focusedRowIndex.value = rowIndex

                var retries = 0
                while (ttsHelper?.isReady != true && retries < 20) {
                    delay(100)
                    retries++
                }

                if (ttsHelper?.isReady == true) {
                    val defaultName = "Zeile ${rowIndex + 1}"
                    val cueText = rowNames.getOrNull(rowIndex)?.takeIf { it.isNotBlank() } ?: defaultName
                    ttsHelper?.speakRouted(
                        text = cueText, 
                        deviceAddress = settingsRepository.cuesAudioDeviceAddress,
                        queueMode = android.speech.tts.TextToSpeech.QUEUE_FLUSH
                    )
                }
                delay(scanDelayMillis)
            }
            _focusedRowIndex.value = null
        }
    }

    private fun startButtonScanning(buttonConfigs: List<ButtonConfig?>, startIndex: Int, limitToRow: Int?) {
        val activeButtonsWithGlobalIndices = buttonConfigs
            .mapIndexedNotNull { index, buttonConfig ->
                if (buttonConfig != null && buttonConfig.isActive) {
                    if (limitToRow == null || index / currentColumns == limitToRow) {
                        Pair(index, buttonConfig)
                    } else null
                } else null
            }

        if (activeButtonsWithGlobalIndices.isEmpty()) {
            _focusedButtonIndex.value = null
            return
        }

        val startingPosition = activeButtonsWithGlobalIndices.indexOfFirst { it.first >= startIndex }
            .coerceAtLeast(0)

        scanJob = scope.launch {
            for (i in startingPosition until activeButtonsWithGlobalIndices.size) {
                val (globalIndex, buttonConfig) = activeButtonsWithGlobalIndices[i]
                _focusedButtonIndex.value = globalIndex
                val cue = buttonConfig.auditoryCue

                var retries = 0
                while (ttsHelper?.isReady != true && retries < 20) {
                    delay(100)
                    retries++
                }

                if (ttsHelper?.isReady == true) {
                    val cueText = (cue as? AuditoryCue.TextToSpeechCue)?.text?.takeIf { it.isNotBlank() } ?: buttonConfig.label
                    ttsHelper?.speakRouted(
                        text = cueText, 
                        deviceAddress = settingsRepository.cuesAudioDeviceAddress,
                        queueMode = android.speech.tts.TextToSpeech.QUEUE_FLUSH
                    )
                }
                delay(scanDelayMillis)
            }
            _focusedButtonIndex.value = null
            _focusedRowIndex.value = null
        }
    }

    fun selectCurrentRow() {
        val currentRow = _focusedRowIndex.value ?: return
        scanJob?.cancel()
        _focusedRowIndex.value = currentRow // Keep the row highlighted visually while iterating buttons inside it? Or set it to null. 
        // Setting it to null is better as focusedButtonIndex will take over. Or keep it so we can draw a box around the row. Let's keep it!
        startButtonScanning(currentButtonConfigs, currentRow * currentColumns, currentRow)
    }

    /**
     * Stoppt das Scannen temporär, ohne den aktuellen Index zu löschen.
     * Nützlich, wenn eine Aktion ausgeführt wird.
     */
    fun pauseScanning() {
        scanJob?.cancel()
    }

    /**
     * Stoppt das Scannen komplett und setzt den Index zurück.
     */
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

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

class ScannerEngine(
    private val scope: CoroutineScope,
    var ttsHelper: TextToSpeechHelper? = null
) {
    private val _focusedButtonIndex = MutableStateFlow<Int?>(null)
    val focusedButtonIndex: StateFlow<Int?> = _focusedButtonIndex.asStateFlow()

    private var scanJob: Job? = null
    var scanDelayMillis: Long = 1000L
    var isAutoScanningEnabled: Boolean = false

    fun startScanning(buttonConfigs: List<ButtonConfig?>) {
        scanJob?.cancel()
        
        val activeButtonsWithGlobalIndices = buttonConfigs
            .mapIndexedNotNull { index, buttonConfig ->
                if (buttonConfig != null) Pair(index, buttonConfig) else null
            }

        if (activeButtonsWithGlobalIndices.isEmpty()) {
            _focusedButtonIndex.value = null
            return
        }

        scanJob = scope.launch {
            for ((globalIndex, buttonConfig) in activeButtonsWithGlobalIndices) {
                _focusedButtonIndex.value = globalIndex
                val cue = buttonConfig.auditoryCue

                // Wait up to ~2 seconds if TTS is not ready yet for the very first item
                var retries = 0
                while (ttsHelper?.isReady != true && retries < 20) {
                    delay(100)
                    retries++
                }

                if (ttsHelper?.isReady == true) {
                    val cueText = (cue as? AuditoryCue.TextToSpeechCue)?.text?.takeIf { it.isNotBlank() } ?: buttonConfig.label
                    ttsHelper?.speak(cueText)
                }
                delay(scanDelayMillis)
            }
            _focusedButtonIndex.value = null
        }
    }

    fun stopScanning() {
        scanJob?.cancel()
        _focusedButtonIndex.value = null
    }

    fun setFocusedIndex(index: Int?) {
        _focusedButtonIndex.value = index
    }
    
    fun clear() {
        scanJob?.cancel()
    }
}

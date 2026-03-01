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

    private var scanJob: Job? = null
    var scanDelayMillis: Long = 1000L
    var isAutoScanningEnabled: Boolean = false

    fun startScanning(buttonConfigs: List<ButtonConfig?>, startIndex: Int = 0) {
        scanJob?.cancel()
        
        val activeButtonsWithGlobalIndices = buttonConfigs
            .mapIndexedNotNull { index, buttonConfig ->
                if (buttonConfig != null && buttonConfig.isActive) Pair(index, buttonConfig) else null
            }

        if (activeButtonsWithGlobalIndices.isEmpty()) {
            _focusedButtonIndex.value = null
            return
        }

        // Finde den Startpunkt in der gefilterten Liste
        val startingPosition = activeButtonsWithGlobalIndices.indexOfFirst { it.first >= startIndex }
            .coerceAtLeast(0)

        scanJob = scope.launch {
            // Schleife ab dem Startpunkt
            for (i in startingPosition until activeButtonsWithGlobalIndices.size) {
                val (globalIndex, buttonConfig) = activeButtonsWithGlobalIndices[i]
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
                    ttsHelper?.speakRouted(
                        text = cueText, 
                        deviceAddress = settingsRepository.cuesAudioDeviceAddress,
                        queueMode = android.speech.tts.TextToSpeech.QUEUE_FLUSH
                    )
                }
                delay(scanDelayMillis)
            }
            // Wenn wir durch sind, fangen wir normalerweise nicht von vorne an, sondern hören auf oder resetten
            _focusedButtonIndex.value = null
        }
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
    }

    fun setFocusedIndex(index: Int?) {
        _focusedButtonIndex.value = index
    }
    
    fun clear() {
        scanJob?.cancel()
    }
}

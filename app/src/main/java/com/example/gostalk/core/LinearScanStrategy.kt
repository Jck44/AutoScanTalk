package com.example.gostalk.core

import com.example.gostalk.model.AuditoryCue
import com.example.gostalk.model.ButtonConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

class LinearScanStrategy : ScanStrategy {
    override suspend fun executeScan(
        buttonConfigs: List<ButtonConfig?>,
        columns: Int,
        rowNames: List<String>,
        startIndex: Int,
        focusedButtonIndex: MutableStateFlow<Int?>,
        focusedRowIndex: MutableStateFlow<Int?>,
        onSpeakCue: suspend (String) -> Unit,
        delayMillis: Long
    ) {
        focusedRowIndex.value = null
        
        val activeButtonsWithGlobalIndices = buttonConfigs
            .mapIndexedNotNull { index, buttonConfig ->
                if (buttonConfig != null && buttonConfig.isActive) {
                    Pair(index, buttonConfig)
                } else null
            }

        if (activeButtonsWithGlobalIndices.isEmpty()) {
            focusedButtonIndex.value = null
            return
        }

        val startingPosition = activeButtonsWithGlobalIndices.indexOfFirst { it.first >= startIndex }
            .coerceAtLeast(0)

        while (true) {
            for (i in startingPosition until activeButtonsWithGlobalIndices.size) {
                val (globalIndex, buttonConfig) = activeButtonsWithGlobalIndices[i]
                focusedButtonIndex.value = globalIndex
                
                val cue = buttonConfig.auditoryCue
                val cueText = (cue as? AuditoryCue.TextToSpeechCue)?.text?.takeIf { it.isNotBlank() } ?: buttonConfig.label
                onSpeakCue(cueText)
                
                delay(delayMillis)
            }
            // After one full pass, we loop from the beginning in the next while(true) iteration
            // The startingPosition is only for the very first pass.
        }
    }
}

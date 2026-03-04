package com.andreas_kratzer.ghosttalk.core

import com.andreas_kratzer.ghosttalk.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import kotlinx.coroutines.delay
import com.andreas_kratzer.ghosttalk.domain.FeatureGuard
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
        delayMillis: Long,
        featureGuard: FeatureGuard
    ) {
        focusedRowIndex.value = null
        
        val activeButtonsWithGlobalIndices = buttonConfigs
            .mapIndexedNotNull { index, buttonConfig ->
                if (buttonConfig != null && buttonConfig.isActive && featureGuard.isButtonVisible(buttonConfig)) {
                    Pair(index, buttonConfig)
                } else null
            }

        if (activeButtonsWithGlobalIndices.isEmpty()) {
            focusedButtonIndex.value = null
            return
        }

        val startingPosition = activeButtonsWithGlobalIndices.indexOfFirst { it.first >= startIndex }
            .coerceAtLeast(0)

        var currentPos = startingPosition
        
        // Initial delay to settle race conditions (e.g. page transition triggers)
        delay(100)
        
        while (true) {
            for (i in currentPos until activeButtonsWithGlobalIndices.size) {
                val (globalIndex, buttonConfig) = activeButtonsWithGlobalIndices[i]
                focusedButtonIndex.value = globalIndex
                
                val cue = buttonConfig.auditoryCue
                val cueText = (cue as? AuditoryCue.TextToSpeechCue)?.text?.takeIf { it.isNotBlank() } ?: buttonConfig.label
                onSpeakCue(cueText)
                
                delay(delayMillis)
            }
            currentPos = 0
        }
    }
}

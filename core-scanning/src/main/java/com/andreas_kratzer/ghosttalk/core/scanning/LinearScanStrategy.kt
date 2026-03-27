package com.andreas_kratzer.ghosttalk.core.scanning

import com.andreas_kratzer.ghosttalk.core.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.util.GridUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class LinearScanStrategy : ScanStrategy {
    override suspend fun executeScan(
        buttonConfigs: List<ButtonConfig?>,
        rows: Int,
        columns: Int,
        rowNames: List<String>,
        startIndex: Int,
        focusedButtonIndex: MutableStateFlow<Int?>,
        focusedRowIndex: MutableStateFlow<Int?>,
        onSpeakCue: suspend (String) -> Unit,
        onPrefetchCue: suspend (String) -> Unit,
        onCycleCompleted: suspend () -> Unit,
        delayMillis: Long,
        featureGuard: FeatureGuardProxy
    ) {
        focusedRowIndex.value = null
        
        val activeButtonsWithGlobalIndices = buttonConfigs
            .mapIndexedNotNull { index, buttonConfig ->
                if (buttonConfig != null && 
                    buttonConfig.isActive && 
                    GridUtils.isVisibleInGrid(index, rows = rows, columns = columns) &&
                    featureGuard.isButtonVisible(buttonConfig)) {
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
        
        // Initial delay to settle race conditions
        delay(100)
        
        while (true) {
            for (i in currentPos until activeButtonsWithGlobalIndices.size) {
                val (globalIndex, buttonConfig) = activeButtonsWithGlobalIndices[i]
                focusedButtonIndex.value = globalIndex
                
                val cue = buttonConfig.auditoryCue
                val cueText = (cue as? AuditoryCue.TextToSpeechCue)?.text?.takeIf { it.isNotBlank() } ?: buttonConfig.label
                
                // Prefetch the next item
                val nextIndex = if (i + 1 < activeButtonsWithGlobalIndices.size) i + 1 else 0
                val (_, nextButtonConfig) = activeButtonsWithGlobalIndices[nextIndex]
                val nextCue = nextButtonConfig.auditoryCue
                val nextCueText = (nextCue as? AuditoryCue.TextToSpeechCue)?.text?.takeIf { it.isNotBlank() } ?: nextButtonConfig.label
                
                CoroutineScope(Dispatchers.IO).launch {
                    onPrefetchCue(nextCueText)
                }

                onSpeakCue(cueText)
                
                delay(delayMillis)
            }
            onCycleCompleted()
            currentPos = 0
        }
    }
}

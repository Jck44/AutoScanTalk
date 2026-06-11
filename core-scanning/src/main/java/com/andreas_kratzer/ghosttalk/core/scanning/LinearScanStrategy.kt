package com.andreas_kratzer.ghosttalk.core.scanning

import com.andreas_kratzer.ghosttalk.core.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.util.GridUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

import javax.inject.Inject

class LinearScanStrategy @Inject constructor() : ScanStrategy {
    override suspend fun executeScan(context: ScanContext) {
        context.focusedRowIndex.value = null
        
        val activeButtonsWithGlobalIndices = context.buttonConfigs
            .mapIndexedNotNull { index, buttonConfig ->
                if (buttonConfig != null && 
                    buttonConfig.isActive && 
                    GridUtils.isVisibleInGrid(index, rows = context.rows, columns = context.columns) &&
                    context.featureGuard.isButtonVisible(buttonConfig)) {
                    Pair(index, buttonConfig)
                } else null
            }

        if (activeButtonsWithGlobalIndices.isEmpty()) {
            context.focusedButtonIndex.value = null
            return
        }

        val startingPosition = activeButtonsWithGlobalIndices.indexOfFirst { it.first >= context.startIndex }
            .coerceAtLeast(0)

        var currentPos = startingPosition
        
        // Initial delay to settle race conditions
        delay(100)
        
        while (true) {
            for (i in currentPos until activeButtonsWithGlobalIndices.size) {
                val (globalIndex, buttonConfig) = activeButtonsWithGlobalIndices[i]
                context.focusedButtonIndex.value = globalIndex
                
                val cue = buttonConfig.auditoryCue
                val cueText = (cue as? AuditoryCue.TextToSpeechCue)?.text?.takeIf { it.isNotBlank() } ?: buttonConfig.label
                
                // Prefetch the next item
                val nextIndex = if (i + 1 < activeButtonsWithGlobalIndices.size) i + 1 else 0
                val (_, nextButtonConfig) = activeButtonsWithGlobalIndices[nextIndex]
                val nextCue = nextButtonConfig.auditoryCue
                val nextCueText = (nextCue as? AuditoryCue.TextToSpeechCue)?.text?.takeIf { it.isNotBlank() } ?: nextButtonConfig.label
                
                context.scope.launch(Dispatchers.IO) {
                    context.onPrefetchCue(nextCueText)
                }

                context.onSpeakCue(cueText)
                
                delay(context.delayMillis)
            }
            context.onCycleCompleted()
            currentPos = 0
        }
    }
}

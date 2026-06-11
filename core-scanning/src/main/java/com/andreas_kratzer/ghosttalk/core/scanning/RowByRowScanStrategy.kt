package com.andreas_kratzer.ghosttalk.core.scanning

import com.andreas_kratzer.ghosttalk.core.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

import javax.inject.Inject

class RowByRowScanStrategy @Inject constructor() : ScanStrategy {
    override suspend fun executeScan(context: ScanContext) {
        context.focusedButtonIndex.value = null
        
        val hasAnyVisibleButtons = context.buttonConfigs.any { config ->
            config != null && config.isActive && context.featureGuard.isButtonVisible(config)
        }
        if (!hasAnyVisibleButtons) {
            context.focusedRowIndex.value = null
            return
        }

        // Initial delay to settle race conditions
        delay(100)

        val startRow = (context.startIndex / context.columns).coerceIn(0, context.rows - 1)
        var currentRow = startRow

        while (true) {
            for (r in currentRow until context.rows) {
                // Check if row has any visible buttons
                val hasVisibleButtons = (0 until context.columns).any { c ->
                    val index = com.andreas_kratzer.ghosttalk.core.util.GridUtils.getGlobalIndex(r, c)
                    val config = context.buttonConfigs.getOrNull(index)
                    config != null && config.isActive && context.featureGuard.isButtonVisible(config)
                }

                if (hasVisibleButtons) {
                    context.focusedRowIndex.value = r
                    val rowName = context.rowNames.getOrNull(r) ?: "Zeile ${r + 1}"
                    
                    // Predict the next row mathematically (first row after `r` that has buttons, or wrap to 0)
                    var nextValidRow = r + 1
                    var foundNext = false
                    while (nextValidRow < context.rows) {
                        val hasBtns = (0 until context.columns).any { c ->
                            val index = com.andreas_kratzer.ghosttalk.core.util.GridUtils.getGlobalIndex(nextValidRow, c)
                            val config = context.buttonConfigs.getOrNull(index)
                            config != null && config.isActive && context.featureGuard.isButtonVisible(config)
                        }
                        if (hasBtns) { foundNext = true; break }
                        nextValidRow++
                     }
                     if (!foundNext) {
                         // Wrapping check
                         nextValidRow = 0
                         while (nextValidRow <= r) {
                             val hasBtns = (0 until context.columns).any { c ->
                                 val index = com.andreas_kratzer.ghosttalk.core.util.GridUtils.getGlobalIndex(nextValidRow, c)
                                 val config = context.buttonConfigs.getOrNull(index)
                                 config != null && config.isActive && context.featureGuard.isButtonVisible(config)
                             }
                             if (hasBtns) { foundNext = true; break }
                             nextValidRow++
                         }
                     }
                     
                     if (foundNext) {
                         val nextRowName = context.rowNames.getOrNull(nextValidRow) ?: "Zeile ${nextValidRow + 1}"
                         context.scope.launch(Dispatchers.IO) {
                             context.onPrefetchCue(nextRowName)
                         }
                     }

                     context.onSpeakCue(rowName)
                     delay(context.delayMillis)
                }
            }
            context.onCycleCompleted()
            currentRow = 0
        }
    }

    suspend fun executeButtonScanInRow(
        context: ScanContext,
        rowIndex: Int
    ) {
        val rowButtons = (0 until context.columns).mapNotNull { c ->
            val globalIndex = com.andreas_kratzer.ghosttalk.core.util.GridUtils.getGlobalIndex(rowIndex, c)
            val config = context.buttonConfigs.getOrNull(globalIndex)
            if (config != null && config.isActive && context.featureGuard.isButtonVisible(config)) {
                Pair(globalIndex, config)
            } else null
        }

        if (rowButtons.isEmpty()) return

        // Initial delay to settle race conditions
        delay(100)

        while (true) {
            for (i in rowButtons.indices) {
                val (globalIndex, config) = rowButtons[i]
                context.focusedButtonIndex.value = globalIndex
                val cue = config.auditoryCue
                val cueText = (cue as? AuditoryCue.TextToSpeechCue)?.text?.takeIf { it.isNotBlank() } ?: config.label
                
                val nextIndex = if (i + 1 < rowButtons.size) i + 1 else 0
                val (_, nextConfig) = rowButtons[nextIndex]
                val nextCue = nextConfig.auditoryCue
                val nextCueText = (nextCue as? AuditoryCue.TextToSpeechCue)?.text?.takeIf { it.isNotBlank() } ?: nextConfig.label
                
                context.scope.launch(Dispatchers.IO) {
                    context.onPrefetchCue(nextCueText)
                }
                
                context.onSpeakCue(cueText)
                delay(context.delayMillis)
            }
            context.onCycleCompleted()
        }
    }
}

package com.andreas_kratzer.ghosttalk.core.scanning

import com.andreas_kratzer.ghosttalk.core.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class RowByRowScanStrategy : ScanStrategy {
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
        focusedButtonIndex.value = null
        
        // Initial delay to settle race conditions
        delay(100)

        val startRow = (startIndex / columns).coerceIn(0, rows - 1)
        var currentRow = startRow

        while (true) {
            for (r in currentRow until rows) {
                // Check if row has any visible buttons
                val hasVisibleButtons = (0 until columns).any { c ->
                    val index = com.andreas_kratzer.ghosttalk.core.util.GridUtils.getGlobalIndex(r, c)
                    val config = buttonConfigs.getOrNull(index)
                    config != null && config.isActive && featureGuard.isButtonVisible(config)
                }

                if (hasVisibleButtons) {
                    focusedRowIndex.value = r
                    val rowName = rowNames.getOrNull(r) ?: "Zeile ${r + 1}"
                    
                    // Predict the next row mathematically (first row after `r` that has buttons, or wrap to 0)
                    var nextValidRow = r + 1
                    var foundNext = false
                    while (nextValidRow < rows) {
                        val hasBtns = (0 until columns).any { c ->
                            val index = com.andreas_kratzer.ghosttalk.core.util.GridUtils.getGlobalIndex(nextValidRow, c)
                            val config = buttonConfigs.getOrNull(index)
                            config != null && config.isActive && featureGuard.isButtonVisible(config)
                        }
                        if (hasBtns) { foundNext = true; break }
                        nextValidRow++
                    }
                    if (!foundNext) {
                        // Wrapping check
                        nextValidRow = 0
                        while (nextValidRow <= r) {
                            val hasBtns = (0 until columns).any { c ->
                                val index = com.andreas_kratzer.ghosttalk.core.util.GridUtils.getGlobalIndex(nextValidRow, c)
                                val config = buttonConfigs.getOrNull(index)
                                config != null && config.isActive && featureGuard.isButtonVisible(config)
                            }
                            if (hasBtns) { foundNext = true; break }
                            nextValidRow++
                        }
                    }
                    
                    if (foundNext) {
                        val nextRowName = rowNames.getOrNull(nextValidRow) ?: "Zeile ${nextValidRow + 1}"
                        CoroutineScope(Dispatchers.IO).launch {
                            onPrefetchCue(nextRowName)
                        }
                    }

                    onSpeakCue(rowName)
                    delay(delayMillis)
                }
            }
            onCycleCompleted()
            currentRow = 0
        }
    }

    suspend fun executeButtonScanInRow(
        buttonConfigs: List<ButtonConfig?>,
        rows: Int,
        columns: Int,
        rowIndex: Int,
        focusedButtonIndex: MutableStateFlow<Int?>,
        onSpeakCue: suspend (String) -> Unit,
        onPrefetchCue: suspend (String) -> Unit,
        onCycleCompleted: suspend () -> Unit,
        delayMillis: Long,
        featureGuard: FeatureGuardProxy
    ) {
        val rowButtons = (0 until columns).mapNotNull { c ->
            val globalIndex = com.andreas_kratzer.ghosttalk.core.util.GridUtils.getGlobalIndex(rowIndex, c)
            val config = buttonConfigs.getOrNull(globalIndex)
            if (config != null && config.isActive && featureGuard.isButtonVisible(config)) {
                Pair(globalIndex, config)
            } else null
        }

        if (rowButtons.isEmpty()) return

        // Initial delay to settle race conditions
        delay(100)

        while (true) {
            for (i in rowButtons.indices) {
                val (globalIndex, config) = rowButtons[i]
                focusedButtonIndex.value = globalIndex
                val cue = config.auditoryCue
                val cueText = (cue as? AuditoryCue.TextToSpeechCue)?.text?.takeIf { it.isNotBlank() } ?: config.label
                
                val nextIndex = if (i + 1 < rowButtons.size) i + 1 else 0
                val (_, nextConfig) = rowButtons[nextIndex]
                val nextCue = nextConfig.auditoryCue
                val nextCueText = (nextCue as? AuditoryCue.TextToSpeechCue)?.text?.takeIf { it.isNotBlank() } ?: nextConfig.label
                
                CoroutineScope(Dispatchers.IO).launch {
                    onPrefetchCue(nextCueText)
                }
                
                onSpeakCue(cueText)
                delay(delayMillis)
            }
            onCycleCompleted()
        }
    }
}

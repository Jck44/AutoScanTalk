package com.andreas_kratzer.ghosttalk.core

import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.domain.FeatureGuard
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

class RowByRowScanStrategy : ScanStrategy {
    override suspend fun executeScan(
        buttonConfigs: List<ButtonConfig?>,
        columns: Int,
        rowNames: List<String>,
        startIndex: Int, // This is startIndex for ROWS in this strategy
        focusedButtonIndex: MutableStateFlow<Int?>,
        focusedRowIndex: MutableStateFlow<Int?>,
        onSpeakCue: suspend (String) -> Unit,
        delayMillis: Long,
        featureGuard: FeatureGuard
    ) {
        focusedButtonIndex.value = null
        
        val activeRows = mutableListOf<Int>()
        val totalRows = (buttonConfigs.size + columns - 1) / columns
        
        for (r in 0 until totalRows) {
            val startIdx = r * columns
            val endIdx = minOf(startIdx + columns, buttonConfigs.size)
            var hasActive = false
            for (i in startIdx until endIdx) {
                val btn = buttonConfigs[i]
                if (btn != null && btn.isActive && featureGuard.isButtonVisible(btn)) {
                    hasActive = true
                    break
                }
            }
            if (hasActive) {
                activeRows.add(r)
            }
        }

        if (activeRows.isEmpty()) {
            focusedRowIndex.value = null
            return
        }

        val startingPosition = activeRows.indexOfFirst { it >= startIndex }.coerceAtLeast(0)

        var currentPos = startingPosition
        while (true) {
            for (i in currentPos until activeRows.size) {
                val rowIndex = activeRows[i]
                focusedRowIndex.value = rowIndex

                val defaultName = "Zeile ${rowIndex + 1}"
                val cueText = rowNames.getOrNull(rowIndex)?.takeIf { it.isNotBlank() } ?: defaultName
                onSpeakCue(cueText)
                
                delay(delayMillis)
            }
            currentPos = 0
        }
    }

    /**
     * Helper to start scanning buttons within a specific row.
     * This acts as a secondary strategy or a mode of this strategy.
     */
    suspend fun executeButtonScanInRow(
        buttonConfigs: List<ButtonConfig?>,
        columns: Int,
        rowIndex: Int,
        focusedButtonIndex: MutableStateFlow<Int?>,
        onSpeakCue: suspend (String) -> Unit,
        delayMillis: Long,
        featureGuard: FeatureGuard
    ) {
        // Keep focusedRowIndex as is (to highlight the row)
        val activeButtonsInRow = buttonConfigs
            .mapIndexedNotNull { index, config ->
                if (config != null && config.isActive && index / columns == rowIndex && featureGuard.isButtonVisible(config)) {
                    Pair(index, config)
                } else null
            }

        if (activeButtonsInRow.isEmpty()) {
            focusedButtonIndex.value = null
            return
        }

        while (true) {
            for (i in activeButtonsInRow.indices) {
                val (globalIndex, buttonConfig) = activeButtonsInRow[i]
                focusedButtonIndex.value = globalIndex
                
                val cueText = (buttonConfig.auditoryCue as? com.andreas_kratzer.ghosttalk.model.AuditoryCue.TextToSpeechCue)?.text 
                    ?: buttonConfig.label
                onSpeakCue(cueText)
                
                delay(delayMillis)
            }
        }
    }
}

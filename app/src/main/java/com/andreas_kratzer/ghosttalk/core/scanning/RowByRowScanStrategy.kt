package com.andreas_kratzer.ghosttalk.core.scanning

import com.andreas_kratzer.ghosttalk.domain.settings.FeatureGuard
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.ui.util.GridUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

class RowByRowScanStrategy : ScanStrategy {
    override suspend fun executeScan(
        buttonConfigs: List<ButtonConfig?>,
        rows: Int,
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
        
        for (r in 0 until rows) {
            var hasActive = false
            for (c in 0 until columns) {
                val i = GridUtils.getGlobalIndex(r, c)
                val btn = buttonConfigs.getOrNull(i)
                if (btn != null && 
                    btn.isActive && 
                    featureGuard.isButtonVisible(btn)) {
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
        
        // Initial delay to settle race conditions (e.g. page transition triggers)
        delay(100)
        
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
        rows: Int,
        columns: Int,
        rowIndex: Int,
        focusedButtonIndex: MutableStateFlow<Int?>,
        onSpeakCue: suspend (String) -> Unit,
        delayMillis: Long,
        featureGuard: FeatureGuard
    ) {
        val activeButtonsInRow = buttonConfigs
            .mapIndexedNotNull { index, config ->
                if (config != null && 
                    config.isActive && 
                    index / GridUtils.MAX_GRID_SIZE == rowIndex && 
                    GridUtils.isVisibleInGrid(index, rows = rows, columns = columns) &&
                    featureGuard.isButtonVisible(config)) {
                    Pair(index, config)
                } else null
            }

        if (activeButtonsInRow.isEmpty()) {
            focusedButtonIndex.value = null
            return
        }

        // Initial delay to settle race conditions
        delay(100)
        
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

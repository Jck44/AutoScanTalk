package com.andreas_kratzer.ghosttalk.core.scanning

import com.andreas_kratzer.ghosttalk.core.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import javax.inject.Inject

class RowByRowScanStrategy @Inject constructor() : ScanStrategy {

    sealed interface ScanStep {
        data class Button(val index: Int) : ScanStep
        data class Row(val rowIndex: Int, val name: String) : ScanStep
    }

    override suspend fun executeScan(context: ScanContext) {
        context.focusedButtonIndex.value = null
        
        android.util.Log.d("ScanStrategy", "executeScan: hasStaticRow=${context.hasStaticRow}, pagePattern=${context.pagePattern}, staticRowPattern=${context.staticRowPattern}, startIndex=${context.startIndex}")
        val steps = mutableListOf<ScanStep>()

        // 1. Static Row Steps
        if (context.hasStaticRow) {
            val staticRowActiveButtons = context.buttonConfigs.take(49).mapIndexedNotNull { index, config ->
                if (config != null && 
                    config.isActive && 
                    context.featureGuard.isButtonVisible(config)) {
                    Pair(index, config)
                } else null
            }
            if (staticRowActiveButtons.isNotEmpty()) {
                if (context.staticRowPattern == "row_by_row") {
                    val staticRowName = context.rowNames.getOrNull(0) ?: "Statische Zeile"
                    steps.add(ScanStep.Row(0, staticRowName))
                } else {
                    for ((index, _) in staticRowActiveButtons) {
                        steps.add(ScanStep.Button(index))
                    }
                }
            }
        }

        // 2. Main Page Steps
        val startRowIndexOffset = if (context.hasStaticRow) 1 else 0
        val shiftOffset = if (context.hasStaticRow) 49 else 0
        
        if (context.pagePattern == "row_by_row") {
            for (r in 0 until context.mainRows) {
                val hasBtns = (0 until context.mainColumns).any { c ->
                    val index = shiftOffset + com.andreas_kratzer.ghosttalk.core.util.GridUtils.getGlobalIndex(r, c)
                    val config = context.buttonConfigs.getOrNull(index)
                    config != null && config.isActive && context.featureGuard.isButtonVisible(config)
                }
                if (hasBtns) {
                    val rowName = context.rowNames.getOrNull(r + startRowIndexOffset) ?: "Zeile ${r + 1}"
                    steps.add(ScanStep.Row(rowIndex = r + startRowIndexOffset, name = rowName))
                }
            }
        } else {
            // Main page is linear/button-by-button
            for (index in 0 until (context.mainRows * context.mainColumns)) {
                val globalIdx = shiftOffset + index
                val config = context.buttonConfigs.getOrNull(globalIdx)
                if (config != null && config.isActive && context.featureGuard.isButtonVisible(config)) {
                    steps.add(ScanStep.Button(globalIdx))
                }
            }
        }

        android.util.Log.d("ScanStrategy", "executeScan: generated ${steps.size} steps: $steps")
        if (steps.isEmpty()) {
            context.focusedRowIndex.value = null
            return
        }

        // Initial delay to settle race conditions
        delay(100)

        var currentStepPos = 0
        if (context.startIndex > 0) {
            val found = steps.indexOfFirst { step ->
                when (step) {
                    is ScanStep.Button -> step.index >= context.startIndex
                    is ScanStep.Row -> step.rowIndex >= context.startIndex
                }
            }
            if (found != -1) {
                currentStepPos = found
            }
        }

        while (true) {
            for (i in currentStepPos until steps.size) {
                val step = steps[i]
                
                // Prefetch the next step cue
                val nextIndex = if (i + 1 < steps.size) i + 1 else 0
                val nextCueText = when (val nextStep = steps[nextIndex]) {
                    is ScanStep.Button -> {
                        val config = context.buttonConfigs.getOrNull(nextStep.index)
                        val cue = config?.auditoryCue
                        (cue as? AuditoryCue.TextToSpeechCue)?.text?.takeIf { it.isNotBlank() } ?: config?.label ?: ""
                    }
                    is ScanStep.Row -> {
                        nextStep.name
                    }
                }
                context.scope.launch(Dispatchers.IO) {
                    context.onPrefetchCue(nextCueText)
                }

                // Execute current step
                when (step) {
                    is ScanStep.Button -> {
                        context.focusedRowIndex.value = null
                        context.focusedButtonIndex.value = step.index
                        val config = context.buttonConfigs.getOrNull(step.index)
                        val cue = config?.auditoryCue
                        val cueText = (cue as? AuditoryCue.TextToSpeechCue)?.text?.takeIf { it.isNotBlank() } ?: config?.label ?: ""
                        context.onSpeakCue(cueText)
                    }
                    is ScanStep.Row -> {
                        context.focusedButtonIndex.value = null
                        context.focusedRowIndex.value = step.rowIndex
                        context.onSpeakCue(step.name)
                    }
                }
                
                delay(context.delayMillis)
            }
            context.onCycleCompleted()
            currentStepPos = 0
        }
    }

    suspend fun executeButtonScanInRow(
        context: ScanContext,
        rowIndex: Int
    ) {
        val rowButtons = if (context.hasStaticRow && rowIndex == 0) {
            (0 until 49).mapNotNull { c ->
                val config = context.buttonConfigs.getOrNull(c)
                if (config != null && config.isActive && context.featureGuard.isButtonVisible(config)) {
                    Pair(c, config)
                } else null
            }
        } else {
            (0 until context.mainColumns).mapNotNull { c ->
                val globalIndex = if (context.hasStaticRow) {
                    49 + com.andreas_kratzer.ghosttalk.core.util.GridUtils.getGlobalIndex(rowIndex - 1, c)
                } else {
                    com.andreas_kratzer.ghosttalk.core.util.GridUtils.getGlobalIndex(rowIndex, c)
                }
                val config = context.buttonConfigs.getOrNull(globalIndex)
                if (config != null && config.isActive && context.featureGuard.isButtonVisible(config)) {
                    Pair(globalIndex, config)
                } else null
            }
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

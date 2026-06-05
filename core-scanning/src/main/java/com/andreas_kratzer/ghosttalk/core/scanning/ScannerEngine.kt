package com.andreas_kratzer.ghosttalk.core.scanning

import com.andreas_kratzer.ghosttalk.core.di.ApplicationScope
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.Page
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScannerEngine @Inject constructor(
    @param:ApplicationScope private val scope: CoroutineScope,
    private val featureGuard: FeatureGuardProxy,
    private val feedbackProvider: ScannerFeedbackProvider
) {
    private val _focusedButtonIndex = MutableStateFlow<Int?>(null)
    val focusedButtonIndex: StateFlow<Int?> = _focusedButtonIndex.asStateFlow()

    private val _focusedRowIndex = MutableStateFlow<Int?>(null)
    val focusedRowIndex: StateFlow<Int?> = _focusedRowIndex.asStateFlow()

    private var currentButtonConfigs: List<ButtonConfig?> = emptyList()
    private var currentRows: Int = 4
    private var currentColumns: Int = 4
    private var currentRowNames: List<String> = emptyList()

    private var currentPattern: String = "linear"
    private var currentStartIndex: Int = 0
    private var currentPageId: String? = null

    private var currentStaticRowPage: Page? = null
    private var currentStaticRowPattern: String = "linear"

    private var scanJob: Job? = null
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _onCycleCompleted = MutableSharedFlow<Unit>()
    val onCycleCompleted: SharedFlow<Unit> = _onCycleCompleted.asSharedFlow()
    
    var scanDelayMillis: Long = 1000L

    sealed interface ScanStep {
        data class Button(val index: Int, val config: ButtonConfig) : ScanStep
        data class Row(val rowIndex: Int, val name: String) : ScanStep
    }

    fun startScanning(
        buttonConfigs: List<ButtonConfig?>, 
        startIndex: Int = 0, 
        pattern: String = "linear", 
        rows: Int = 4,
        columns: Int = 4, 
        rowNames: List<String> = emptyList(),
        pageId: String? = null,
        staticRowPage: Page? = null,
        staticRowPattern: String = "linear"
    ) {
        if (scanJob?.isActive == true &&
            currentPageId == pageId &&
            currentButtonConfigs == buttonConfigs &&
            (currentStartIndex == startIndex || _focusedButtonIndex.value == startIndex || _focusedRowIndex.value == startIndex) &&
            currentPattern == pattern &&
            currentRows == rows &&
            currentColumns == columns &&
            currentRowNames == rowNames &&
            currentStaticRowPage == staticRowPage &&
            currentStaticRowPattern == staticRowPattern
        ) {
            return
        }

        scanJob?.cancel()
        scanJob = null
        currentButtonConfigs = buttonConfigs
        currentRows = rows
        currentColumns = columns
        currentRowNames = rowNames
        currentPattern = pattern
        currentStartIndex = startIndex
        currentPageId = pageId
        currentStaticRowPage = staticRowPage
        currentStaticRowPattern = staticRowPattern

        _isScanning.value = true

        val steps = mutableListOf<ScanStep>()

        // 1. Static Row Steps (if enabled)
        if (staticRowPage != null) {
            val staticRowActiveButtons = staticRowPage.buttonConfigs
                .mapIndexedNotNull { index, config ->
                    if (config != null &&
                        config.isActive &&
                        com.andreas_kratzer.ghosttalk.core.util.GridUtils.isVisibleInGrid(index, rows = staticRowPage.rows, columns = staticRowPage.columns) &&
                        featureGuard.isButtonVisible(config)) {
                        Pair(index, config)
                    } else null
                }
            
            if (staticRowActiveButtons.isNotEmpty()) {
                if (staticRowPattern == "row_by_row") {
                    val rowName = staticRowPage.rowNames.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "Statische Zeile"
                    steps.add(ScanStep.Row(rowIndex = 0, name = rowName))
                } else { // "linear"
                    for ((index, config) in staticRowActiveButtons) {
                        steps.add(ScanStep.Button(index = index, config = config))
                    }
                }
            }
        }

        // 2. Main Page Steps
        val mainPageActiveButtons = buttonConfigs
            .mapIndexedNotNull { index, config ->
                if (config != null &&
                    config.isActive &&
                    com.andreas_kratzer.ghosttalk.core.util.GridUtils.isVisibleInGrid(index, rows = rows, columns = columns) &&
                    featureGuard.isButtonVisible(config)) {
                    Pair(index, config)
                } else null
            }
        
        if (mainPageActiveButtons.isNotEmpty()) {
            if (pattern == "row_by_row") {
                val startRowIndexOffset = if (staticRowPage != null) 1 else 0
                for (r in 0 until rows) {
                    val hasBtns = (0 until columns).any { c ->
                        val idx = com.andreas_kratzer.ghosttalk.core.util.GridUtils.getGlobalIndex(r, c)
                        val config = buttonConfigs.getOrNull(idx)
                        config != null && config.isActive && featureGuard.isButtonVisible(config)
                    }
                    if (hasBtns) {
                        val rowName = rowNames.getOrNull(r) ?: "Zeile ${r + 1}"
                        steps.add(ScanStep.Row(rowIndex = r + startRowIndexOffset, name = rowName))
                    }
                }
            } else { // "linear"
                val shiftOffset = if (staticRowPage != null) 49 else 0
                for ((index, config) in mainPageActiveButtons) {
                    steps.add(ScanStep.Button(index = shiftOffset + index, config = config))
                }
            }
        }

        val job = scope.launch {
            try {
                if (steps.isEmpty()) {
                    _focusedButtonIndex.value = null
                    _focusedRowIndex.value = null
                    return@launch
                }

                delay(100)

                var currentStepPos = 0
                if (startIndex > 0) {
                    val found = steps.indexOfFirst { step ->
                        when (step) {
                            is ScanStep.Button -> step.index >= startIndex
                            is ScanStep.Row -> step.rowIndex >= startIndex
                        }
                    }
                    if (found != -1) {
                        currentStepPos = found
                    }
                }

                while (true) {
                    for (i in currentStepPos until steps.size) {
                        val step = steps[i]
                        
                        // Prefetch next step cue
                        val nextStepPos = if (i + 1 < steps.size) i + 1 else 0
                        val nextCueText = when (val nextStep = steps[nextStepPos]) {
                            is ScanStep.Button -> {
                                val nextConfig = nextStep.config
                                val nextCue = nextConfig.auditoryCue
                                (nextCue as? com.andreas_kratzer.ghosttalk.core.model.AuditoryCue.TextToSpeechCue)?.text?.takeIf { it.isNotBlank() } ?: nextConfig.label
                            }
                            is ScanStep.Row -> {
                                nextStep.name
                            }
                        }
                        scope.launch(Dispatchers.IO) {
                            handlePrefetchCue(nextCueText)
                        }

                        // Execute current step focus & speak
                        when (step) {
                            is ScanStep.Button -> {
                                _focusedRowIndex.value = null
                                _focusedButtonIndex.value = step.index
                                val cue = step.config.auditoryCue
                                val cueText = (cue as? com.andreas_kratzer.ghosttalk.core.model.AuditoryCue.TextToSpeechCue)?.text?.takeIf { it.isNotBlank() } ?: step.config.label
                                handleSpeakCue(cueText)
                            }
                            is ScanStep.Row -> {
                                _focusedButtonIndex.value = null
                                _focusedRowIndex.value = step.rowIndex
                                handleSpeakCue(step.name)
                            }
                        }

                        delay(scanDelayMillis)
                    }
                    _onCycleCompleted.emit(Unit)
                    currentStepPos = 0
                }
            } finally {
                if (scanJob === this@launch) {
                    _isScanning.value = false
                }
            }
        }
        scanJob = job
    }

    private suspend fun handleSpeakCue(text: String) {
        feedbackProvider.speakCue(text)
    }

    private suspend fun handlePrefetchCue(text: String) {
        feedbackProvider.prefetchCue(text)
    }

    fun selectCurrentRow() {
        val currentRowIndex = _focusedRowIndex.value ?: return
        val staticRowPage = currentStaticRowPage
        
        val rowButtons = mutableListOf<Pair<Int, ButtonConfig>>()

        if (currentRowIndex == 0 && staticRowPage != null) {
            // Static row buttons (indices < 49)
            staticRowPage.buttonConfigs.forEachIndexed { index, config ->
                if (config != null &&
                    config.isActive &&
                    com.andreas_kratzer.ghosttalk.core.util.GridUtils.isVisibleInGrid(index, rows = staticRowPage.rows, columns = staticRowPage.columns) &&
                    featureGuard.isButtonVisible(config)) {
                    rowButtons.add(Pair(index, config))
                }
            }
        } else {
            // Main page row buttons
            val pageRow = if (staticRowPage != null) currentRowIndex - 1 else currentRowIndex
            val shiftOffset = if (staticRowPage != null) 49 else 0
            if (pageRow in 0 until currentRows) {
                for (c in 0 until currentColumns) {
                    val globalIndex = com.andreas_kratzer.ghosttalk.core.util.GridUtils.getGlobalIndex(pageRow, c)
                    val config = currentButtonConfigs.getOrNull(globalIndex)
                    if (config != null &&
                        config.isActive &&
                        featureGuard.isButtonVisible(config)) {
                        rowButtons.add(Pair(shiftOffset + globalIndex, config))
                    }
                }
            }
        }

        if (rowButtons.isEmpty()) return

        scanJob?.cancel()
        scanJob = null
        
        _isScanning.value = true
        val job = scope.launch {
            try {
                delay(100)
                
                while (true) {
                    for (i in rowButtons.indices) {
                        val (globalIndex, config) = rowButtons[i]
                        _focusedButtonIndex.value = globalIndex

                        // Prefetch next button cue
                        val nextIndex = if (i + 1 < rowButtons.size) i + 1 else 0
                        val (_, nextConfig) = rowButtons[nextIndex]
                        val nextCue = nextConfig.auditoryCue
                        val nextCueText = (nextCue as? com.andreas_kratzer.ghosttalk.core.model.AuditoryCue.TextToSpeechCue)?.text?.takeIf { it.isNotBlank() } ?: nextConfig.label
                        scope.launch(Dispatchers.IO) {
                            handlePrefetchCue(nextCueText)
                        }

                        // Speak current button cue
                        val cue = config.auditoryCue
                        val cueText = (cue as? com.andreas_kratzer.ghosttalk.core.model.AuditoryCue.TextToSpeechCue)?.text?.takeIf { it.isNotBlank() } ?: config.label
                        handleSpeakCue(cueText)
                        
                        delay(scanDelayMillis)
                    }
                    _onCycleCompleted.emit(Unit)
                }
            } finally {
                if (scanJob === this@launch) {
                    _isScanning.value = false
                }
            }
        }
        scanJob = job
    }

    fun pauseScanning() {
        scanJob?.cancel()
        scanJob = null
        _isScanning.value = false
    }

    fun stopScanning() {
        scanJob?.cancel()
        scanJob = null
        _isScanning.value = false
        _focusedButtonIndex.value = null
        _focusedRowIndex.value = null
    }

    fun setFocusedIndex(index: Int?) {
        _focusedButtonIndex.value = index
    }

    fun setFocusedRowIndex(index: Int?) {
        _focusedRowIndex.value = index
    }
    
    fun clear() {
        scanJob?.cancel()
        _focusedButtonIndex.value = null
        _focusedRowIndex.value = null
    }
}

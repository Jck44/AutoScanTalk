package com.andreas_kratzer.ghosttalk.core.scanning

import com.andreas_kratzer.ghosttalk.core.di.ApplicationScope
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.Page
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScannerEngine @Inject constructor(
    @param:ApplicationScope private val scope: CoroutineScope,
    private val featureGuard: FeatureGuardProxy,
    private val feedbackProvider: ScannerFeedbackProvider,
    private val stateManager: ScanStateManager,
    private val scanTimer: ScanTimer,
    private val linearStrategy: LinearScanStrategy,
    private val rowByRowStrategy: RowByRowScanStrategy
) {
    val focusedButtonIndex: StateFlow<Int?> = stateManager.focusedButtonIndex
    val focusedRowIndex: StateFlow<Int?> = stateManager.focusedRowIndex
    val isScanning: StateFlow<Boolean> = stateManager.isScanning

    // Schützt den gesamten veränderlichen Zustand (scanJob + alle current*-Felder).
    // Bewusst ein JVM-Monitor statt eines Coroutine-Mutex, weil alle mutierenden Methoden
    // nicht-suspend sind (sie werden aus nicht-suspend Flow-Collectoren aufgerufen).
    private val lock = Any()

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

    private val _onCycleCompleted = MutableSharedFlow<Unit>()
    val onCycleCompleted: SharedFlow<Unit> = _onCycleCompleted.asSharedFlow()
    
    var scanDelayMillis: Long
        get() = scanTimer.scanDelayMillis
        set(value) {
            scanTimer.scanDelayMillis = value
        }

    private fun getButtonConfigByIndex(index: Int): ButtonConfig? {
        return if (index < 49) {
            currentStaticRowPage?.buttonConfigs?.getOrNull(index)
        } else {
            currentButtonConfigs.getOrNull(index - 49)
        }
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
    ) = synchronized(lock) {
        if (scanJob?.isActive == true &&
            currentPageId == pageId &&
            currentPattern == pattern &&
            currentRows == rows &&
            currentColumns == columns &&
            currentRowNames == rowNames &&
            currentStaticRowPage == staticRowPage &&
            currentStaticRowPattern == staticRowPattern
        ) {
            // Update button configs in place without resetting the scan job
            currentButtonConfigs = buttonConfigs
            return@synchronized
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

        stateManager.setScanning(true)

        // Delegate scanning to the selected strategy
        val job = scope.launch {
            try {
                // Build a combined list representing the static row (first, if present) and main buttons
                val combinedButtonConfigs = mutableListOf<ButtonConfig?>()
                var staticRowOffset = 0
                if (staticRowPage != null) {
                    // Prepend static row buttons up to 49
                    combinedButtonConfigs.addAll(staticRowPage.buttonConfigs)
                    while (combinedButtonConfigs.size < 49) {
                        combinedButtonConfigs.add(null)
                    }
                    staticRowOffset = 49
                }
                combinedButtonConfigs.addAll(buttonConfigs)

                // The virtual rows and columns for row-by-row scanning
                val totalRows = (if (staticRowPage != null) 1 else 0) + rows
                val totalCols = if (staticRowPage != null) maxOf(staticRowPage.columns, columns) else columns

                // Prepare row names (first row is static row name if present)
                val combinedRowNames = mutableListOf<String>()
                if (staticRowPage != null) {
                    val staticRowName = staticRowPage.rowNames.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "Statische Zeile"
                    combinedRowNames.add(staticRowName)
                }
                combinedRowNames.addAll(rowNames)

                // Select strategy
                val strategy = if (pattern == "row_by_row" || (staticRowPage != null && staticRowPattern == "row_by_row")) {
                    rowByRowStrategy
                } else {
                    linearStrategy
                }

                val context = ScanContext(
                    scope = scope,
                    buttonConfigs = combinedButtonConfigs,
                    rows = totalRows,
                    columns = totalCols,
                    rowNames = combinedRowNames,
                    startIndex = if (staticRowPage != null && startIndex > 0 && pattern == "linear") {
                        startIndex + staticRowOffset
                    } else startIndex,
                    focusedButtonIndex = stateManager.focusedButtonIndex,
                    focusedRowIndex = stateManager.focusedRowIndex,
                    onSpeakCue = { handleSpeakCue(it) },
                    onPrefetchCue = { handlePrefetchCue(it) },
                    onCycleCompleted = { _onCycleCompleted.emit(Unit) },
                    delayMillis = scanTimer.scanDelayMillis,
                    featureGuard = featureGuard,
                    hasStaticRow = staticRowPage != null,
                    staticRowPattern = staticRowPattern,
                    pagePattern = pattern,
                    mainRows = rows,
                    mainColumns = columns
                )

                strategy.executeScan(context)
            } finally {
                synchronized(lock) {
                    if (scanJob === this@launch) {
                        stateManager.setScanning(false)
                    }
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

    fun selectCurrentRow() = synchronized(lock) {
        val currentRowIndex = focusedRowIndex.value ?: return@synchronized
        // Konsistenten Snapshot des aktuellen Zustands ziehen, damit ein paralleles
        // startScanning die im Coroutine-Body genutzten Felder nicht unter uns wegzieht.
        val staticRowPage = currentStaticRowPage
        val buttonConfigsSnapshot = currentButtonConfigs
        val rowsSnapshot = currentRows
        val columnsSnapshot = currentColumns
        val patternSnapshot = currentPattern
        val staticRowPatternSnapshot = currentStaticRowPattern

        scanJob?.cancel()
        scanJob = null

        stateManager.setScanning(true)
        val job = scope.launch {
            try {
                val combinedButtonConfigs = mutableListOf<ButtonConfig?>()
                if (staticRowPage != null) {
                    combinedButtonConfigs.addAll(staticRowPage.buttonConfigs)
                    while (combinedButtonConfigs.size < 49) {
                        combinedButtonConfigs.add(null)
                    }
                }
                combinedButtonConfigs.addAll(buttonConfigsSnapshot)

                val totalCols = if (staticRowPage != null) maxOf(staticRowPage.columns, columnsSnapshot) else columnsSnapshot

                val context = ScanContext(
                    scope = scope,
                    buttonConfigs = combinedButtonConfigs,
                    rows = (if (staticRowPage != null) 1 else 0) + rowsSnapshot,
                    columns = totalCols,
                    rowNames = emptyList(),
                    startIndex = 0,
                    focusedButtonIndex = stateManager.focusedButtonIndex,
                    focusedRowIndex = stateManager.focusedRowIndex,
                    onSpeakCue = { handleSpeakCue(it) },
                    onPrefetchCue = { handlePrefetchCue(it) },
                    onCycleCompleted = { _onCycleCompleted.emit(Unit) },
                    delayMillis = scanTimer.scanDelayMillis,
                    featureGuard = featureGuard,
                    hasStaticRow = staticRowPage != null,
                    staticRowPattern = staticRowPatternSnapshot,
                    pagePattern = patternSnapshot,
                    mainRows = rowsSnapshot,
                    mainColumns = columnsSnapshot
                )

                rowByRowStrategy.executeButtonScanInRow(context, currentRowIndex)
            } finally {
                synchronized(lock) {
                    if (scanJob === this@launch) {
                        stateManager.setScanning(false)
                    }
                }
            }
        }
        scanJob = job
    }

    fun pauseScanning() = synchronized(lock) {
        scanJob?.cancel()
        scanJob = null
        stateManager.setScanning(false)
    }

    fun stopScanning() = synchronized(lock) {
        scanJob?.cancel()
        scanJob = null
        stateManager.setScanning(false)
        stateManager.clear()
    }

    fun setFocusedIndex(index: Int?) {
        stateManager.setFocusedButtonIndex(index)
    }

    fun setFocusedRowIndex(index: Int?) {
        stateManager.setFocusedRowIndex(index)
    }
    
    fun clear() = synchronized(lock) {
        scanJob?.cancel()
        stateManager.clear()
    }
}

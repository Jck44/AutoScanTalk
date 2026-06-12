package com.andreas_kratzer.ghosttalk.core.scanning

import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

sealed interface ResumePoint {
    data class AtButton(val combinedIndex: Int) : ResumePoint
    data class AtRow(val rowIndex: Int) : ResumePoint
}

data class ScanContext(
    val scope: CoroutineScope,
    val buttonConfigs: List<ButtonConfig?>,
    val rows: Int,
    val columns: Int,
    val rowNames: List<String>,
    val startIndex: Int,
    val focusedButtonIndex: MutableStateFlow<Int?>,
    val focusedRowIndex: MutableStateFlow<Int?>,
    val onSpeakCue: suspend (String) -> Unit,
    val onPrefetchCue: suspend (String) -> Unit,
    val onCycleCompleted: suspend () -> Unit,
    val delayMillis: () -> Long,
    val featureGuard: FeatureGuardProxy,
    val hasStaticRow: Boolean = false,
    val staticRowPattern: String = "linear",
    val pagePattern: String = "row_by_row",
    val mainRows: Int = rows,
    val mainColumns: Int = columns,
    val resumePoint: ResumePoint? = null
)

interface ScanStrategy {
    suspend fun executeScan(context: ScanContext)
}

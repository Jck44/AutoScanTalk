package com.andreas_kratzer.ghosttalk.core.scanning

import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

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
    val delayMillis: Long,
    val featureGuard: FeatureGuardProxy
)

interface ScanStrategy {
    suspend fun executeScan(context: ScanContext)
}


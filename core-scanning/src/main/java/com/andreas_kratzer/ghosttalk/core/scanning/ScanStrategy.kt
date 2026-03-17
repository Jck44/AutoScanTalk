package com.andreas_kratzer.ghosttalk.core.scanning

import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import kotlinx.coroutines.flow.MutableStateFlow

interface ScanStrategy {
    suspend fun executeScan(
        buttonConfigs: List<ButtonConfig?>,
        rows: Int,
        columns: Int,
        rowNames: List<String>,
        startIndex: Int,
        focusedButtonIndex: MutableStateFlow<Int?>,
        focusedRowIndex: MutableStateFlow<Int?>,
        onSpeakCue: suspend (String) -> Unit,
        onCycleCompleted: suspend () -> Unit,
        delayMillis: Long,
        featureGuard: FeatureGuardProxy
    )
}

package com.andreas_kratzer.ghosttalk.core.scanning

import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.domain.settings.FeatureGuard
import kotlinx.coroutines.flow.MutableStateFlow

interface ScanStrategy {
    suspend fun executeScan(
        buttonConfigs: List<ButtonConfig?>,
        columns: Int,
        rowNames: List<String>,
        startIndex: Int,
        focusedButtonIndex: MutableStateFlow<Int?>,
        focusedRowIndex: MutableStateFlow<Int?>,
        onSpeakCue: suspend (String) -> Unit,
        delayMillis: Long,
        featureGuard: FeatureGuard
    )
}

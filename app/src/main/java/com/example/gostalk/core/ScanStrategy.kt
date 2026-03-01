package com.example.gostalk.core

import com.example.gostalk.model.ButtonConfig
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
        delayMillis: Long
    )
}

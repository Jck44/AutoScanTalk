package com.andreas_kratzer.ghosttalk.core.scanning

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanStateManager @Inject constructor() {
    val focusedButtonIndex = MutableStateFlow<Int?>(null)
    val focusedRowIndex = MutableStateFlow<Int?>(null)
    val isScanning = MutableStateFlow(false)

    fun setFocusedButtonIndex(index: Int?) {
        focusedButtonIndex.value = index
    }

    fun setFocusedRowIndex(index: Int?) {
        focusedRowIndex.value = index
    }

    fun setScanning(scanning: Boolean) {
        isScanning.value = scanning
    }

    fun clear() {
        focusedButtonIndex.value = null
        focusedRowIndex.value = null
    }
}

package com.andreas_kratzer.ghosttalk.core.scanning

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanStateManager @Inject constructor() {
    private val _focusedButtonIndex = MutableStateFlow<Int?>(null)
    val focusedButtonIndex: StateFlow<Int?> = _focusedButtonIndex.asStateFlow()

    private val _focusedRowIndex = MutableStateFlow<Int?>(null)
    val focusedRowIndex: StateFlow<Int?> = _focusedRowIndex.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    fun setFocusedButtonIndex(index: Int?) {
        _focusedButtonIndex.value = index
    }

    fun setFocusedRowIndex(index: Int?) {
        _focusedRowIndex.value = index
    }

    fun setScanning(scanning: Boolean) {
        _isScanning.value = scanning
    }

    fun clear() {
        _focusedButtonIndex.value = null
        _focusedRowIndex.value = null
    }
}

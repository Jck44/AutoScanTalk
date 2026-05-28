package com.andreas_kratzer.ghosttalk.core.actions

import kotlinx.coroutines.flow.StateFlow

/**
 * Interface to provide action execution state to the scanner.
 */
interface ScannerActionProvider {
    val isExecuting: StateFlow<Boolean>
}

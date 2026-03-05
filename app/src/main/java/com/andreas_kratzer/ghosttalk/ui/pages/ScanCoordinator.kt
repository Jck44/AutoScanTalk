package com.andreas_kratzer.ghosttalk.ui.pages

import android.util.Log
import com.andreas_kratzer.ghosttalk.core.actions.ActionExecutor
import com.andreas_kratzer.ghosttalk.core.scanning.ScannerEngine
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.model.Page
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Coordinates scanning lifecycle: start, stop, pause, resume.
 *
 * Extracted from PageViewModel to isolate scanning logic from page management,
 * action handling, and other ViewModel concerns.
 */
class ScanCoordinator(
    private val scope: CoroutineScope,
    private val scannerEngine: ScannerEngine,
    private val settingsRepository: SettingsRepository,
    private val actionExecutor: ActionExecutor,
    private val currentPage: StateFlow<Page?>,
    private val isUserModeActive: StateFlow<Boolean>
) {
    val focusedButtonIndex: StateFlow<Int?> = scannerEngine.focusedButtonIndex
    val focusedRowIndex: StateFlow<Int?> = scannerEngine.focusedRowIndex

    fun init() {
        // Scanning trigger: observe ActionExecutor, current page, and user mode state
        scope.launch {
            combine(
                actionExecutor.isExecuting,
                currentPage,
                isUserModeActive
            ) { isExecuting, page, isActive -> Triple(isExecuting, page, isActive) }
                .collect { (isExecuting, _, isActive) ->
                    if (!isActive) {
                        Log.d("ScanCoordinator", "User mode inactive, ignoring state change.")
                        return@collect
                    }
                    if (isExecuting) {
                        Log.d("ScanCoordinator", "ActionExecutor is executing. Pausing scan.")
                        stopScanningTemporarily()
                    } else if (currentPage.value != null) {
                        Log.d("ScanCoordinator", "Ready to resume on page ${currentPage.value?.id}")
                        resumeScanningIfEnabled()
                    }
                }
        }

        // React to scan delay changes
        scope.launch {
            settingsRepository.scanDelayFlow.collect { delay -> setScanDelay(delay) }
        }
    }

    fun resumeScanningIfEnabled() {
        if (actionExecutor.isExecuting.value) {
            Log.d("ScanCoordinator", "resumeScanningIfEnabled: ActionExecutor busy, skipping.")
            return
        }
        if (settingsRepository.autoStartScanning) {
            val startIndex = if (settingsRepository.resumeScanningFromStart) {
                0
            } else {
                focusedButtonIndex.value ?: 0
            }
            startScanning(startIndex)
        }
    }

    fun startScanning(startIndex: Int = 0) {
        val page = currentPage.value ?: return
        scannerEngine.startScanning(
            buttonConfigs = page.buttonConfigs,
            startIndex = startIndex,
            pattern = page.scanPattern ?: settingsRepository.defaultScanPattern,
            columns = page.columns,
            rowNames = page.rowNames,
            pageId = page.id
        )
    }

    fun setScanDelay(delayMillis: Long) {
        scannerEngine.scanDelayMillis = delayMillis
        if (settingsRepository.autoStartScanning) {
            startScanning()
        }
    }

    fun stopScanningTemporarily() {
        scannerEngine.pauseScanning()
    }

    fun stopScanning() {
        scannerEngine.stopScanning()
    }

    fun selectCurrentRow() {
        scannerEngine.selectCurrentRow()
    }

    fun setFocusedIndex(index: Int?) {
        scannerEngine.setFocusedIndex(index)
    }

    fun onPageChanged(isSamePage: Boolean) {
        if (isSamePage) {
            scannerEngine.pauseScanning()
        } else {
            scannerEngine.stopScanning()
        }
    }

    fun clear() {
        scannerEngine.clear()
    }
}

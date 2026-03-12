package com.andreas_kratzer.ghosttalk.domain.actions

import com.andreas_kratzer.ghosttalk.core.actions.ActionExecutor
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.ui.pages.ScanCoordinator
import javax.inject.Inject

class ActivateFocusedButtonUseCase @Inject constructor(
    private val activateButtonUseCase: ActivateButtonUseCase
) {
    suspend fun execute(
        currentPage: Page?,
        activeBookId: String?,
        isUserModeActive: Boolean,
        smartPredictions: List<String>,
        actionExecutor: ActionExecutor,
        scanCoordinator: ScanCoordinator
    ) {
        val focusedIdx = scanCoordinator.focusedButtonIndex.value
        val focusedRow = scanCoordinator.focusedRowIndex.value
        
        if (focusedIdx != null) {
            activateButtonUseCase.execute(
                index = focusedIdx,
                currentPage = currentPage,
                activeBookId = activeBookId,
                isUserModeActive = isUserModeActive,
                smartPredictions = smartPredictions,
                actionExecutor = actionExecutor,
                scanCoordinator = scanCoordinator
            )
        } else if (focusedRow != null) {
            scanCoordinator.selectCurrentRow()
        }
    }
}

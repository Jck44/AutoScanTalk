package com.andreas_kratzer.ghosttalk.domain

import com.andreas_kratzer.ghosttalk.core.actions.ActionExecutor
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.model.Page
import javax.inject.Inject

class ResolveSmartPredictionUseCase @Inject constructor(
    private val pageRepository: PageRepository
) {
    suspend fun execute(
        predictionId: String,
        currentPage: Page?,
        activeBookId: String?,
        isUserModeActive: Boolean,
        actionExecutor: ActionExecutor
    ) {
        val matchingButton = currentPage?.buttonConfigs?.find { it?.id == predictionId }
        if (matchingButton != null) {
            actionExecutor.executeButtonAction(
                matchingButton,
                bookId = activeBookId.takeIf { isUserModeActive }
            )
            return
        }

        val targetPage = pageRepository.getPageById(predictionId)
        if (targetPage != null) {
            actionExecutor.executeButtonAction(
                ButtonConfig(
                    label = targetPage.name,
                    auditoryCue = null,
                    buttonAction = NavigateToPageButtonAction(targetPage.id)
                ),
                bookId = activeBookId.takeIf { isUserModeActive }
            )
        }
    }
}

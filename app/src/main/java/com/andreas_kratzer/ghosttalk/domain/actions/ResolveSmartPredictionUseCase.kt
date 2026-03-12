package com.andreas_kratzer.ghosttalk.domain.actions

import com.andreas_kratzer.ghosttalk.core.actions.ActionExecutor
import com.andreas_kratzer.ghosttalk.core.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.data.PageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    ) = withContext(Dispatchers.Default) {
        // 1. Check current page
        val matchingButtonInCurrent = currentPage?.buttonConfigs?.filterNotNull()?.find { it.id == predictionId }
        if (matchingButtonInCurrent != null) {
            actionExecutor.executeButtonAction(
                matchingButtonInCurrent, 
                activeBookId.takeIf { isUserModeActive }, 
                currentPage.rows, 
                currentPage.columns
            )
            return@withContext
        }

        // 2. Check all pages - move to IO for the DB call
        val allPages = withContext(Dispatchers.IO) { pageRepository.getAllPages() }
        
        for (p in allPages) {
            val btn = p.buttonConfigs.filterNotNull().find { it.id == predictionId }
            if (btn != null) {
                actionExecutor.executeButtonAction(btn, activeBookId.takeIf { isUserModeActive }, p.rows, p.columns)
                return@withContext
            }
        }

        // 3. Check if it's a page
        val targetPage = withContext(Dispatchers.IO) { pageRepository.getPageById(predictionId) }
        if (targetPage != null) {
            val navConfig = ButtonConfig(
                id = targetPage.id,
                label = targetPage.name,
                auditoryCue = AuditoryCue.TextToSpeechCue(targetPage.name),
                buttonAction = NavigateToPageButtonAction(targetPage.id)
            )
            actionExecutor.executeButtonAction(navConfig, activeBookId.takeIf { isUserModeActive }, 1, 1)
            return@withContext
        }
    }
}

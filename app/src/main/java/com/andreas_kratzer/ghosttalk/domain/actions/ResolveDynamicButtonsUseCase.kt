package com.andreas_kratzer.ghosttalk.domain.actions

import com.andreas_kratzer.ghosttalk.core.actions.FrequentActionResolver
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.model.ButtonAction
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.FrequentActionButtonAction
import com.andreas_kratzer.ghosttalk.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.SmartPredictionButtonAction
import javax.inject.Inject

/**
 * UseCase to resolve dynamic buttons (Frequent Actions and Smart Predictions)
 * into concrete buttons at load time.
 */
class ResolveDynamicButtonsUseCase @Inject constructor(
    private val frequentActionResolver: FrequentActionResolver,
    private val pageRepository: PageRepository
) {

    suspend fun execute(
        page: Page,
        bookId: String,
        smartPredictions: List<String>?
    ): Page {
        // Step 1: Resolve Frequent Actions
        val frequentlyResolvedPage = frequentActionResolver.resolve(page, bookId)

        // Step 2: Resolve Smart Predictions
        val finalConfigs = frequentlyResolvedPage.buttonConfigs.map { config ->
            val action = config?.buttonAction
            if (action is SmartPredictionButtonAction && config.isActive) {
                if (smartPredictions == null) {
                    return@map config // Keep placeholder while waiting
                }
                val predictionId = smartPredictions.getOrNull(action.rank - 1)
                if (predictionId != null) {
                    resolveSmartPrediction(predictionId, frequentlyResolvedPage, config)
                } else {
                    null // No prediction available for this rank, deactivate/hide button
                }
            } else {
                config
            }
        }

        return frequentlyResolvedPage.copy(buttonConfigs = finalConfigs)
    }

    private suspend fun resolveSmartPrediction(predictionId: String, currentPage: Page, originalConfig: ButtonConfig): ButtonConfig? {
        // Check if it's a button on the current page
        val matchingButton = currentPage.buttonConfigs.filterNotNull().find { it.id == predictionId }
        
        if (matchingButton != null) {
            // Recursion guard: A dynamic button cannot resolve to another dynamic button
            if (isDynamic(matchingButton.buttonAction)) {
                return null
            }
            return matchingButton
        }

        // Expanded search: Check all pages for the predicted button ID
        val allPages = pageRepository.getAllPages()
        for (p in allPages) {
            val btn = p.buttonConfigs.filterNotNull().find { it.id == predictionId }
            if (btn != null) {
                if (isDynamic(btn.buttonAction)) return null
                return btn
            }
        }

        // Check if it's a page navigation
        val targetPage = pageRepository.getPageById(predictionId)
        if (targetPage != null) {
            return ButtonConfig(
                id = targetPage.id,
                label = targetPage.name,
                auditoryCue = AuditoryCue.TextToSpeechCue(targetPage.name),
                buttonAction = NavigateToPageButtonAction(targetPage.id)
            )
        }

        return null
    }

    private fun isDynamic(action: ButtonAction): Boolean {
        return action is SmartPredictionButtonAction || action is FrequentActionButtonAction
    }
}

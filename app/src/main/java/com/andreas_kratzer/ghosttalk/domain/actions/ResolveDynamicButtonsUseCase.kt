package com.andreas_kratzer.ghosttalk.domain.actions

import com.andreas_kratzer.ghosttalk.core.actions.FrequentActionResolver
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
    private val frequentActionResolver: FrequentActionResolver
) {

    suspend fun execute(
        page: Page,
        bookId: String,
        smartPredictions: List<String>?,
        allPages: List<Page>
    ): Page {
        // Create lookup maps for performance
        val buttonLookup = allPages.flatMap { it.buttonConfigs }.filterNotNull().associateBy { it.id }
        val pageLookup = allPages.associateBy { it.id }

        // Step 1: Resolve Frequent Actions
        // Note: FrequentActionResolver might still use DB internally if not refactored, 
        // but we start with Smart Predictions here.
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
                    resolveSmartPrediction(predictionId, frequentlyResolvedPage, config, buttonLookup, pageLookup)
                } else {
                    null // No prediction available for this rank, deactivate/hide button
                }
            } else {
                config
            }
        }

        return frequentlyResolvedPage.copy(buttonConfigs = finalConfigs)
    }

    private fun resolveSmartPrediction(
        predictionId: String, 
        currentPage: Page, 
        originalConfig: ButtonConfig,
        buttonLookup: Map<String, ButtonConfig>,
        pageLookup: Map<String, Page>
    ): ButtonConfig? {
        // 1. Check if it's a button on the current page
        val matchingButtonInCurrent = currentPage.buttonConfigs.filterNotNull().find { it.id == predictionId }
        if (matchingButtonInCurrent != null) {
            if (isDynamic(matchingButtonInCurrent.buttonAction)) return null
            return matchingButtonInCurrent
        }

        // 2. Check in lookup map (all buttons from all pages)
        val matchingButton = buttonLookup[predictionId]
        if (matchingButton != null) {
            if (isDynamic(matchingButton.buttonAction)) return null
            return matchingButton
        }

        // 3. Check if it's a page navigation
        val targetPage = pageLookup[predictionId]
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

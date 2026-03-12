package com.andreas_kratzer.ghosttalk.domain.actions

import com.andreas_kratzer.ghosttalk.core.actions.FrequentActionResolver
import com.andreas_kratzer.ghosttalk.core.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.FrequentActionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.SmartPredictionButtonAction
import javax.inject.Inject

/**
 * UseCase to resolve dynamic buttons (Frequent Actions and Smart Predictions)
 * into concrete buttons at load time.
 */
class ResolveDynamicButtonsUseCase @Inject constructor(
    private val frequentActionResolver: FrequentActionResolver
) {

    private var lastAllPagesRef: List<Page>? = null
    private var cachedButtonLookup: Map<String, ButtonConfig>? = null
    private var cachedPageLookup: Map<String, Page>? = null

    suspend fun execute(
        page: Page,
        bookId: String,
        smartPredictions: List<String>?,
        allPages: List<Page>
    ): Page {
        // Step 0: Check if any dynamic resolution is needed at all
        val hasDynamicButtons = page.buttonConfigs.any { config ->
            config?.isActive == true && isDynamic(config.buttonAction)
        }
        if (!hasDynamicButtons) {
            return page
        }

        // Step 1: Create/Get lookup maps for performance
        // Memoization: Only rebuild if the list reference changed
        if (lastAllPagesRef !== allPages || cachedButtonLookup == null || cachedPageLookup == null) {
            cachedButtonLookup = allPages.flatMap { it.buttonConfigs }.filterNotNull().associateBy { it.id }
            cachedPageLookup = allPages.associateBy { it.id }
            lastAllPagesRef = allPages
        }
        val buttonLookup = cachedButtonLookup!!
        val pageLookup = cachedPageLookup!!

        // Step 2: Resolve Frequent Actions
        val frequentlyResolvedPage = frequentActionResolver.resolve(page, bookId)

        // Step 3: Resolve Smart Predictions
        var changed = frequentlyResolvedPage !== page
        val finalConfigs = frequentlyResolvedPage.buttonConfigs.map { config ->
            val action = config?.buttonAction
            if (action is SmartPredictionButtonAction && config.isActive) {
                if (smartPredictions == null) {
                    return@map config // Keep placeholder while waiting
                }
                val predictionId = smartPredictions.getOrNull(action.rank - 1)
                val resolved = if (predictionId != null) {
                    resolveSmartPrediction(predictionId, frequentlyResolvedPage, config, buttonLookup, pageLookup)
                } else {
                    null // No prediction available for this rank, deactivate/hide button
                }
                if (resolved !== config) changed = true
                resolved
            } else {
                config
            }
        }

        return if (changed) frequentlyResolvedPage.copy(buttonConfigs = finalConfigs) else frequentlyResolvedPage
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

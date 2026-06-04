package com.andreas_kratzer.ghosttalk.domain.actions

import android.app.Application
import com.andreas_kratzer.ghosttalk.core.actions.FrequentActionResolver
import com.andreas_kratzer.ghosttalk.core.data.ActionLogProvider
import com.andreas_kratzer.ghosttalk.core.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.FrequentActionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.PredictionType
import com.andreas_kratzer.ghosttalk.core.model.PreviousActionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SmartPredictionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import javax.inject.Inject

/**
 * UseCase to resolve dynamic buttons (Frequent Actions and Smart Predictions)
 * into concrete buttons at load time.
 */
class ResolveDynamicButtonsUseCase @Inject constructor(
    private val frequentActionResolver: FrequentActionResolver,
    private val actionLogProvider: ActionLogProvider,
    private val application: Application
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

        // Step 3: Load history once for all "Previous Action" buttons on this page
        // Filter out dynamic actions to prevent recursion and ensure we resolve to actual tasks.
        val hasPreviousActionButtons = page.buttonConfigs.any { it?.buttonAction is PreviousActionButtonAction }
        val filteredHistory = if (hasPreviousActionButtons) {
            val fullHistory = actionLogProvider.loadSavedLogEntries()
            fullHistory.filter { entry ->
                val hAction = entry.action
                hAction != null && !isDynamic(hAction)
            }
        } else {
            emptyList()
        }

        // Step 4: Resolve Smart Predictions and Previous Actions
        var changed = frequentlyResolvedPage !== page
        val finalConfigs = frequentlyResolvedPage.buttonConfigs.mapIndexed { index, config ->
            if (config == null) return@mapIndexed null
            
            // Check if the original config at this index was active and was a FrequentActionButtonAction
            val originalConfig = page.buttonConfigs.getOrNull(index)
            val originalAction = originalConfig?.buttonAction
            
            val action = config.buttonAction
            if (action is SmartPredictionButtonAction && config.isActive) {
                if (smartPredictions == null) {
                    return@mapIndexed config // Keep placeholder while waiting
                }
                val filteredPredictions = smartPredictions.filter { predId ->
                    matchesType(predId, action.predictionType, frequentlyResolvedPage, buttonLookup, pageLookup) &&
                        resolveSmartPrediction(predId, frequentlyResolvedPage, buttonLookup, pageLookup) != null
                }
                val predictionId = filteredPredictions.getOrNull(action.rank - 1)
                val resolved = if (predictionId != null) {
                    resolveSmartPrediction(predictionId, frequentlyResolvedPage, buttonLookup, pageLookup)
                } else {
                    null // No prediction available for this rank, deactivate/hide button
                }
                if (resolved !== config) changed = true
                resolved
            } else if (action is PreviousActionButtonAction && config.isActive) {
                val entry = filteredHistory.getOrNull(action.rank - 1)
                val historicalAction = entry?.action
                val resolved = if (historicalAction != null) {
                    val label = entry.label ?: entry.message
                    val resolvedCue = label
                    val prefixedCue = if (action.rank == 1) {
                        application.getString(com.andreas_kratzer.ghosttalk.R.string.audio_cue_last_action_prefix, resolvedCue)
                    } else {
                        application.getString(com.andreas_kratzer.ghosttalk.R.string.audio_cue_previous_action_prefix_format, action.rank, resolvedCue)
                    }
                    config.copy(
                        label = label, 
                        buttonAction = historicalAction,
                        auditoryCue = AuditoryCue.TextToSpeechCue(prefixedCue)
                    )
                } else {
                    // No history available, provide feedback in ear (auditory cue)
                    config.copy(
                        label = "No History",
                        buttonAction = SpeakTextButtonAction(),
                        spokenText = "Keine vorherige Aktion gefunden", 
                        playActionAsAuditoryCue = true
                    )
                }
                if (resolved !== config) changed = true
                resolved
            } else if (originalAction is FrequentActionButtonAction && originalConfig.isActive) {
                val resolvedCueText = (config.auditoryCue as? AuditoryCue.TextToSpeechCue)?.text?.takeIf { it.isNotBlank() } ?: config.label
                val prefixedCue = if (originalAction.rank == 1) {
                    application.getString(com.andreas_kratzer.ghosttalk.R.string.audio_cue_frequent_action_prefix, resolvedCueText)
                } else {
                    application.getString(com.andreas_kratzer.ghosttalk.R.string.audio_cue_frequent_action_prefix_format, originalAction.rank, resolvedCueText)
                }
                val resolved = config.copy(
                    auditoryCue = AuditoryCue.TextToSpeechCue(prefixedCue)
                )
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
        return action is SmartPredictionButtonAction || action is FrequentActionButtonAction || action is PreviousActionButtonAction
    }

    private fun matchesType(
        predictionId: String,
        predictionType: PredictionType,
        currentPage: Page,
        buttonLookup: Map<String, ButtonConfig>,
        pageLookup: Map<String, Page>
    ): Boolean {
        if (predictionType == PredictionType.ALL) return true

        val isNav = pageLookup.containsKey(predictionId) || run {
            val button = currentPage.buttonConfigs.filterNotNull().find { it.id == predictionId }
                ?: buttonLookup[predictionId]
            button?.buttonAction is NavigateToPageButtonAction
        }

        return if (predictionType == PredictionType.NAVIGATION) isNav else !isNav
    }
}

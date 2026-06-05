package com.andreas_kratzer.ghosttalk.core.data.impl.analytics

import android.util.Log
import com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository.ButtonUsageEvent
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.util.GridUtils
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class PathAnalyzer @Inject constructor() {

    data class ShortcutRecommendation(
        val sourcePageId: String,
        val sourcePageName: String,
        val targetButtonConfig: ButtonConfig,
        val occurrenceCount: Int,
        val estimatedTimeSavedSec: Int
    )

    /**
     * Helper to compute exact scanning steps for a button in its page layout context.
     */
    private fun getScanSteps(page: Page, button: ButtonConfig): Int {
        val buttons = page.buttonConfigs
        val rows = page.rows
        val columns = page.columns
        val pattern = page.scanPattern?.takeIf { it.isNotBlank() && it != "default" } ?: "row_by_row"
        
        val activeButtons = buttons.mapIndexedNotNull { index, btn ->
            if (btn != null && btn.isActive && GridUtils.isVisibleInGrid(index, rows, columns)) {
                Pair(index, btn)
            } else null
        }
        
        val buttonIndex = activeButtons.indexOfFirst { it.second.id == button.id }
        if (buttonIndex == -1) return 1
        
        return if (pattern == "row_by_row") {
            val activeRows = (0 until rows).filter { r ->
                (0 until columns).any { c ->
                    val gIdx = GridUtils.getGlobalIndex(r, c)
                    buttons.getOrNull(gIdx)?.let { it.isActive && GridUtils.isVisibleInGrid(gIdx, rows, columns) } == true
                }
            }
            val (globalIdx, _) = activeButtons[buttonIndex]
            val r = globalIdx / GridUtils.MAX_GRID_SIZE
            val rowsBefore = activeRows.indexOf(r).coerceAtLeast(0)
            
            val activeButtonsInRow = (0 until columns).mapNotNull { col ->
                val gIdx = GridUtils.getGlobalIndex(r, col)
                val btn = buttons.getOrNull(gIdx)
                if (btn != null && btn.isActive && GridUtils.isVisibleInGrid(gIdx, rows, columns)) btn else null
            }
            val buttonsBeforeInRow = activeButtonsInRow.indexOf(button).coerceAtLeast(0)
            (rowsBefore + 1) + (buttonsBeforeInRow + 1)
        } else {
            buttonIndex + 1
        }
    }

    /**
     * Analyzes historical chronological click events and generates shortcut suggestions.
     *
     * @param historyEvents Chronological list of past button presses.
     * @param pages All unfiltered pages in the current book (to find page names and verify slots/buttons).
     * @param scanDelayMs Current autoscan delay in milliseconds.
     * @param defaultStartPageId The ID of the start page.
     */
    fun analyzePaths(
        historyEvents: List<ButtonUsageEvent>,
        pages: List<Page>,
        scanDelayMs: Long,
        defaultStartPageId: String? = null
    ): List<ShortcutRecommendation> {
        Log.d("PathAnalyzer", "analyzePaths: historyEvents size = ${historyEvents.size}, pages size = ${pages.size}, scanDelay = $scanDelayMs, startPage = $defaultStartPageId")
        if (historyEvents.size < 3 || pages.isEmpty()) {
            Log.d("PathAnalyzer", "analyzePaths: Aborting. historyEvents < 3 or pages is empty.")
            return emptyList()
        }

        // 1. Sort chronologically (ascending) and ignore accidental/error clicks
        val sortedEvents = historyEvents
            .filter { !it.isAccidental }
            .sortedBy { it.timestamp }

        // 2. Group into sessions based on sessionId if available, falling back to 5-minute inactivity gap
        val sessions = mutableListOf<List<ButtonUsageEvent>>()
        var currentSession = mutableListOf<ButtonUsageEvent>()


        for (event in sortedEvents) {
            if (currentSession.isEmpty()) {
                currentSession.add(event)
            } else {
                val lastEvent = currentSession.last()
                val isNewSession = if (event.sessionId != null && lastEvent.sessionId != null) {
                    event.sessionId != lastEvent.sessionId
                } else {
                    event.timestamp - lastEvent.timestamp > 5 * 60 * 1000
                }
                if (isNewSession) {
                    sessions.add(currentSession)
                    currentSession = mutableListOf(event)
                } else {
                    currentSession.add(event)
                }
            }
        }
        if (currentSession.isNotEmpty()) {
            sessions.add(currentSession)
        }
        Log.d("PathAnalyzer", "analyzePaths: Grouped into ${sessions.size} sessions")

        // 3. Process sessions to identify page-to-button transition bottlenecks
        val pageMap = pages.associateBy { it.id }
        
        // Map of SourcePageId -> Map of TargetButtonId -> List of transition timestamps
        val transitionCounts = mutableMapOf<String, MutableMap<String, MutableList<Long>>>()
        // Map to hold target ButtonConfigs for easy construction
        val targetButtonConfigs = mutableMapOf<String, ButtonConfig>()

        // Calculate dynamic threshold based on scan delay
        // 10 steps minimum navigation + 25s cognitive/reading/selection tolerance padding
        val maxIntervalMs = (10 * scanDelayMs) + 25000L
        Log.d("PathAnalyzer", "analyzePaths: maxIntervalMs = $maxIntervalMs")

        for ((_, session) in sessions.withIndex()) {
            if (session.size < 2) {
                continue
            }

            // Keep track of the active context (thematic source page)
            var activeSourcePageId: String? = null
            var activeSourceTimestamp = 0L

            for (event in session) {
                val currentPageId = event.pageId ?: continue
                val currentButtonId = event.buttonId ?: continue

                // Check if this button is a standard navigation/system back button
                val isSystemAction = event.label.equals("Zurück", ignoreCase = true) || 
                                     event.label.equals("Startseite", ignoreCase = true) ||
                                     event.actionType.contains("Previous", ignoreCase = true)

                if (activeSourcePageId == null) {
                    // Start a new source context if we are on a valid thematic page and not doing system back navigations
                    if (!isSystemAction) {
                        activeSourcePageId = currentPageId
                        activeSourceTimestamp = event.timestamp
                    }
                } else {
                    if (currentPageId == activeSourcePageId) {
                        // Still on the same page, update the reference timestamp for the active source
                        if (!isSystemAction) {
                            activeSourceTimestamp = event.timestamp
                        }
                    } else {
                        // We are on a different page now!
                        // Check if this click represents an actual vocabulary selection (non-system action)
                        if (!isSystemAction) {
                            val interval = event.timestamp - activeSourceTimestamp
                            if (interval in 1..maxIntervalMs) {
                                // Find the actual Page objects
                                val sourcePage = pageMap[activeSourcePageId]
                                val targetPage = pageMap[currentPageId]

                                if (sourcePage != null && targetPage != null) {
                                    // Retrieve the button config
                                    val buttonConfig = targetPage.buttonConfigs.find { it?.id == currentButtonId }
                                    if (buttonConfig != null) {
                                        // Verify this button does NOT already exist on the source page (no duplicate needed)
                                        val alreadyExists = sourcePage.buttonConfigs.any { 
                                            it != null && (it.id == buttonConfig.id || it.label.equals(buttonConfig.label, ignoreCase = true)) 
                                        }

                                        if (!alreadyExists) {
                                            targetButtonConfigs[buttonConfig.id] = buttonConfig
                                            transitionCounts.getOrPut(activeSourcePageId) { mutableMapOf() }
                                                .getOrPut(buttonConfig.id) { mutableListOf() }
                                                .add(event.timestamp)
                                        }
                                    }
                                }
                            }
                        }
                        
                        // After transition, update the active source context to the current page (if it's not a system action)
                        if (!isSystemAction) {
                            activeSourcePageId = currentPageId
                            activeSourceTimestamp = event.timestamp
                        } else {
                            // If it's a back button, reset active source page
                            activeSourcePageId = null
                        }
                    }
                }
            }
        }

        // 4. Run dynamic self-adjusting recommendation generation
        var currentDegradation = 0.15
        var currentMinOccurrences = 2
        var currentMinStepsSaved = 2

        var results = generateRecommendations(
            transitionCounts = transitionCounts,
            targetButtonConfigs = targetButtonConfigs,
            pageMap = pageMap,
            pages = pages,
            scanDelayMs = scanDelayMs,
            defaultStartPageId = defaultStartPageId,
            minOccurrences = currentMinOccurrences,
            degradationFactor = currentDegradation,
            minStepsSaved = currentMinStepsSaved
        )

        Log.d("PathAnalyzer", "analyzePaths: Initial pass yielded ${results.size} recommendations (degradation=$currentDegradation, minOccurrences=$currentMinOccurrences, minStepsSaved=$currentMinStepsSaved)")

        if (results.isEmpty()) {
            // Self-adjust: LOOSEN constraints since we have no suggestions
            currentDegradation = 0.05
            currentMinStepsSaved = 1
            Log.d("PathAnalyzer", "analyzePaths: Zero recommendations found. Relaxing rules (degradation=$currentDegradation, minStepsSaved=$currentMinStepsSaved)")
            results = generateRecommendations(
                transitionCounts = transitionCounts,
                targetButtonConfigs = targetButtonConfigs,
                pageMap = pageMap,
                pages = pages,
                scanDelayMs = scanDelayMs,
                defaultStartPageId = defaultStartPageId,
                minOccurrences = currentMinOccurrences,
                degradationFactor = currentDegradation,
                minStepsSaved = currentMinStepsSaved
            )
        } else if (results.size > 5) {
            // Self-adjust: TIGHTEN constraints since we have too many suggestions
            currentDegradation = 0.3
            currentMinOccurrences = 3
            currentMinStepsSaved = 3
            Log.d("PathAnalyzer", "analyzePaths: Too many recommendations (${results.size}). Tightening rules (degradation=$currentDegradation, minOccurrences=$currentMinOccurrences, minStepsSaved=$currentMinStepsSaved)")
            results = generateRecommendations(
                transitionCounts = transitionCounts,
                targetButtonConfigs = targetButtonConfigs,
                pageMap = pageMap,
                pages = pages,
                scanDelayMs = scanDelayMs,
                defaultStartPageId = defaultStartPageId,
                minOccurrences = currentMinOccurrences,
                degradationFactor = currentDegradation,
                minStepsSaved = currentMinStepsSaved
            )

            // Hard cap as fallback to avoid UI clutter
            if (results.size > 5) {
                results = results.sortedWith(
                    compareByDescending<ShortcutRecommendation> { it.occurrenceCount }
                        .thenByDescending { it.estimatedTimeSavedSec }
                ).take(5)
            }
        }

        // Sort final recommendations by occurrence count descending, then by time saved
        val finalResults = results.sortedWith(
            compareByDescending<ShortcutRecommendation> { it.occurrenceCount }
                .thenByDescending { it.estimatedTimeSavedSec }
        )
        Log.d("PathAnalyzer", "analyzePaths: Final recommendations count = ${finalResults.size}")
        finalResults.forEach { rec ->
            Log.d("PathAnalyzer", "  -> Suggest '${rec.targetButtonConfig.label}' on '${rec.sourcePageName}' (saved: ${rec.estimatedTimeSavedSec}s, count: ${rec.occurrenceCount})")
        }
        return finalResults
    }

    private fun generateRecommendations(
        transitionCounts: Map<String, Map<String, List<Long>>>,
        targetButtonConfigs: Map<String, ButtonConfig>,
        pageMap: Map<String, Page>,
        pages: List<Page>,
        scanDelayMs: Long,
        defaultStartPageId: String?,
        minOccurrences: Int,
        degradationFactor: Double,
        minStepsSaved: Int
    ): List<ShortcutRecommendation> {
        val recommendations = mutableListOf<ShortcutRecommendation>()

        for ((sourcePageId, targetMap) in transitionCounts) {
            val sourcePage = pageMap[sourcePageId] ?: continue
            
            for ((buttonId, timestamps) in targetMap) {
                val buttonConfig = targetButtonConfigs[buttonId] ?: continue
                val count = timestamps.size

                // We only recommend if it occurs at least minOccurrences times
                if (count >= minOccurrences) {
                    val activeButtonsOnSource = sourcePage.buttonConfigs.count { it != null && it.isActive }
                    
                    // Apply dynamic start page limits and general capacity checks
                    val isStartPage = sourcePageId == defaultStartPageId
                    val maxVisibleSlots = sourcePage.rows * sourcePage.columns
                    val maxAllowedOnSource = if (isStartPage) {
                        maxOf(24, activeButtonsOnSource + 1).coerceAtMost(maxVisibleSlots)
                    } else {
                        maxOf(36, activeButtonsOnSource + 1).coerceAtMost(maxVisibleSlots)
                    }

                    if (activeButtonsOnSource >= maxAllowedOnSource) {
                        continue
                    }

                    // Check if adding this button requires grid expansion
                    val visibleActiveButtons = sourcePage.buttonConfigs.filterNotNull().count {
                        it.isActive && GridUtils.isVisibleInGrid(
                            sourcePage.buttonConfigs.indexOf(it), sourcePage.rows, sourcePage.columns
                        )
                    }
                    val requiresExpansion = visibleActiveButtons >= maxVisibleSlots

                    // Find which page this button belongs to
                    val targetPage = pages.find { page -> page.buttonConfigs.any { it?.id == buttonId } }
                    
                    val timeSavedSec = if (targetPage != null) {
                        val stepsToNavigate = sourcePage.buttonConfigs.find { 
                            it != null && it.isActive && it.buttonAction is NavigateToPageButtonAction && 
                            (it.buttonAction as NavigateToPageButtonAction).pageId == targetPage.id
                        }?.let { getScanSteps(sourcePage, it) } ?: 4
                        
                        val stepsToTarget = getScanSteps(targetPage, buttonConfig)
                        
                        val targetIndex = when (val result = GridUtils.determineTargetSlot(sourcePage, forceMove = true)) {
                            is GridUtils.SlotPlacementResult.Success -> result.targetIndex
                            is GridUtils.SlotPlacementResult.NeedsConfirmation -> result.targetIndex
                            else -> -1
                        }
                        val prospectiveIndex = if (targetIndex != -1) targetIndex else activeButtonsOnSource
                        val pattern = sourcePage.scanPattern?.takeIf { it.isNotBlank() && it != "default" } ?: "row_by_row"
                        
                        val stepsWithShortcut = if (pattern == "row_by_row") {
                            val rows = sourcePage.rows
                            val columns = sourcePage.columns
                            val r = prospectiveIndex / GridUtils.MAX_GRID_SIZE
                            val col = prospectiveIndex % GridUtils.MAX_GRID_SIZE
                            
                            val activeRows = (0 until rows).filter { rowIdx ->
                                rowIdx == r || (0 until columns).any { colIdx ->
                                    val gIdx = GridUtils.getGlobalIndex(rowIdx, colIdx)
                                    sourcePage.buttonConfigs.getOrNull(gIdx)?.let { it.isActive && GridUtils.isVisibleInGrid(gIdx, rows, columns) } == true
                                }
                            }
                            val rowsBefore = activeRows.indexOf(r).coerceAtLeast(0)
                            
                            val activeButtonsInRow = (0 until columns).mapNotNull { colIdx ->
                                val gIdx = GridUtils.getGlobalIndex(r, colIdx)
                                if (colIdx == col) {
                                    buttonConfig
                                } else {
                                    val btn = sourcePage.buttonConfigs.getOrNull(gIdx)
                                    if (btn != null && btn.isActive && GridUtils.isVisibleInGrid(gIdx, rows, columns)) btn else null
                                }
                            }
                            val buttonsBeforeInRow = activeButtonsInRow.indexOf(buttonConfig).coerceAtLeast(0)
                            (rowsBefore + 1) + (buttonsBeforeInRow + 1)
                        } else {
                            activeButtonsOnSource + 1
                        }
                        
                        // Collective degradation based on dynamic degradation factor
                        val degradation = (activeButtonsOnSource * degradationFactor).toInt()
                        
                        // Penalty if adding the button forces a row/col size increase
                        val expansionPenalty = if (requiresExpansion) 3 else 0
                        
                        val stepsSaved = ((stepsToNavigate + stepsToTarget) - (stepsWithShortcut + degradation + expansionPenalty))
                        
                        // Only recommend if we actually save positive scan steps
                        if (stepsSaved < minStepsSaved) continue
                        
                        max(5, (((stepsSaved * scanDelayMs) + 3000) / 1000).toInt())
                    } else {
                        val stepsSaved = 6 - (if (requiresExpansion) 3 else 0)
                        if (stepsSaved < minStepsSaved) continue
                        max(5, (((stepsSaved * scanDelayMs) + 3000) / 1000).toInt())
                    }

                    recommendations.add(
                        ShortcutRecommendation(
                            sourcePageId = sourcePageId,
                            sourcePageName = sourcePage.name,
                            targetButtonConfig = buttonConfig,
                            occurrenceCount = count,
                            estimatedTimeSavedSec = timeSavedSec
                        )
                    )
                }
            }
        }
        return recommendations
    }
}

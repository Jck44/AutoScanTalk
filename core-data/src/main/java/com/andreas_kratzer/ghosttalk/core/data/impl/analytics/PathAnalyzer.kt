package com.andreas_kratzer.ghosttalk.core.data.impl.analytics

import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository.ButtonUsageEvent
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
     * Analyzes historical chronological click events and generates shortcut suggestions.
     *
     * @param historyEvents Chronological list of past button presses.
     * @param pages All unfiltered pages in the current book (to find page names and verify slots/buttons).
     * @param scanDelayMs Current autoscan delay in milliseconds.
     */
    fun analyzePaths(
        historyEvents: List<ButtonUsageEvent>,
        pages: List<Page>,
        scanDelayMs: Long
    ): List<ShortcutRecommendation> {
        if (historyEvents.size < 3 || pages.isEmpty()) return emptyList()

        // 1. Sort chronologically (ascending)
        val sortedEvents = historyEvents.sortedBy { it.timestamp }

        // 2. Group into sessions based on 5-minute inactivity gap
        val sessions = mutableListOf<List<ButtonUsageEvent>>()
        var currentSession = mutableListOf<ButtonUsageEvent>()

        for (event in sortedEvents) {
            if (currentSession.isEmpty()) {
                currentSession.add(event)
            } else {
                val lastEvent = currentSession.last()
                if (event.timestamp - lastEvent.timestamp > 5 * 60 * 1000) {
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

        // 3. Process sessions to identify page-to-button transition bottlenecks
        // A bottleneck transition is: Page A -> ... -> Click on Button X on Page B
        // Criteria for a valid shortcut recommendation:
        // - Page A != Page B
        // - Button X is NOT already present on Page A
        // - Time taken is within the scan-based dynamic threshold
        val pageMap = pages.associateBy { it.id }
        
        // Map of SourcePageId -> Map of TargetButtonId -> List of transition timestamps
        val transitionCounts = mutableMapOf<String, MutableMap<String, MutableList<Long>>>()
        // Map to hold target ButtonConfigs for easy construction
        val targetButtonConfigs = mutableMapOf<String, ButtonConfig>()

        // Calculate dynamic threshold based on scan delay
        // 10 steps minimum navigation + 25s cognitive/reading/selection tolerance padding
        val maxIntervalMs = (10 * scanDelayMs) + 25000L

        for (session in sessions) {
            if (session.size < 2) continue

            // Keep track of the active context (thematic source page)
            var activeSourcePageId: String? = null
            var activeSourceTimestamp: Long = 0L

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

        // 4. Build recommendations list
        val recommendations = mutableListOf<ShortcutRecommendation>()

        for ((sourcePageId, targetMap) in transitionCounts) {
            val sourcePage = pageMap[sourcePageId] ?: continue
            
            for ((buttonId, timestamps) in targetMap) {
                val buttonConfig = targetButtonConfigs[buttonId] ?: continue
                val count = timestamps.size

                // We only recommend if it occurs at least 2 times (reliable pattern)
                if (count >= 2) {
                    // Estimated time saved = minimum 6 scan steps + 3 seconds cognitive transition
                    val timeSavedSec = max(5, (((6 * scanDelayMs) + 3000) / 1000).toInt())

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

        // Sort by occurrence count descending, then by time saved
        return recommendations.sortedWith(
            compareByDescending<ShortcutRecommendation> { it.occurrenceCount }
                .thenByDescending { it.estimatedTimeSavedSec }
        )
    }
}

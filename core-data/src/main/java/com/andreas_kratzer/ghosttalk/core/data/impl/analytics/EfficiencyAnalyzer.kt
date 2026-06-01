package com.andreas_kratzer.ghosttalk.core.data.impl.analytics

import com.andreas_kratzer.ghosttalk.core.model.ButtonEffortMetrics
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.util.GridUtils
import javax.inject.Inject
import javax.inject.Singleton

import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction

@Singleton
class EfficiencyAnalyzer @Inject constructor() {

    /**
     * Calculates the access effort and frustration metrics for all active buttons on a page.
     *
     * @param page The current page being viewed/edited.
     * @param allPages All unfiltered pages of the current book.
     * @param startPageId The ID of the start page.
     * @param clickCounts Map of button ID to their total click counts.
     * @param scanDelayMs The current scan interval in milliseconds.
     * @param defaultScanPattern The book-level default scan pattern (e.g., "linear" or "row_by_row").
     */
    fun calculatePageMetrics(
        page: Page,
        allPages: List<Page>,
        startPageId: String?,
        clickCounts: Map<String, Long>,
        scanDelayMs: Long,
        defaultScanPattern: String
    ): Map<String, ButtonEffortMetrics> {
        val buttons = page.buttonConfigs
        val rows = page.rows
        val columns = page.columns
        val pattern = page.scanPattern?.takeIf { it.isNotBlank() && it != "default" } ?: defaultScanPattern

        // Calculate global shortest paths first
        val shortestPaths = findShortestPathsFromStart(allPages, startPageId, scanDelayMs, defaultScanPattern)
        val pagePathData = shortestPaths[page.id]
        val pagePathTime = pagePathData?.first ?: 0
        val pagePathRoute = pagePathData?.second ?: emptyList()

        // Filter and get only active buttons that are visible in the current grid size
        val activeButtonsWithGlobalIndices = buttons.mapIndexedNotNull { index, buttonConfig ->
            if (buttonConfig != null &&
                buttonConfig.isActive &&
                GridUtils.isVisibleInGrid(index, rows = rows, columns = columns)
            ) {
                Pair(index, buttonConfig)
            } else null
        }

        if (activeButtonsWithGlobalIndices.isEmpty()) {
            return emptyMap()
        }

        val rawMetricsList = mutableListOf<RawMetrics>()

        if (pattern == "row_by_row") {
            // Find which rows contain at least one active visible button
            val activeRows = (0 until rows).filter { r ->
                (0 until columns).any { c ->
                    val globalIdx = GridUtils.getGlobalIndex(r, c)
                    buttons.getOrNull(globalIdx)?.let { it.isActive && GridUtils.isVisibleInGrid(globalIdx, rows, columns) } == true
                }
            }

            for ((globalIdx, buttonConfig) in activeButtonsWithGlobalIndices) {
                val r = globalIdx / GridUtils.MAX_GRID_SIZE

                // Number of active rows before this button's row
                val rowsBefore = activeRows.indexOf(r).coerceAtLeast(0)
                
                // Active buttons in this specific row before this column
                val activeButtonsInRow = (0 until columns).mapNotNull { col ->
                    val gIdx = GridUtils.getGlobalIndex(r, col)
                    val btn = buttons.getOrNull(gIdx)
                    if (btn != null && btn.isActive && GridUtils.isVisibleInGrid(gIdx, rows, columns)) btn else null
                }
                val buttonsBeforeInRow = activeButtonsInRow.indexOf(buttonConfig).coerceAtLeast(0)

                // Total steps = steps to select row + steps to select button in row
                val totalSteps = (rowsBefore + 1) + (buttonsBeforeInRow + 1)
                val accessTimeSec = ((totalSteps * scanDelayMs) / 1000.0).toInt().coerceAtLeast(1)
                val usageCount = clickCounts[buttonConfig.id]?.toInt() ?: 0

                rawMetricsList.add(RawMetrics(buttonConfig.id, accessTimeSec, usageCount))
            }
        } else {
            // Linear scanning
            for (i in activeButtonsWithGlobalIndices.indices) {
                val (_, buttonConfig) = activeButtonsWithGlobalIndices[i]
                
                val accessTimeSec = (((i + 1) * scanDelayMs) / 1000.0).toInt().coerceAtLeast(1)
                val usageCount = clickCounts[buttonConfig.id]?.toInt() ?: 0

                rawMetricsList.add(RawMetrics(buttonConfig.id, accessTimeSec, usageCount))
            }
        }

        // Calculate frustration indices and normalize
        val maxRawFrustration = rawMetricsList.maxOfOrNull { it.accessTimeSec * it.usageCount }?.toFloat() ?: 0f
        val maxClicks = rawMetricsList.maxOfOrNull { it.usageCount }?.toFloat() ?: 0f

        return rawMetricsList.associate { raw ->
            val rawFrustration = (raw.accessTimeSec * raw.usageCount).toFloat()
            val normalizedFrustration = if (maxRawFrustration > 0f) {
                rawFrustration / maxRawFrustration
            } else {
                0f
            }
            val heatmapIntensity = if (maxClicks > 0f) {
                raw.usageCount.toFloat() / maxClicks
            } else {
                0f
            }

            val buttonConfig = buttons.find { it?.id == raw.buttonId }
            val labelStr = buttonConfig?.label ?: "Kachel"
            val buttonRoute = pagePathRoute + "[$labelStr]"

            raw.buttonId to ButtonEffortMetrics(
                buttonId = raw.buttonId,
                accessTimeSec = raw.accessTimeSec,
                usageCount = raw.usageCount,
                frustrationIndex = normalizedFrustration,
                heatmapIntensity = heatmapIntensity,
                shortestPathTimeSec = pagePathTime + raw.accessTimeSec,
                shortestPathRoute = buttonRoute
            )
        }
    }

    private fun findShortestPathsFromStart(
        allPages: List<Page>,
        startPageId: String?,
        scanDelayMs: Long,
        defaultScanPattern: String
    ): Map<String, Pair<Int, List<String>>> {
        val startId = startPageId ?: allPages.firstOrNull()?.id ?: return emptyMap()
        val pageMap = allPages.associateBy { it.id }
        
        val queue = java.util.PriorityQueue<Pair<String, Pair<Int, List<String>>>>(compareBy { it.second.first })
        val startPageName = pageMap[startId]?.name ?: "Startseite"
        queue.add(startId to (0 to listOf(startPageName)))
        
        val shortestTimes = mutableMapOf<String, Pair<Int, List<String>>>()
        
        while (queue.isNotEmpty()) {
            val pollResult = queue.poll() ?: break
            val currentPageId = pollResult.first
            val (currentTime, currentRoute) = pollResult.second
            
            if (currentPageId in shortestTimes) continue
            shortestTimes[currentPageId] = pollResult.second
            
            val currentPage = pageMap[currentPageId] ?: continue
            val buttons = currentPage.buttonConfigs
            val rows = currentPage.rows
            val columns = currentPage.columns
            val pattern = currentPage.scanPattern?.takeIf { it.isNotBlank() && it != "default" } ?: defaultScanPattern
            
            val activeButtonsWithGlobalIndices = buttons.mapIndexedNotNull { index, buttonConfig ->
                if (buttonConfig != null &&
                    buttonConfig.isActive &&
                    GridUtils.isVisibleInGrid(index, rows = rows, columns = columns)
                ) {
                    Pair(index, buttonConfig)
                } else null
            }
            
            if (pattern == "row_by_row") {
                val activeRows = (0 until rows).filter { r ->
                    (0 until columns).any { c ->
                        val globalIdx = GridUtils.getGlobalIndex(r, c)
                        buttons.getOrNull(globalIdx)?.let { it.isActive && GridUtils.isVisibleInGrid(globalIdx, rows, columns) } == true
                    }
                }
                
                for ((globalIdx, buttonConfig) in activeButtonsWithGlobalIndices) {
                    val action = buttonConfig.buttonAction
                    if (action is NavigateToPageButtonAction) {
                        val targetId = action.pageId
                        if (targetId.isNotBlank() && targetId !in shortestTimes) {
                            val r = globalIdx / GridUtils.MAX_GRID_SIZE
                            val rowsBefore = activeRows.indexOf(r).coerceAtLeast(0)
                            val activeButtonsInRow = (0 until columns).mapNotNull { col ->
                                val gIdx = GridUtils.getGlobalIndex(r, col)
                                val btn = buttons.getOrNull(gIdx)
                                if (btn != null && btn.isActive && GridUtils.isVisibleInGrid(gIdx, rows, columns)) btn else null
                            }
                            val buttonsBeforeInRow = activeButtonsInRow.indexOf(buttonConfig).coerceAtLeast(0)
                            val totalSteps = (rowsBefore + 1) + (buttonsBeforeInRow + 1)
                            val edgeTimeSec = ((totalSteps * scanDelayMs) / 1000.0).toInt().coerceAtLeast(1)
                            
                            val targetPageName = pageMap[targetId]?.name ?: "Unbekannt"
                            val nextTime = currentTime + edgeTimeSec
                            val nextRoute = currentRoute + targetPageName
                            queue.add(Pair(targetId, Pair(nextTime, nextRoute)))
                        }
                    }
                }
            } else {
                for (i in activeButtonsWithGlobalIndices.indices) {
                    val (_, buttonConfig) = activeButtonsWithGlobalIndices[i]
                    val action = buttonConfig.buttonAction
                    if (action is NavigateToPageButtonAction) {
                        val targetId = action.pageId
                        if (targetId.isNotBlank() && targetId !in shortestTimes) {
                            val edgeTimeSec = (((i + 1) * scanDelayMs) / 1000.0).toInt().coerceAtLeast(1)
                            val targetPageName = pageMap[targetId]?.name ?: "Unbekannt"
                            val nextTime = currentTime + edgeTimeSec
                            val nextRoute = currentRoute + targetPageName
                            queue.add(Pair(targetId, Pair(nextTime, nextRoute)))
                        }
                    }
                }
            }
        }
        
        return shortestTimes
    }

    private data class RawMetrics(
        val buttonId: String,
        val accessTimeSec: Int,
        val usageCount: Int
    )
}

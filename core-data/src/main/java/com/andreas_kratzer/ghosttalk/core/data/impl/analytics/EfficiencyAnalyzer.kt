package com.andreas_kratzer.ghosttalk.core.data.impl.analytics

import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ButtonEffortMetrics
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.util.GridUtils
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EfficiencyAnalyzer @Inject constructor() {

    /**
     * Calculates the access effort and frustration metrics for all active buttons on a page.
     *
     * @param page The current page being viewed/edited.
     * @param clickCounts Map of button ID to their total click counts.
     * @param scanDelayMs The current scan interval in milliseconds.
     * @param defaultScanPattern The book-level default scan pattern (e.g., "linear" or "row_by_row").
     */
    fun calculatePageMetrics(
        page: Page,
        clickCounts: Map<String, Long>,
        scanDelayMs: Long,
        defaultScanPattern: String
    ): Map<String, ButtonEffortMetrics> {
        val buttons = page.buttonConfigs
        val rows = page.rows
        val columns = page.columns
        val pattern = page.scanPattern?.takeIf { it.isNotBlank() && it != "default" } ?: defaultScanPattern

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
                val c = globalIdx % GridUtils.MAX_GRID_SIZE

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

            raw.buttonId to ButtonEffortMetrics(
                buttonId = raw.buttonId,
                accessTimeSec = raw.accessTimeSec,
                usageCount = raw.usageCount,
                frustrationIndex = normalizedFrustration,
                heatmapIntensity = heatmapIntensity
            )
        }
    }

    private data class RawMetrics(
        val buttonId: String,
        val accessTimeSec: Int,
        val usageCount: Int
    )
}

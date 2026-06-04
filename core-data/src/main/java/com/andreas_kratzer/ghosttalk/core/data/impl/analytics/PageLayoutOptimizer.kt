package com.andreas_kratzer.ghosttalk.core.data.impl.analytics

import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.util.GridUtils
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PageLayoutOptimizer @Inject constructor() {

    sealed interface LayoutOptimizationProposal {
        val pageId: String
        val pageName: String

        data class SplitPageProposal(
            override val pageId: String,
            override val pageName: String,
            val activeButtonsCount: Int,
            val currentAverageScanTimeSec: Double,
            val estimatedNewAverageScanTimeSec: Double
        ) : LayoutOptimizationProposal

        data class ChangeScanPatternProposal(
            override val pageId: String,
            override val pageName: String,
            val activeButtonsCount: Int,
            val currentAverageScanTimeSec: Double,
            val estimatedNewAverageScanTimeSec: Double,
            val targetScanPattern: String
        ) : LayoutOptimizationProposal
    }

    fun analyzePages(
        pages: List<Page>,
        defaultStartPageId: String?,
        scanDelayMs: Long,
        defaultScanPattern: String
    ): List<LayoutOptimizationProposal> {
        val proposals = mutableListOf<LayoutOptimizationProposal>()

        for (page in pages) {
            val isStartPage = page.id == defaultStartPageId
            val pattern = page.scanPattern?.takeIf { it.isNotBlank() && it != "default" } ?: defaultScanPattern

            // Calculate active buttons visible in grid
            val activeButtonsCount = page.buttonConfigs.filterNotNull().count {
                it.isActive && GridUtils.isVisibleInGrid(
                    page.buttonConfigs.indexOf(it), page.rows, page.columns
                )
            }

            if (activeButtonsCount == 0) continue

            // 1. Calculate current average scan steps
            val currentAverageSteps = if (pattern == "row_by_row") {
                calculateAverageRowColSteps(page)
            } else {
                (activeButtonsCount + 1) / 2.0
            }
            val currentAverageTimeSec = (currentAverageSteps * scanDelayMs) / 1000.0

            // 2. Row-by-Row conversion suggestion (if currently linear and N > 8)
            if (pattern == "linear" || (pattern == "default" && defaultScanPattern == "linear")) {
                if (activeButtonsCount > 8) {
                    val rowColSteps = calculateRowColStepsForSize(page.rows, page.columns, activeButtonsCount)
                    val newAverageTimeSec = (rowColSteps * scanDelayMs) / 1000.0
                    val timeSaved = currentAverageTimeSec - newAverageTimeSec

                    // Suggest pattern change if saving is >= 2.0 seconds/steps
                    if (timeSaved >= 2.0) {
                        proposals.add(
                            LayoutOptimizationProposal.ChangeScanPatternProposal(
                                pageId = page.id,
                                pageName = page.name,
                                activeButtonsCount = activeButtonsCount,
                                currentAverageScanTimeSec = currentAverageTimeSec,
                                estimatedNewAverageScanTimeSec = newAverageTimeSec,
                                targetScanPattern = "row_by_row"
                            )
                        )
                    }
                }
            }

            // 3. Split Page suggestion
            val splitThresholdSec = 8.0
            val maxCountLimit = if (isStartPage) {
                // Dynamically scale max buttons based on scan delay
                ((12000L / maxOf(500L, scanDelayMs)).toInt()).coerceIn(6, 12)
            } else {
                16
            }

            if (currentAverageTimeSec > splitThresholdSec || activeButtonsCount > maxCountLimit) {
                val categoriesCount = 3
                val parentAverageSteps = (categoriesCount + 1) / 2.0
                val subpageButtonsCount = Math.ceil(activeButtonsCount.toDouble() / categoriesCount).toInt()
                val subpageAverageSteps = (subpageButtonsCount + 1) / 2.0

                // Add transition delay penalty (2.0s)
                val transitionPenaltySec = 2.0
                val estimatedNewAverageTimeSec = ((parentAverageSteps + subpageAverageSteps) * scanDelayMs) / 1000.0 + transitionPenaltySec

                if (estimatedNewAverageTimeSec < currentAverageTimeSec || activeButtonsCount > maxCountLimit) {
                    proposals.add(
                        LayoutOptimizationProposal.SplitPageProposal(
                            pageId = page.id,
                            pageName = page.name,
                            activeButtonsCount = activeButtonsCount,
                            currentAverageScanTimeSec = currentAverageTimeSec,
                            estimatedNewAverageScanTimeSec = estimatedNewAverageTimeSec
                        )
                    )
                }
            }
        }

        return proposals
    }

    private fun calculateAverageRowColSteps(page: Page): Double {
        val rows = page.rows
        val columns = page.columns
        val buttons = page.buttonConfigs

        val activeRows = (0 until rows).filter { r ->
            (0 until columns).any { c ->
                val gIdx = GridUtils.getGlobalIndex(r, c)
                buttons.getOrNull(gIdx)?.let { it.isActive && GridUtils.isVisibleInGrid(gIdx, rows, columns) } == true
            }
        }

        if (activeRows.isEmpty()) return 1.0

        val steps = mutableListOf<Int>()
        for (r in activeRows) {
            val activeButtonsInRow = (0 until columns).mapNotNull { col ->
                val gIdx = GridUtils.getGlobalIndex(r, col)
                val btn = buttons.getOrNull(gIdx)
                if (btn != null && btn.isActive && GridUtils.isVisibleInGrid(gIdx, rows, columns)) btn else null
            }
            val rowsBefore = activeRows.indexOf(r)
            for (colIdx in activeButtonsInRow.indices) {
                steps.add((rowsBefore + 1) + (colIdx + 1))
            }
        }

        return if (steps.isEmpty()) 1.0 else steps.average()
    }

    private fun calculateRowColStepsForSize(rows: Int, columns: Int, activeButtonsCount: Int): Double {
        val activeRows = Math.min(rows, Math.ceil(activeButtonsCount.toDouble() / columns).toInt())
        val avgButtonsPerRow = activeButtonsCount.toDouble() / activeRows

        val avgRowSelectSteps = (activeRows + 1) / 2.0
        val avgColSelectSteps = (avgButtonsPerRow + 1) / 2.0
        return avgRowSelectSteps + avgColSelectSteps
    }
}

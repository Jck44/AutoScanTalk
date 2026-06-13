package com.andreas_kratzer.ghosttalk.core.util

import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.Page

object GridUtils {
    sealed interface SlotPlacementResult {
        object TargetFull : SlotPlacementResult
        data class NeedsConfirmation(
            val targetIndex: Int,
            val requiredRows: Int,
            val requiredCols: Int
        ) : SlotPlacementResult
        data class Success(
            val targetIndex: Int,
            val requiredRows: Int,
            val requiredCols: Int
        ) : SlotPlacementResult
    }

    /**
     * Determines the best free target slot for duplicating or moving a button to the target page.
     * Prioritizes slots within the visible area before falling back to the hidden area.
     */
    fun determineTargetSlot(toPage: Page, forceMove: Boolean): SlotPlacementResult {
        var targetIndex = toPage.buttonConfigs.indices.firstOrNull { i ->
            toPage.buttonConfigs[i] == null && isVisibleInGrid(i, toPage.rows, toPage.columns)
        } ?: -1

        // Fallback to first free slot in the entire page if no visible slot is free
        if (targetIndex == -1) {
            targetIndex = toPage.buttonConfigs.indexOfFirst { it == null }
        }

        if (targetIndex == -1) {
            return SlotPlacementResult.TargetFull
        }

        val reqRow = (targetIndex / MAX_GRID_SIZE) + 1
        val reqCol = (targetIndex % MAX_GRID_SIZE) + 1

        val needsRow = reqRow > toPage.rows
        val needsCol = reqCol > toPage.columns

        return if ((needsRow || needsCol) && !forceMove) {
            SlotPlacementResult.NeedsConfirmation(
                targetIndex = targetIndex,
                requiredRows = reqRow.coerceAtLeast(toPage.rows),
                requiredCols = reqCol.coerceAtLeast(toPage.columns)
            )
        } else {
            SlotPlacementResult.Success(
                targetIndex = targetIndex,
                requiredRows = reqRow.coerceAtLeast(toPage.rows),
                requiredCols = reqCol.coerceAtLeast(toPage.columns)
            )
        }
    }
    const val MAX_GRID_SIZE = 7
    const val TOTAL_SLOTS = MAX_GRID_SIZE * MAX_GRID_SIZE

    /**
     * Maps 2D grid coordinates to a 1D list index (assuming 7-wide persistent grid).
     */
    fun getGlobalIndex(row: Int, column: Int): Int {
        return row * MAX_GRID_SIZE + column
    }

    /**
     * Maps a local index (from a smaller grid like 4x4) to the global 7x7 index.
     */
    fun localToGlobalIndex(localIndex: Int, sourceColumns: Int): Int {
        val r = localIndex / sourceColumns
        val c = localIndex % sourceColumns
        return getGlobalIndex(r, c)
    }

    /**
     * Maps a global 7x7 index back to a local index for a specific grid size.
     */
    fun globalToLocalIndex(globalIndex: Int, targetColumns: Int): Int {
        val r = globalIndex / MAX_GRID_SIZE
        val c = globalIndex % MAX_GRID_SIZE
        return r * targetColumns + c
    }

    /**
     * Checks if a global index is within the visible "window" of the current grid size.
     */
    fun isVisibleInGrid(globalIndex: Int, rows: Int, columns: Int): Boolean {
        val r = globalIndex / MAX_GRID_SIZE
        val c = globalIndex % MAX_GRID_SIZE
        return r < rows && c < columns
    }

    /**
     * Migration helper to convert a 6x6 mapped button list to a 7x7 mapped list.
     */
    fun migrateFrom6To7(oldConfigs: List<ButtonConfig?>): List<ButtonConfig?> {
        val newConfigs = MutableList<ButtonConfig?>(TOTAL_SLOTS) { null }
        val now = System.currentTimeMillis()
        oldConfigs.forEachIndexed { oldIndex, config ->
            if (config != null) {
                val r = oldIndex / 6
                val c = oldIndex % 6
                if (r < MAX_GRID_SIZE) {
                    val newIndex = getGlobalIndex(r, c)
                    newConfigs[newIndex] = config.copy(updatedAt = now)
                }
            }
        }
        return newConfigs
    }

    /**
     * Ensures the buttonConfigs list matches exactly 49 slots (7x7).
     * If sourceRows/sourceColumns are provided, it treats the input list as a spatially
     * packed grid and maps it into the 7x7 storage.
     */
    fun adjustButtonConfigs(
        configs: List<ButtonConfig?>,
        sourceRows: Int? = null,
        sourceColumns: Int? = null
    ): List<ButtonConfig?> {
        // If it's already 49, just return it (or take it to be safe)
        if (configs.size == TOTAL_SLOTS && sourceColumns == null) {
            return configs.take(TOTAL_SLOTS)
        }

        // If it's the old 36-slot size, migrate it
        if (configs.size == 36 && sourceColumns == null) {
            return migrateFrom6To7(configs)
        }
        
        val newConfigs = MutableList<ButtonConfig?>(TOTAL_SLOTS) { null }
        val now = System.currentTimeMillis()
        
        if (sourceColumns != null && sourceRows != null && configs.size != TOTAL_SLOTS) {
            // Spatially map existing buttons based on their original grid positions
            // This is used when creating a page from a template that might NOT be 7x7 yet
            configs.forEachIndexed { index, config ->
                if (config != null && index < sourceRows * sourceColumns) {
                    val globalIdx = localToGlobalIndex(index, sourceColumns)
                    if (globalIdx < TOTAL_SLOTS) {
                        newConfigs[globalIdx] = config.copy(updatedAt = now)
                    }
                }
            }
        } else {
            // Linear copy if no dimensions or already 7x7 logic
            configs.forEachIndexed { index, config ->
                if (index < TOTAL_SLOTS) {
                    newConfigs[index] = config
                }
            }
        }
        
        return newConfigs
    }
}

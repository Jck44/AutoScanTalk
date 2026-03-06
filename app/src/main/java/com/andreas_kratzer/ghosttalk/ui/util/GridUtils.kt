package com.andreas_kratzer.ghosttalk.ui.util

import com.andreas_kratzer.ghosttalk.model.ButtonConfig

object GridUtils {
    const val MAX_GRID_SIZE = 6
    const val TOTAL_SLOTS = MAX_GRID_SIZE * MAX_GRID_SIZE

    /**
     * Maps 2D grid coordinates to a 1D list index (assuming 6-wide persistent grid).
     */
    fun getGlobalIndex(row: Int, column: Int): Int {
        return row * MAX_GRID_SIZE + column
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
     * Ensures the buttonConfigs list matches exactly 36 slots (6x6).
     * This preserves button positions regardless of the current visible grid size.
     */
    fun adjustButtonConfigs(
        configs: List<ButtonConfig?>,
        rows: Int = MAX_GRID_SIZE,
        columns: Int = MAX_GRID_SIZE
    ): List<ButtonConfig?> {
        val mutableConfigs = configs.toMutableList()
        
        // Ensure minimum 36 slots
        while (mutableConfigs.size < TOTAL_SLOTS) {
            mutableConfigs.add(null)
        }
        
        // If it was somehow larger, truncate to 36
        if (mutableConfigs.size > TOTAL_SLOTS) {
            return mutableConfigs.take(TOTAL_SLOTS)
        }
        
        return mutableConfigs
    }
}

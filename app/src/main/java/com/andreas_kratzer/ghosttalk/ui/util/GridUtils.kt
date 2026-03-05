package com.andreas_kratzer.ghosttalk.ui.util

import com.andreas_kratzer.ghosttalk.model.ButtonConfig

object GridUtils {
    /**
     * Ensures the buttonConfigs list matches the required grid size (rows * columns).
     * Pads with nulls if too short, truncates if too long.
     */
    fun adjustButtonConfigs(
        configs: List<ButtonConfig?>,
        rows: Int,
        columns: Int
    ): List<ButtonConfig?> {
        val totalSlots = rows * columns
        val mutableConfigs = configs.toMutableList()
        
        while (mutableConfigs.size < totalSlots) {
            mutableConfigs.add(null)
        }
        
        if (mutableConfigs.size > totalSlots) {
            return mutableConfigs.take(totalSlots)
        }
        
        return mutableConfigs
    }
}

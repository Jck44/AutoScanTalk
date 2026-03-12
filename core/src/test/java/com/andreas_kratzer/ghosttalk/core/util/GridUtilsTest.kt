package com.andreas_kratzer.ghosttalk.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class GridUtilsTest {

    @Test
    fun `GridUtils regression - should preserve absolute spatial positions across different grid window sizes`() {
        val globalIndex7x7 = GridUtils.getGlobalIndex(row = 1, column = 1) // Row 1, Col 1 -> Index 8 (1*7 + 1)
        
        // In a 2x2 grid, Row 1, Col 1 is local index 3
        val localIndexIn2x2 = GridUtils.globalToLocalIndex(globalIndex7x7, targetColumns = 2)
        assertEquals(3, localIndexIn2x2)

        // Mapping back from local 3 in a 2x2 grid should yield global 8
        val backToGlobal = GridUtils.localToGlobalIndex(localIndexIn2x2, sourceColumns = 2)
        assertEquals(8, backToGlobal)
        
        // In a 4x4 grid, Row 1, Col 1 is local index 5
        val localIndexIn4x4 = GridUtils.globalToLocalIndex(globalIndex7x7, targetColumns = 4)
        assertEquals(5, localIndexIn4x4)
        
        // Mapping back from local 5 in a 4x4 grid should yield global 8
        val backToGlobal4x4 = GridUtils.localToGlobalIndex(localIndexIn4x4, sourceColumns = 4)
        assertEquals(8, backToGlobal4x4)
    }

    @Test
    fun `migrateFrom6To7 preserves spatial positions`() {
        val oldConfigs = MutableList<com.andreas_kratzer.ghosttalk.core.model.ButtonConfig?>(36) { null }
        // Set button at (1, 1) in 6x6 grid. Linear index = 1*6 + 1 = 7
        val testButton = com.andreas_kratzer.ghosttalk.core.model.ButtonConfig(id = "1", label = "Test", spokenText = "", buttonAction = com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction(), auditoryCue = null)
        oldConfigs[7] = testButton
        
        val newConfigs = GridUtils.migrateFrom6To7(oldConfigs)
        
        // In 7x7 grid, (1, 1) should be at index 1*7 + 1 = 8
        assertEquals(49, newConfigs.size)
        assertEquals(testButton, newConfigs[8])
        org.junit.Assert.assertNull(newConfigs[7]) // Linear index 7 in 7x7 is (1, 0)
    }

    @Test
    fun `adjustButtonConfigs maps 4x4 spatially into 7x7`() {
        val configs = MutableList<com.andreas_kratzer.ghosttalk.core.model.ButtonConfig?>(16) { null }
        // Button at (1, 1) in 4x4. Linear index = 1*4 + 1 = 5
        val testButton = com.andreas_kratzer.ghosttalk.core.model.ButtonConfig(id = "1", label = "Test", spokenText = "", buttonAction = com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction(), auditoryCue = null)
        configs[5] = testButton
        
        val adjusted = GridUtils.adjustButtonConfigs(configs, sourceRows = 4, sourceColumns = 4)
        
        // In 7x7, (1, 1) is index 8
        assertEquals(49, adjusted.size)
        assertEquals(testButton, adjusted[8])
        org.junit.Assert.assertNull(adjusted[5]) // Index 5 in 7x7 is (0, 5)
    }

    @Test
    fun `adjustButtonConfigs triggers migration for 36 slots`() {
        val oldConfigs = List<com.andreas_kratzer.ghosttalk.core.model.ButtonConfig?>(36) { null }
        val adjusted = GridUtils.adjustButtonConfigs(oldConfigs)
        assertEquals(49, adjusted.size)
    }
}

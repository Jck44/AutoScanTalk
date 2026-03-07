package com.andreas_kratzer.ghosttalk.ui.util

import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.SpeakTextButtonAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GridUtilsMigrationTest {

    @Test
    fun testMigrationFrom6To7() {
        // Create a 6x6 mapping (size 36)
        val oldConfigs = MutableList<ButtonConfig?>(36) { null }
        
        // Button at (0, 5) -> index 5
        val btnRow0Col5 = ButtonConfig(id = "1", label = "R0C5", spokenText = "", buttonAction = SpeakTextButtonAction(), auditoryCue = null)
        oldConfigs[5] = btnRow0Col5
        
        // Button at (1, 0) -> index 6
        val btnRow1Col0 = ButtonConfig(id = "2", label = "R1C0", spokenText = "", buttonAction = SpeakTextButtonAction(), auditoryCue = null)
        oldConfigs[6] = btnRow1Col0
        
        // Button at (5, 5) -> index 35
        val btnRow5Col5 = ButtonConfig(id = "3", label = "R5C5", spokenText = "", buttonAction = SpeakTextButtonAction(), auditoryCue = null)
        oldConfigs[35] = btnRow5Col5
        
        val newConfigs = GridUtils.migrateFrom6To7(oldConfigs)
        
        assertEquals(49, newConfigs.size)
        
        // (0, 5) in 7x7 is index 5
        assertEquals("R0C5", newConfigs[5]?.label)
        
        // (1, 0) in 7x7 is index 7
        assertEquals("R1C0", newConfigs[7]?.label)
        assertNull(newConfigs[6]) // (0, 6) in 7x7 is empty
        
        // (5, 5) in 7x7 is index 5 * 7 + 5 = 40
        assertEquals("R5C5", newConfigs[40]?.label)
    }

    @Test
    fun `migrateFrom6To7 preserves spatial positions`() {
        val oldConfigs = MutableList<ButtonConfig?>(36) { null }
        // Set button at (1, 1) in 6x6 grid. Linear index = 1*6 + 1 = 7
        val testButton = ButtonConfig(id = "1", label = "Test", spokenText = "", buttonAction = SpeakTextButtonAction(), auditoryCue = null)
        oldConfigs[7] = testButton
        
        val newConfigs = GridUtils.migrateFrom6To7(oldConfigs)
        
        // In 7x7 grid, (1, 1) should be at index 1*7 + 1 = 8
        assertEquals(49, newConfigs.size)
        assertEquals(testButton, newConfigs[8])
        assertNull(newConfigs[7]) // Linear index 7 in 7x7 is (1, 0)
    }

    @Test
    fun `adjustButtonConfigs maps 4x4 spatially into 7x7`() {
        val configs = MutableList<ButtonConfig?>(16) { null }
        // Button at (1, 1) in 4x4. Linear index = 1*4 + 1 = 5
        val testButton = ButtonConfig(id = "1", label = "Test", spokenText = "", buttonAction = SpeakTextButtonAction(), auditoryCue = null)
        configs[5] = testButton
        
        val adjusted = GridUtils.adjustButtonConfigs(configs, sourceRows = 4, sourceColumns = 4)
        
        // In 7x7, (1, 1) is index 8
        assertEquals(49, adjusted.size)
        assertEquals(testButton, adjusted[8])
        assertNull(adjusted[5]) // Index 5 in 7x7 is (0, 5)
    }

    @Test
    fun testAdjustButtonConfigsTriggersMigration() {
        val oldConfigs = List<ButtonConfig?>(36) { null }
        val adjusted = GridUtils.adjustButtonConfigs(oldConfigs)
        assertEquals(49, adjusted.size)
    }
}

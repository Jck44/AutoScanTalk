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
}

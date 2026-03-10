package com.andreas_kratzer.ghosttalk.ui.pages.sections

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.ui.pages.GridButton
import com.andreas_kratzer.ghosttalk.ui.pages.PageViewModel
import com.andreas_kratzer.ghosttalk.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.ui.util.GridUtils

@Composable
fun ButtonGrid(
    page: Page,
    focusedButtonIndex: Int?,
    focusedRowIndex: Int?,
    smartPredictions: List<String>?,
    pageViewModel: PageViewModel
) {
    val dimensions = LocalDimensions.current
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.TopCenter
    ) {
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        
        // Calculate available space from constraints
        val availableWidth = maxWidth - (dimensions.paddingMedium * 2)
        val availableHeight = maxHeight - (dimensions.paddingMedium * 2)
        
        // Calculate size to fit columns
        val buttonWidthToFit = (availableWidth - (dimensions.gridSpacing * (page.columns - 1))) / page.columns
        // Calculate size to fit rows
        val buttonHeightToFit = (availableHeight - (dimensions.gridSpacing * (page.rows - 1))) / page.rows
        
        // Optimal size is the minimum of both to ensure it fits in the available area
        // We cap the maximum size (e.g. 140dp) so small grids don't over-expand
        val maxButtonSize = 140.dp
        val optimalSize = minOf(buttonWidthToFit, buttonHeightToFit).coerceIn(dimensions.minButtonWidth, maxButtonSize)
        
        val totalWidth = (optimalSize * page.columns) + (dimensions.gridSpacing * (page.columns - 1))
        val totalHeight = (optimalSize * page.rows) + (dimensions.gridSpacing * (page.rows - 1))

        LazyVerticalGrid(
            columns = GridCells.Fixed(page.columns),
            modifier = Modifier
                .width(totalWidth)
                .height(totalHeight),
            contentPadding = PaddingValues(dimensions.paddingMedium),
            verticalArrangement = Arrangement.spacedBy(dimensions.gridSpacing),
            horizontalArrangement = Arrangement.spacedBy(dimensions.gridSpacing),
            userScrollEnabled = false // Should fit, so no scrolling needed within the grid itself
        ) {
            val rows = page.rows
            val cols = page.columns
            val totalVisible = rows * cols
            
            items(
                count = totalVisible,
                key = { visibleIndex -> 
                    val r = visibleIndex / cols
                    val c = visibleIndex % cols
                    "${page.id}_${GridUtils.getGlobalIndex(r, c)}"
                }
            ) { visibleIndex ->
                val r = visibleIndex / cols
                val c = visibleIndex % cols
                
                // Use derivedStateOf or remember for these values to avoid unnecessary recompositions
                val globalIndex = androidx.compose.runtime.remember(r, c) { 
                    GridUtils.getGlobalIndex(r, c) 
                }
                val buttonConfig = androidx.compose.runtime.remember(page.buttonConfigs, globalIndex) {
                    page.buttonConfigs.getOrNull(globalIndex)
                }

                val isFocused = focusedButtonIndex == globalIndex
                val isRowFocused = focusedRowIndex != null && r == focusedRowIndex

                val isVisible = androidx.compose.runtime.remember(buttonConfig, pageViewModel.featureGuard) {
                    buttonConfig != null && pageViewModel.featureGuard.isButtonVisible(buttonConfig)
                }

                if (buttonConfig != null && buttonConfig.isActive && isVisible) {
                    GridButton(
                        buttonConfig = buttonConfig,
                        isFocused = isFocused,
                        isRowFocused = isRowFocused,
                        isEditorMode = false,
                        onClick = { pageViewModel.activateButtonAtIndex(globalIndex) }
                    )
                } else {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

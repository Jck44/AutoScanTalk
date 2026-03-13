package com.andreas_kratzer.ghosttalk.ui.pages.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.ui.pages.GridButton
import com.andreas_kratzer.ghosttalk.ui.pages.PageViewModel
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.ui.util.GridUtils

@Composable
fun ButtonGrid(
    page: Page,
    focusedButtonIndex: Int?,
    focusedRowIndex: Int?,
    pageViewModel: PageViewModel
) {
    val dimensions = LocalDimensions.current
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        // Calculate available space from constraints with a small safety margin for rounding
        val availableWidth = maxWidth - (dimensions.paddingMedium * 2) - 1.dp
        val availableHeight = maxHeight - (dimensions.paddingMedium * 2) - 1.dp
        
        // Calculate size to fit columns and rows independently
        val buttonWidthToFit = (availableWidth - (dimensions.gridSpacing * (page.columns - 1))) / page.columns
        val buttonHeightToFit = (availableHeight - (dimensions.gridSpacing * (page.rows - 1))) / page.rows
        
        // Use a larger max size for tablets (180dp)
        val maxButtonSize = 180.dp
        
        // Calculate optimal width and height
        var optimalWidth = buttonWidthToFit.coerceIn(dimensions.minButtonWidth, maxButtonSize)
        var optimalHeight = buttonHeightToFit.coerceIn(dimensions.minButtonWidth, maxButtonSize)
        
        // Cap aspect ratio to prevent extreme stretching (max 1.5:1 or 1:1.5)
        val maxRatio = 1.5f
        if (optimalWidth > optimalHeight * maxRatio) {
            optimalWidth = optimalHeight * maxRatio
        } else if (optimalHeight > optimalWidth * maxRatio) {
            optimalHeight = optimalWidth * maxRatio
        }
        
        // Total size must include the contentPadding of the LazyVerticalGrid
        val totalWidth = (optimalWidth * page.columns) + (dimensions.gridSpacing * (page.columns - 1)) + (dimensions.paddingMedium * 2)
        val totalHeight = (optimalHeight * page.rows) + (dimensions.gridSpacing * (page.rows - 1)) + (dimensions.paddingMedium * 2)

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
                        onClick = { pageViewModel.activateButtonAtIndex(globalIndex) },
                        modifier = Modifier.width(optimalWidth).height(optimalHeight)
                    )
                } else {
                    androidx.compose.foundation.layout.Spacer(
                        modifier = Modifier.width(optimalWidth).height(optimalHeight)
                    )
                }
            }
        }
    }
}

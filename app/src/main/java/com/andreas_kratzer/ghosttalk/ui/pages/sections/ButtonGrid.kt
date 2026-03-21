package com.andreas_kratzer.ghosttalk.ui.pages.sections

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.ui.pages.GridButton
import com.andreas_kratzer.ghosttalk.ui.pages.PageViewModel
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.ui.util.GridUtils
import androidx.compose.ui.platform.testTag

@Composable
fun ButtonGrid(
    page: Page,
    focusedButtonIndex: Int?,
    focusedRowIndex: Int?,
    isScanning: Boolean,
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

        Column(
            modifier = Modifier
                .width(totalWidth)
                .height(totalHeight)
                .testTag(if (isScanning) "button_grid_scanning" else "button_grid_idle")
                .padding(dimensions.paddingMedium),
            verticalArrangement = Arrangement.spacedBy(dimensions.gridSpacing)
        ) {
            val rows = page.rows
            val cols = page.columns
            
            for (r in 0 until rows) {
                val isRowFocused = focusedRowIndex != null && r == focusedRowIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .run {
                            if (isRowFocused) {
                                border(
                                    width = 3.dp,
                                    color = MaterialTheme.colorScheme.secondary,
                                    shape = MaterialTheme.shapes.medium
                                ).padding(4.dp)
                            } else this
                        },
                    horizontalArrangement = Arrangement.spacedBy(dimensions.gridSpacing)
                ) {
                    for (c in 0 until cols) {
                        val globalIndex = GridUtils.getGlobalIndex(r, c)
                        val buttonConfig = page.buttonConfigs.getOrNull(globalIndex)

                        val isFocused = focusedButtonIndex == globalIndex

                        val isVisible = buttonConfig != null && pageViewModel.featureGuard.isButtonVisible(buttonConfig)

                        if (buttonConfig != null && buttonConfig.isActive && isVisible) {
                            GridButton(
                                buttonConfig = buttonConfig,
                                isFocused = isFocused,
                                isRowFocused = isRowFocused,
                                onClick = { pageViewModel.activateButtonAtIndex(globalIndex) },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        } else {
                            Spacer(
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }
    }
}

package com.andreas_kratzer.ghosttalk.ui.pages.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.core.util.GridUtils
import com.andreas_kratzer.ghosttalk.ui.pages.GridButton

@Composable
fun ButtonGrid(
    page: Page,
    focusedButtonIndex: Int?,
    focusedRowIndex: Int?,
    isScanning: Boolean,
    isButtonVisible: (ButtonConfig) -> Boolean,
    onButtonClick: (Int) -> Unit,
    staticRowPage: Page? = null
) {
    val dimensions = LocalDimensions.current
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        // Calculate available space from constraints with a small safety margin for rounding
        val availableWidth = maxWidth - (dimensions.paddingMedium * 2) - 1.dp
        val availableHeight = maxHeight - (dimensions.paddingMedium * 2) - 1.dp
        
        val visualRows = page.rows + (if (staticRowPage != null) 1 else 0)

        // Calculate size to fit columns and rows independently
        val buttonWidthToFit = (availableWidth - (dimensions.gridSpacing * (page.columns - 1))) / page.columns
        val buttonHeightToFit = (availableHeight - (dimensions.gridSpacing * (visualRows - 1))) / visualRows
        
        // Use a larger max size for tablets (180dp)
        val maxButtonSize = 180.dp
        
        // Calculate optimal width and height
        var optimalWidth = buttonWidthToFit.coerceIn(dimensions.minButtonWidth, maxButtonSize)
        
        // On phones, we enforce a minimum height to ensure readability, even if it requires scrolling.
        val minButtonHeight = if (dimensions.isTablet) dimensions.minButtonWidth else 45.dp
        val maxButtonHeight = if (dimensions.isTablet) 180.dp else 120.dp
        var optimalHeight = buttonHeightToFit.coerceIn(minButtonHeight, maxButtonHeight)
        
        // Cap aspect ratio to prevent extreme stretching (max 4.0:1)
        val maxRatio = 4.0f
        if (optimalWidth > optimalHeight * maxRatio) {
            optimalWidth = optimalHeight * maxRatio
        } else if (optimalHeight > optimalWidth * maxRatio) {
            optimalHeight = optimalWidth * maxRatio
        }
        
        // Total size must include the contentPadding of the LazyVerticalGrid
        val totalWidth = (optimalWidth * page.columns) + (dimensions.gridSpacing * (page.columns - 1)) + (dimensions.paddingMedium * 2)
        
        // Static row requires extra height for background padding and spacing/divider
        val staticRowExtraHeight = if (staticRowPage != null) {
            (dimensions.paddingSmall * 2) + 1.dp + dimensions.gridSpacing
        } else {
            0.dp
        }
        val totalHeight = (optimalHeight * visualRows) + (dimensions.gridSpacing * (visualRows - 1)) + (dimensions.paddingMedium * 2) + staticRowExtraHeight

        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .width(totalWidth)
                .height(totalHeight.coerceAtMost(this@BoxWithConstraints.maxHeight))
                .run {
                    if (!dimensions.isTablet && totalHeight > this@BoxWithConstraints.maxHeight) {
                        verticalScroll(scrollState)
                    } else this
                }
                .testTag(if (isScanning) "button_grid_scanning" else "button_grid_idle")
                .padding(dimensions.paddingMedium),
            verticalArrangement = Arrangement.spacedBy(dimensions.gridSpacing)
        ) {
            // Render static row if present
            if (staticRowPage != null) {
                val isRowFocused = focusedRowIndex != null && focusedRowIndex == 0
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .let { 
                            if (!dimensions.isTablet && totalHeight > this@BoxWithConstraints.maxHeight) {
                                it.height(optimalHeight + (dimensions.paddingSmall * 2) + 1.dp + dimensions.gridSpacing)
                            } else {
                                it.weight(1.05f)
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                shape = MaterialTheme.shapes.medium
                            )
                            .padding(dimensions.paddingSmall)
                            .run {
                                if (isRowFocused) {
                                    padding(2.dp)
                                    .border(
                                        width = 3.dp,
                                        color = MaterialTheme.colorScheme.secondary,
                                        shape = MaterialTheme.shapes.small
                                    ).padding(4.dp)
                                } else this
                            },
                        horizontalArrangement = Arrangement.spacedBy(dimensions.gridSpacing)
                    ) {
                        for (c in 0 until staticRowPage.columns) {
                            val globalIndex = GridUtils.getGlobalIndex(0, c)
                            val buttonConfig = staticRowPage.buttonConfigs.getOrNull(globalIndex)
                            val isFocused = focusedButtonIndex == globalIndex
                            val isVisible = buttonConfig != null && isButtonVisible(buttonConfig)
                            if (buttonConfig != null && buttonConfig.isActive && isVisible) {
                                GridButton(
                                    buttonConfig = buttonConfig,
                                    isFocused = isFocused,
                                    isRowFocused = isRowFocused,
                                    isEditorMode = false,
                                    onClick = { onButtonClick(globalIndex) },
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                            } else {
                                Spacer(
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(dimensions.gridSpacing))
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }

            // Render page rows
            val rows = page.rows
            val cols = page.columns
            val startRowIndexOffset = if (staticRowPage != null) 1 else 0
            val shiftOffset = if (staticRowPage != null) 49 else 0
            
            for (r in 0 until rows) {
                val visualRowIndex = r + startRowIndexOffset
                val isRowFocused = focusedRowIndex != null && visualRowIndex == focusedRowIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .let { 
                            if (!dimensions.isTablet && totalHeight > this@BoxWithConstraints.maxHeight) {
                                it.height(optimalHeight)
                            } else {
                                it.weight(1f)
                            }
                        }
                        .run {
                            if (isRowFocused) {
                                padding(2.dp)
                                .border(
                                    width = 3.dp,
                                    color = MaterialTheme.colorScheme.secondary,
                                    shape = MaterialTheme.shapes.small
                                ).padding(4.dp)
                            } else this
                        },
                    horizontalArrangement = Arrangement.spacedBy(dimensions.gridSpacing)
                ) {
                    for (c in 0 until cols) {
                        val globalIndex = GridUtils.getGlobalIndex(r, c)
                        val buttonConfig = page.buttonConfigs.getOrNull(globalIndex)

                        val isFocused = focusedButtonIndex == (shiftOffset + globalIndex)

                        val isVisible = buttonConfig != null && isButtonVisible(buttonConfig)

                        if (buttonConfig != null && buttonConfig.isActive && isVisible) {
                            GridButton(
                                buttonConfig = buttonConfig,
                                isFocused = isFocused,
                                isRowFocused = isRowFocused,
                                isEditorMode = false,
                                onClick = { onButtonClick(shiftOffset + globalIndex) },
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

package com.andreas_kratzer.ghosttalk.ui.pages.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.ui.pages.GridButton
import com.andreas_kratzer.ghosttalk.ui.pages.PageViewModel
import com.andreas_kratzer.ghosttalk.ui.theme.LocalDimensions

@Composable
fun ButtonGrid(
    page: Page,
    focusedButtonIndex: Int?,
    focusedRowIndex: Int?,
    smartPredictions: List<String>?,
    pageViewModel: PageViewModel
) {
    val dimensions = LocalDimensions.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(page.columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(dimensions.paddingMedium),
        verticalArrangement = Arrangement.spacedBy(dimensions.gridSpacing),
        horizontalArrangement = Arrangement.spacedBy(dimensions.gridSpacing)
    ) {
        val rows = page.rows
        val cols = page.columns
        val totalVisible = rows * cols
        
        items(totalVisible) { visibleIndex ->
            val r = visibleIndex / cols
            val globalIndex = com.andreas_kratzer.ghosttalk.ui.util.GridUtils.getGlobalIndex(r, visibleIndex % cols)
            val buttonConfig = page.buttonConfigs.getOrNull(globalIndex)

            val isFocused = globalIndex == focusedButtonIndex
            val isRowFocused = focusedRowIndex != null && r == focusedRowIndex

            val isVisible = buttonConfig != null && pageViewModel.featureGuard.isButtonVisible(buttonConfig)

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

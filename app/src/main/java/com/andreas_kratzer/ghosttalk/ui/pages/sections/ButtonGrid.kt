package com.andreas_kratzer.ghosttalk.ui.pages.sections

import android.content.res.Configuration
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
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
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(rememberScrollState()),
        contentAlignment = androidx.compose.ui.Alignment.TopCenter
    ) {
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val minButtonWidth = if( isLandscape) (dimensions.minButtonWidth.value * 1.8).dp else dimensions.minButtonWidth
        val totalMinWidth = minButtonWidth * page.columns + (dimensions.gridSpacing * (page.columns - 1))
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(page.columns),
            modifier = Modifier
                .widthIn(max = dimensions.baseMaxButtonWidth * page.columns)
                .width(totalMinWidth)
                .fillMaxHeight(),
            contentPadding = PaddingValues(dimensions.paddingMedium),
            verticalArrangement = Arrangement.spacedBy(dimensions.gridSpacing),
            horizontalArrangement = Arrangement.spacedBy(dimensions.gridSpacing)
        ) {
            val rows = page.rows
            val cols = page.columns
            val totalVisible = rows * cols
            
            items(totalVisible) { visibleIndex ->
                val r = visibleIndex / cols
                val globalIndex = GridUtils.getGlobalIndex(r, visibleIndex % cols)
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
}

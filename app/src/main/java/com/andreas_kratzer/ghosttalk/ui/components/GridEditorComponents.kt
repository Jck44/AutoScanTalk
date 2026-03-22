package com.andreas_kratzer.ghosttalk.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.model.GridItem
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.ui.util.GridEditorActions
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GridEditorControls(
    item: GridItem,
    actions: GridEditorActions,
    modifier: Modifier = Modifier
) {
    val dimensions = LocalDimensions.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    FlowRow(
        modifier = modifier.fillMaxWidth().padding(bottom = dimensions.paddingLarge),
        horizontalArrangement = Arrangement.spacedBy(dimensions.paddingExtraLarge),
        verticalArrangement = Arrangement.spacedBy(dimensions.paddingMedium),
        maxItemsInEachRow = 3
    ) {
        val controlModifier = Modifier.weight(1f).widthIn(min = 250.dp)

        var localRows by remember(item.rows) { mutableIntStateOf(item.rows) }
        var localCols by remember(item.columns) { mutableIntStateOf(item.columns) }

        LaunchedEffect(localRows, localCols) {
            if (localRows != item.rows || localCols != item.columns) {
                delay(50)
                actions.updateGridSettings(
                    itemId = item.id,
                    newName = item.name,
                    newScanPattern = item.scanPattern,
                    newRowNames = item.rowNames,
                    newRows = localRows,
                    newColumns = localCols
                )
            }
        }

        // Rows Slider
        Column(modifier = controlModifier) {
            val rowsLabel = stringResource(R.string.page_rows_field) + ": $localRows"
            Text(
                text = rowsLabel,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Slider(
                value = localRows.toFloat(),
                onValueChange = { newValue ->
                    localRows = Math.round(newValue)
                },
                valueRange = 1f..7f,
                steps = 5
            )
        }

        // Columns Slider
        Column(modifier = controlModifier) {
            val colsLabel = stringResource(R.string.page_cols_field) + ": $localCols"
            Text(
                text = colsLabel,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Slider(
                value = localCols.toFloat(),
                onValueChange = { newValue ->
                    localCols = Math.round(newValue)
                },
                valueRange = 1f..7f,
                steps = 5
            )
        }

        // Scan Pattern Dropdown
        var expandedPattern by remember { mutableStateOf(false) }
        val options = listOf(
            null to stringResource(R.string.page_pattern_default),
            "linear" to stringResource(R.string.settings_pattern_linear),
            "row_by_row" to stringResource(R.string.settings_pattern_row_by_row)
        )
        val currentPatternLabel = options.find { it.first == item.scanPattern }?.second ?: stringResource(R.string.page_pattern_default)

        ExposedDropdownMenuBox(
            expanded = expandedPattern,
            onExpandedChange = { expandedPattern = !expandedPattern },
            modifier = controlModifier
        ) {
            OutlinedTextField(
                value = currentPatternLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.page_scan_pattern_override)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPattern) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expandedPattern,
                onDismissRequest = { expandedPattern = false }
            ) {
                options.forEach { (pattern, label) ->
                    DropdownMenuItem(
                        text = { Text(label, style = MaterialTheme.typography.bodyLarge) },
                        onClick = {
                            actions.updateGridSettings(
                                itemId = item.id,
                                newName = item.name,
                                newScanPattern = pattern,
                                newRowNames = item.rowNames
                            )
                            focusManager.clearFocus()
                            expandedPattern = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
    }
}

data class GridSizeInfo(
    val totalWidth: Dp,
    val totalHeight: Dp,
    val optimalWidth: Dp,
    val optimalHeight: Dp,
    val rowHandleWidth: Dp,
    val heightOffsetPerRow: Dp
)

fun calculateGridSize(
    maxWidth: Dp,
    maxHeight: Dp,
    rows: Int,
    cols: Int,
    isRowByRow: Boolean,
    horizontalPadding: Dp,
    isTablet: Boolean,
    dimensions: com.andreas_kratzer.ghosttalk.core.ui.theme.Dimensions
): GridSizeInfo {
    // Precise width if row handles are present: 
    // icon box (48dp) + inner Row padding (8dp left + 8dp right = 16dp total horizontally)
    val rowHandleWidth = if (isRowByRow) 64.dp else 0.dp
    
    // Subtract a small safety margin (2dp) to prevent sub-pixel rounding issues
    val availableWidth = maxWidth - (horizontalPadding * 2) - 2.dp - rowHandleWidth
    val availableHeight = maxHeight - (dimensions.paddingMedium * 2) - 2.dp

    var buttonWidthToFit = (availableWidth - (dimensions.gridSpacing * (cols - 1))) / cols
    // When in row-by-row mode, each row has a Row wrapper with its own padding/border (approx 16dp total height offset per row)
    val heightOffsetPerRow = if (isRowByRow) 16.dp else 0.dp
    var buttonHeightToFit = ((availableHeight - (dimensions.gridSpacing * (rows - 1))) / rows) - heightOffsetPerRow

    val maxButtonSize = 180.dp
    
    // We allow the buttons to go below minButtonWidth if necessary to fit the screen width
    var optimalWidth = buttonWidthToFit.coerceAtMost(maxButtonSize)
    
    // On phones, we enforce a minimum height to ensure readability, even if it requires scrolling.
    // On tablets, we continue to fit the entire grid on the screen.
    val minEditorButtonHeight = if (isTablet) 0.dp else 72.dp
    var optimalHeight = buttonHeightToFit.coerceIn(minEditorButtonHeight, maxButtonSize)

    val maxRatio = 4.0f
    if (optimalWidth > optimalHeight * maxRatio) {
        optimalWidth = optimalHeight * maxRatio
    } else if (optimalHeight > optimalWidth * maxRatio) {
        optimalHeight = optimalWidth * maxRatio
    }

    // The container width and height must include the contentPadding and row handles
    val totalWidth = (optimalWidth * cols) + (dimensions.gridSpacing * (cols - 1)) + (horizontalPadding * 2) + rowHandleWidth
    val totalHeight = ((optimalHeight + heightOffsetPerRow) * rows) + (dimensions.gridSpacing * (rows - 1)) + (dimensions.paddingMedium * 2)

    return GridSizeInfo(
        totalWidth = totalWidth,
        totalHeight = totalHeight,
        optimalWidth = optimalWidth,
        optimalHeight = optimalHeight,
        rowHandleWidth = rowHandleWidth,
        heightOffsetPerRow = heightOffsetPerRow
    )
}

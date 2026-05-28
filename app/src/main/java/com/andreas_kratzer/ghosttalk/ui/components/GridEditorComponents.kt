package com.andreas_kratzer.ghosttalk.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.model.GridItem
import com.andreas_kratzer.ghosttalk.core.model.GridSettingsUpdate
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.ui.util.GridEditorActions
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GridEditorControls(
    item: GridItem,
    actions: GridEditorActions,
    modifier: Modifier = Modifier,
    forceVertical: Boolean = false
) {
    val dimensions = LocalDimensions.current
    
    if (forceVertical) {
        Column(
            modifier = modifier.fillMaxWidth().padding(horizontal = dimensions.paddingMedium),
            verticalArrangement = Arrangement.spacedBy(dimensions.paddingLarge)
        ) {
            GridEditorRowsControl(item, actions, Modifier.fillMaxWidth())
            GridEditorColsControl(item, actions, Modifier.fillMaxWidth())
            GridEditorPatternControl(item, actions, Modifier.fillMaxWidth())
        }
    } else {
        FlowRow(
            modifier = modifier.fillMaxWidth().padding(bottom = dimensions.paddingLarge),
            horizontalArrangement = Arrangement.spacedBy(dimensions.paddingExtraLarge),
            verticalArrangement = Arrangement.spacedBy(dimensions.paddingMedium),
            maxItemsInEachRow = 3
        ) {
            // Hier verzichten wir auf weight(), um Inkompatibilitäten in FlowRow zu vermeiden
            val itemModifier = Modifier.widthIn(min = 200.dp, max = 300.dp)
            GridEditorRowsControl(item, actions, itemModifier)
            GridEditorColsControl(item, actions, itemModifier)
            GridEditorPatternControl(item, actions, itemModifier)
        }
    }
}

@Composable
private fun GridEditorRowsControl(item: GridItem, actions: GridEditorActions, modifier: Modifier) {
    var localRows by remember(item.rows) { mutableIntStateOf(item.rows) }
    LaunchedEffect(localRows) {
        if (localRows != item.rows) {
            delay(50)
            actions.updateGridSettings(item.id, GridSettingsUpdate(rows = localRows))
        }
    }
    Column(modifier = modifier) {
        Text(
            text = "${stringResource(R.string.page_rows_field)}: $localRows",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Slider(
            value = localRows.toFloat(),
            onValueChange = { localRows = Math.round(it) },
            valueRange = 1f..7f,
            steps = 5
        )
    }
}

@Composable
private fun GridEditorColsControl(item: GridItem, actions: GridEditorActions, modifier: Modifier) {
    var localCols by remember(item.columns) { mutableIntStateOf(item.columns) }
    LaunchedEffect(localCols) {
        if (localCols != item.columns) {
            delay(50)
            actions.updateGridSettings(item.id, GridSettingsUpdate(columns = localCols))
        }
    }
    Column(modifier = modifier) {
        Text(
            text = "${stringResource(R.string.page_cols_field)}: $localCols",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Slider(
            value = localCols.toFloat(),
            onValueChange = { localCols = Math.round(it) },
            valueRange = 1f..7f,
            steps = 5
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GridEditorPatternControl(item: GridItem, actions: GridEditorActions, modifier: Modifier) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
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
        modifier = modifier
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
                            update = GridSettingsUpdate(
                                scanPattern = com.andreas_kratzer.ghosttalk.core.model.OptionalProperty(pattern)
                            )
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

@Composable
fun GridEditorSummaryBar(
    item: GridItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimensions = LocalDimensions.current
    val options = listOf(
        null to stringResource(R.string.page_pattern_default),
        "linear" to stringResource(R.string.settings_pattern_linear),
        "row_by_row" to stringResource(R.string.settings_pattern_row_by_row)
    )
    val currentPatternLabel = options.find { it.first == item.scanPattern }?.second ?: stringResource(R.string.page_pattern_default)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(bottom = dimensions.paddingMedium),
        horizontalArrangement = Arrangement.spacedBy(dimensions.paddingSmall, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AssistChip(
            onClick = onClick,
            label = { Text("${item.rows} ${stringResource(R.string.page_rows_field)}") },
            leadingIcon = { Icon(GhostTalkIcons.Sort, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
        AssistChip(
            onClick = onClick,
            label = { Text("${item.columns} ${stringResource(R.string.page_cols_field)}") },
            leadingIcon = { Icon(GhostTalkIcons.GridView, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
        AssistChip(
            onClick = onClick,
            label = { Text(currentPatternLabel) },
            leadingIcon = { Icon(GhostTalkIcons.Description, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
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
    val rowHandleWidth = if (isRowByRow) 64.dp else 0.dp
    val availableWidth = maxWidth - (horizontalPadding * 2) - 2.dp - rowHandleWidth
    val availableHeight = maxHeight - (horizontalPadding * 2) - 2.dp

    var buttonWidthToFit = (availableWidth - (dimensions.gridSpacing * (cols - 1))) / cols
    val heightOffsetPerRow = if (isRowByRow) 16.dp else 0.dp
    var buttonHeightToFit = ((availableHeight - (dimensions.gridSpacing * (rows - 1))) / rows) - heightOffsetPerRow

    val maxButtonSize = 180.dp
    var optimalWidth = buttonWidthToFit.coerceAtMost(maxButtonSize)
    
    val minEditorButtonHeight = if (isTablet) 0.dp else 45.dp
    val maxButtonHeight = if (isTablet) 180.dp else 120.dp
    var optimalHeight = buttonHeightToFit.coerceIn(minEditorButtonHeight, maxButtonHeight)

    val maxRatio = 4.0f
    if (optimalWidth > optimalHeight * maxRatio) {
        optimalWidth = optimalHeight * maxRatio
    } else if (optimalHeight > optimalWidth * maxRatio) {
        optimalHeight = optimalWidth * maxRatio
    }

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

package com.andreas_kratzer.ghosttalk.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.model.GridItem
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.model.PageTemplate
import com.andreas_kratzer.ghosttalk.ui.pages.ButtonConfigDialog
import com.andreas_kratzer.ghosttalk.ui.pages.GridButton
import com.andreas_kratzer.ghosttalk.ui.theme.GhosTTalkIcons
import com.andreas_kratzer.ghosttalk.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.ui.util.GridEditorActions
import com.andreas_kratzer.ghosttalk.ui.util.GridUtils
import kotlinx.coroutines.delay
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GridEditorContent(
    item: GridItem,
    actions: GridEditorActions,
    availablePages: List<Page>,
    templates: List<PageTemplate>,
    featureGuard: com.andreas_kratzer.ghosttalk.domain.settings.FeatureGuard,
    bookDefaultScanPattern: String?,
    paddingValues: PaddingValues,
    onEditPage: ((String) -> Unit)? = null
) {
    val dimensions = LocalDimensions.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val density = LocalDensity.current.density
    val focusManager = LocalFocusManager.current
    val isExecuting by actions.isExecuting.collectAsStateWithLifecycle()

    var selectedButtonIndex by remember { mutableStateOf<Int?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var editingRowIndex by remember { mutableStateOf<Int?>(null) }
    var showRowEditDialog by remember { mutableStateOf(false) }

    val gridState = rememberLazyGridState()
    val rowReorderState = rememberReorderableState()
    val buttonReorderState = rememberReorderableState()

    Column(
        modifier = Modifier
            .padding(paddingValues)
            .padding(if (isLandscape) dimensions.paddingMedium else dimensions.paddingLarge),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Adaptive Controls (Rows, Columns, Scan Pattern)
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = dimensions.paddingLarge),
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

        val rowDefaultLabelTemplate = stringResource(R.string.page_row_label)
        val effectiveScanPattern = item.scanPattern ?: bookDefaultScanPattern

        val rowTargetIndex = rowReorderState.findTargetIndexForGrid(gridState)
        val buttonTargetIndex = buttonReorderState.findTargetButtonIndex(gridState, item.columns, effectiveScanPattern == "row_by_row", density)

        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            val isRowByRow = effectiveScanPattern == "row_by_row"
            
            // Subtract extra width if row handles are present (approx 48dp handle + padding/borders)
            val rowHandleWidth = if (isRowByRow) 64.dp else 0.dp
            
            // Subtract a small safety margin (2dp) to prevent sub-pixel rounding issues causing scrollbars
            val availableWidth = maxWidth - (dimensions.paddingMedium * 2) - 2.dp - rowHandleWidth
            val availableHeight = maxHeight - (dimensions.paddingMedium * 2) - 2.dp

            val buttonWidthToFit = (availableWidth - (dimensions.gridSpacing * (item.columns - 1))) / item.columns
            // When in row-by-row mode, each row has a Row wrapper with its own padding/border (approx 16dp total height offset per row)
            val heightOffsetPerRow = if (isRowByRow) 16.dp else 0.dp
            val buttonHeightToFit = ((availableHeight - (dimensions.gridSpacing * (item.rows - 1))) / item.rows) - heightOffsetPerRow

            val maxButtonSize = 180.dp
            var optimalWidth = buttonWidthToFit.coerceIn(dimensions.minButtonWidth, maxButtonSize)
            var optimalHeight = buttonHeightToFit.coerceIn(dimensions.minButtonWidth, maxButtonSize)

            val maxRatio = 1.5f
            if (optimalWidth > optimalHeight * maxRatio) {
                optimalWidth = optimalHeight * maxRatio
            } else if (optimalHeight > optimalWidth * maxRatio) {
                optimalHeight = optimalWidth * maxRatio
            }

            // The container width and height must include the contentPadding and row handles
            val totalWidth = (optimalWidth * item.columns) + (dimensions.gridSpacing * (item.columns - 1)) + (dimensions.paddingMedium * 2) + rowHandleWidth
            val totalHeight = ((optimalHeight + heightOffsetPerRow) * item.rows) + (dimensions.gridSpacing * (item.rows - 1)) + (dimensions.paddingMedium * 2)

            LazyVerticalGrid(
                columns = GridCells.Fixed(item.columns),
                state = gridState,
                modifier = Modifier
                    .width(totalWidth)
                    .height(totalHeight),
                contentPadding = PaddingValues(dimensions.paddingMedium),
                verticalArrangement = Arrangement.spacedBy(dimensions.gridSpacing),
                horizontalArrangement = Arrangement.spacedBy(dimensions.gridSpacing)
            ) {
            val totalRows = item.rows
            val numCols = item.columns

            for (r in 0 until totalRows) {
                if (effectiveScanPattern == "row_by_row") {
                    item(span = { GridItemSpan(numCols) }) {
                        val isRowTarget = rowTargetIndex == r
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .reorderableItemVisuals(rowReorderState, r)
                                .background(
                                    if (isRowTarget) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    else Color.Transparent
                                )
                                .border(
                                    width = if (isRowTarget) 3.dp else 2.dp,
                                    color = if (isRowTarget) MaterialTheme.colorScheme.primary 
                                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    shape = MaterialTheme.shapes.medium
                                )
                                .height(IntrinsicSize.Min),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(48.dp)
                                    .fillMaxHeight()
                                    .dragHandle(
                                        state = rowReorderState,
                                        index = r,
                                        onDragEnd = { fromIdx ->
                                            if (fromIdx != null) {
                                                val to = rowReorderState.findTargetIndexForGrid(gridState)
                                                if (to != null && to != fromIdx) {
                                                    actions.moveRow(item.id, fromIdx, to)
                                                }
                                            }
                                        }
                                    )
                                    .clickable {
                                        editingRowIndex = r
                                        showRowEditDialog = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = GhosTTalkIcons.Edit,
                                    contentDescription = stringResource(R.string.page_editor_row_name_label),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(dimensions.gridSpacing)
                            ) {
                                for (c in 0 until numCols) {
                                    val globalIndex = GridUtils.getGlobalIndex(r, c)
                                    val buttonConfig = item.buttonConfigs.getOrNull(globalIndex)
                                    val isButtonTarget = buttonTargetIndex == globalIndex
                                    
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .reorderableItemVisuals(buttonReorderState, globalIndex)
                                            .dragHandle(
                                                state = buttonReorderState,
                                                index = globalIndex,
                                                onDragEnd = { fromIdx ->
                                                    if (fromIdx != null) {
                                                        val to = buttonReorderState.findTargetButtonIndex(gridState, numCols, true, density)
                                                        if (to != null && to != fromIdx) {
                                                            actions.moveButton(item.id, fromIdx, to)
                                                        }
                                                    }
                                                }
                                            )
                                            .background(
                                                if (isButtonTarget) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                                else Color.Transparent
                                            )
                                            .border(
                                                width = if (isButtonTarget) 2.dp else 0.dp,
                                                color = if (isButtonTarget) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                shape = MaterialTheme.shapes.medium
                                            )
                                    ) {
                                        GridButton(
                                            buttonConfig = buttonConfig,
                                            isFocused = false,
                                            isEditorMode = true,
                                            onClick = {
                                                selectedButtonIndex = globalIndex
                                                showDialog = true
                                            },
                                            modifier = Modifier.width(optimalWidth).height(optimalHeight)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    for (c in 0 until numCols) {
                        val globalIndex = GridUtils.getGlobalIndex(r, c)
                        item {
                            val isTarget = buttonTargetIndex == globalIndex
                            val buttonConfig = item.buttonConfigs.getOrNull(globalIndex)
                            Box(
                                modifier = Modifier
                                    .reorderableItemVisuals(buttonReorderState, globalIndex)
                                    .dragHandle(
                                        state = buttonReorderState,
                                        index = globalIndex,
                                        onDragEnd = { fromIdx ->
                                            if (fromIdx != null) {
                                                val to = buttonReorderState.findTargetButtonIndex(gridState, numCols, false, density)
                                                if (to != null && to != fromIdx) {
                                                    actions.moveButton(item.id, fromIdx, to)
                                                }
                                            }
                                        }
                                    )
                                    .background(
                                        if (isTarget) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                        else Color.Transparent
                                    )
                                    .border(
                                        width = if (isTarget) 3.dp else 0.dp,
                                        color = if (isTarget) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = MaterialTheme.shapes.medium
                                    )
                            ) {
                                GridButton(
                                    buttonConfig = buttonConfig,
                                    isFocused = false,
                                    isEditorMode = true,
                                    onClick = {
                                        selectedButtonIndex = globalIndex
                                        showDialog = true
                                    },
                                    modifier = Modifier.width(optimalWidth).height(optimalHeight)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val currentRowIndex = editingRowIndex
    if (showRowEditDialog && currentRowIndex != null) {
        RowEditDialog(
            initialName = item.rowNames.getOrNull(currentRowIndex) ?: rowDefaultLabelTemplate.format(currentRowIndex + 1),
                onDismiss = {
                    showRowEditDialog = false
                    editingRowIndex = null
                },
                onSave = { newName ->
                    actions.updateRowName(item.id, currentRowIndex, newName)
                    showRowEditDialog = false
                    editingRowIndex = null
                }
            )
        }

    val currentEditingIndex = selectedButtonIndex
    if (showDialog && currentEditingIndex != null) {
        val buttonConfig = item.buttonConfigs.getOrNull(currentEditingIndex)
        val buttonId = "page_button_${item.id}_${currentEditingIndex}"
        ButtonConfigDialog(
            initialConfig = buttonConfig ?: com.andreas_kratzer.ghosttalk.model.ButtonConfig(),
            currentPageId = item.id,
            buttonId = buttonId,
            availablePages = availablePages,
                featureGuard = featureGuard,
                templates = templates,
                isTesting = isExecuting,
                onDismiss = {
                    showDialog = false
                    selectedButtonIndex = null
                },
                onSave = { newConfig ->
                    actions.updateButtonConfig(item.id, currentEditingIndex, newConfig)
                    showDialog = false
                    selectedButtonIndex = null
                },
                onTest = { testConfig ->
                    actions.executeButtonAction(testConfig)
                },
                onNavigateToPage = onEditPage,
                onCreatePage = { name, rows, cols, templateId, onCreated ->
                    val bookId = (item as? Page)?.bookId ?: "book-default"
                    actions.createNewPage(
                        name = name,
                        rows = rows,
                        columns = cols,
                        bookId = bookId,
                        templateId = templateId,
                        onCreated = onCreated
                    )
                },
                onMoveToPage = { targetId, forceMove, onResult ->
                    actions.moveButtonToPage(item.id, currentEditingIndex, targetId, forceMove, onResult)
                }
            )
        }
    }
}

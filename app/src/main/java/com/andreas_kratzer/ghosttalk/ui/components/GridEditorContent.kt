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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.GridItem
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import com.andreas_kratzer.ghosttalk.ui.pages.ButtonConfigDialog
import com.andreas_kratzer.ghosttalk.ui.pages.GridButton
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhosTTalkIcons
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalCurrentPageId
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalIsUserModeActive
import com.andreas_kratzer.ghosttalk.ui.util.GridEditorActions
import com.andreas_kratzer.ghosttalk.ui.util.GridUtils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GridEditorContent(
    item: GridItem,
    actions: GridEditorActions,
    availablePages: List<Page>,
    templates: List<PageTemplate>,
    featureGuard: com.andreas_kratzer.ghosttalk.feature.settings.domain.FeatureGuard,
    bookDefaultScanPattern: String?,
    paddingValues: PaddingValues,
    onEditPage: ((String) -> Unit)? = null
) {
    CompositionLocalProvider(
        LocalCurrentPageId provides item.id,
        LocalIsUserModeActive provides false
    ) {
        val dimensions = LocalDimensions.current
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val density = LocalDensity.current.density
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
            GridEditorControls(item = item, actions = actions)

            val effectiveScanPattern = item.scanPattern ?: bookDefaultScanPattern
            val isRowByRow = effectiveScanPattern == "row_by_row"

            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                val sizeInfo = calculateGridSize(
                    maxWidth = maxWidth,
                    maxHeight = maxHeight,
                    rows = item.rows,
                    cols = item.columns,
                    isRowByRow = isRowByRow,
                    dimensions = dimensions
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(item.columns),
                    state = gridState,
                    modifier = Modifier
                        .width(sizeInfo.totalWidth)
                        .height(sizeInfo.totalHeight),
                    contentPadding = PaddingValues(dimensions.paddingMedium),
                    verticalArrangement = Arrangement.spacedBy(dimensions.gridSpacing),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.gridSpacing)
                ) {
                    if (isRowByRow) {
                        renderRowByRowGrid(
                            item = item,
                            actions = actions,
                            gridState = gridState,
                            rowReorderState = rowReorderState,
                            buttonReorderState = buttonReorderState,
                            sizeInfo = sizeInfo,
                            dimensions = dimensions,
                            density = density,
                            onEditRow = { r ->
                                editingRowIndex = r
                                showRowEditDialog = true
                            },
                            onEditButton = { idx ->
                                selectedButtonIndex = idx
                                showDialog = true
                            }
                        )
                    } else {
                        renderLinearGrid(
                            item = item,
                            actions = actions,
                            gridState = gridState,
                            buttonReorderState = buttonReorderState,
                            sizeInfo = sizeInfo,
                            dimensions = dimensions,
                            density = density,
                            onEditButton = { idx ->
                                selectedButtonIndex = idx
                                showDialog = true
                            }
                        )
                    }
                }
            }
        }

        EditorDialogs(
            item = item,
            actions = actions,
            availablePages = availablePages,
            templates = templates,
            featureGuard = featureGuard,
            isExecuting = isExecuting,
            editingRowIndex = editingRowIndex,
            showRowEditDialog = showRowEditDialog,
            selectedButtonIndex = selectedButtonIndex,
            showDialog = showDialog,
            onDismissRowDialog = {
                showRowEditDialog = false
                editingRowIndex = null
            },
            onDismissButtonDialog = {
                showDialog = false
                selectedButtonIndex = null
            },
            onEditPage = onEditPage
        )
    }
}

@Composable
private fun EditorButtonCell(
    index: Int,
    buttonConfig: ButtonConfig?,
    reorderState: ReorderableState,
    isTarget: Boolean,
    width: Dp,
    height: Dp,
    onDragEnd: (Int) -> Unit,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .reorderableItemVisuals(reorderState, index)
            .dragHandle(
                state = reorderState,
                index = index,
                onDragEnd = { fromIdx ->
                    if (fromIdx != null) {
                        onDragEnd(fromIdx)
                    }
                }
            )
            .background(
                if (isTarget) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else Color.Transparent
            )
            .border(
                width = if (isTarget) 2.dp else 0.dp,
                color = if (isTarget) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = MaterialTheme.shapes.medium
            )
    ) {
        GridButton(
            buttonConfig = buttonConfig,
            isFocused = false,
            // isEditorMode now defaults to !LocalIsUserModeActive.current
            onClick = onClick,
            modifier = Modifier.width(width).height(height)
        )
    }
}

private fun LazyGridScope.renderRowByRowGrid(
    item: GridItem,
    actions: GridEditorActions,
    gridState: LazyGridState,
    rowReorderState: ReorderableState,
    buttonReorderState: ReorderableState,
    sizeInfo: GridSizeInfo,
    dimensions: com.andreas_kratzer.ghosttalk.core.ui.theme.Dimensions,
    density: Float,
    onEditRow: (Int) -> Unit,
    onEditButton: (Int) -> Unit
) {
    val rowTargetIndex = rowReorderState.findTargetIndexForGrid(gridState)
    val buttonTargetIndex = buttonReorderState.findTargetButtonIndex(gridState, item.columns, true, density)

    for (r in 0 until item.rows) {
        item(span = { GridItemSpan(item.columns) }) {
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
                // Row Drag Handle & Edit Icon
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
                        .clickable { onEditRow(r) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = GhosTTalkIcons.Edit,
                        contentDescription = stringResource(R.string.page_editor_row_name_label),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Row Buttons
                Row(
                    modifier = Modifier.weight(1f).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.gridSpacing)
                ) {
                    for (c in 0 until item.columns) {
                        val globalIndex = GridUtils.getGlobalIndex(r, c)
                        EditorButtonCell(
                            index = globalIndex,
                            buttonConfig = item.buttonConfigs.getOrNull(globalIndex),
                            reorderState = buttonReorderState,
                            isTarget = buttonTargetIndex == globalIndex,
                            width = sizeInfo.optimalWidth,
                            height = sizeInfo.optimalHeight,
                            onDragEnd = { fromIdx ->
                                val to = buttonReorderState.findTargetButtonIndex(gridState, item.columns, true, density)
                                if (to != null && to != fromIdx) {
                                    actions.moveButton(item.id, fromIdx, to)
                                }
                            },
                            onClick = { onEditButton(globalIndex) }
                        )
                    }
                }
            }
        }
    }
}

private fun LazyGridScope.renderLinearGrid(
    item: GridItem,
    actions: GridEditorActions,
    gridState: LazyGridState,
    buttonReorderState: ReorderableState,
    sizeInfo: GridSizeInfo,
    dimensions: com.andreas_kratzer.ghosttalk.core.ui.theme.Dimensions,
    density: Float,
    onEditButton: (Int) -> Unit
) {
    val buttonTargetIndex = buttonReorderState.findTargetButtonIndex(gridState, item.columns, false, density)
    
    items(item.rows * item.columns) { localIndex ->
        val globalIndex = GridUtils.localToGlobalIndex(localIndex, item.columns)
        EditorButtonCell(
            index = globalIndex,
            buttonConfig = item.buttonConfigs.getOrNull(globalIndex),
            reorderState = buttonReorderState,
            isTarget = buttonTargetIndex == globalIndex,
            width = sizeInfo.optimalWidth,
            height = sizeInfo.optimalHeight,
            onDragEnd = { fromIdx ->
                val to = buttonReorderState.findTargetButtonIndex(gridState, item.columns, false, density)
                if (to != null && to != fromIdx) {
                    actions.moveButton(item.id, fromIdx, to)
                }
            },
            onClick = { onEditButton(globalIndex) }
        )
    }
}

@Composable
private fun EditorDialogs(
    item: GridItem,
    actions: GridEditorActions,
    availablePages: List<Page>,
    templates: List<PageTemplate>,
    featureGuard: com.andreas_kratzer.ghosttalk.feature.settings.domain.FeatureGuard,
    isExecuting: Boolean,
    editingRowIndex: Int?,
    showRowEditDialog: Boolean,
    selectedButtonIndex: Int?,
    showDialog: Boolean,
    onDismissRowDialog: () -> Unit,
    onDismissButtonDialog: () -> Unit,
    onEditPage: ((String) -> Unit)?
) {
    if (showRowEditDialog && editingRowIndex != null) {
        RowEditDialog(
            initialName = item.rowNames.getOrNull(editingRowIndex) ?: stringResource(R.string.page_row_label).format(editingRowIndex + 1),
            onDismiss = onDismissRowDialog,
            onSave = { newName ->
                actions.updateRowName(item.id, editingRowIndex, newName)
                onDismissRowDialog()
            }
        )
    }

    if (showDialog && selectedButtonIndex != null) {
        val buttonConfig = item.buttonConfigs.getOrNull(selectedButtonIndex)
        val buttonId = "page_button_${item.id}_${selectedButtonIndex}"
        ButtonConfigDialog(
            buttonConfig = buttonConfig ?: ButtonConfig(),
            pages = availablePages,
            templates = templates,
            onDismiss = onDismissButtonDialog,
            onSave = { newConfig ->
                actions.updateButtonConfig(item.id, selectedButtonIndex, newConfig)
                onDismissButtonDialog()
            }
        )
    }
}

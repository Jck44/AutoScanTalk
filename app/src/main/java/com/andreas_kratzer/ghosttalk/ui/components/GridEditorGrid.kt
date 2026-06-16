package com.andreas_kratzer.ghosttalk.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.GridItem
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.ui.theme.Dimensions
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.core.util.GridUtils
import com.andreas_kratzer.ghosttalk.ui.pages.GridButton
import com.andreas_kratzer.ghosttalk.ui.util.GridEditorActions

@Composable
fun GridEditorGrid(
    pageToShow: GridItem,
    gridState: LazyGridState,
    sizeInfo: GridSizeInfo,
    dimensions: Dimensions,
    density: Float,
    gridSpacingPx: Float,
    availablePages: List<Page>,
    pageMetrics: Map<String, com.andreas_kratzer.ghosttalk.core.model.ButtonEffortMetrics>,
    isEditPreviewActive: Boolean,
    isRowByRow: Boolean,
    horizontalPadding: Dp,
    maxHeight: Dp,
    actions: GridEditorActions,
    buttonReorderState: ReorderableState,
    rowReorderState: ReorderableState,
    isMultiSelectMode: Boolean = false,
    selectedButtonIndices: Set<Int> = emptySet(),
    onEditRow: (Int) -> Unit,
    onEditButton: (Int) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isReducedMotion = remember(context) {
        try {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            ) == 0.0f
        } catch (e: Exception) {
            false
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(pageToShow.columns),
        state = gridState,
        modifier = Modifier
            .width(sizeInfo.totalWidth)
            .height(sizeInfo.totalHeight.coerceAtMost(maxHeight)),
        contentPadding = PaddingValues(horizontal = horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(dimensions.gridSpacing),
        horizontalArrangement = Arrangement.spacedBy(dimensions.gridSpacing)
    ) {
        if (isRowByRow) {
            renderRowByRowGrid(
                item = pageToShow,
                actions = actions,
                gridState = gridState,
                rowReorderState = rowReorderState,
                buttonReorderState = buttonReorderState,
                sizeInfo = sizeInfo,
                dimensions = dimensions,
                density = density,
                gridSpacingPx = gridSpacingPx,
                availablePages = availablePages,
                pageMetrics = pageMetrics,
                isEditPreviewActive = isEditPreviewActive,
                isMultiSelectMode = isMultiSelectMode,
                selectedButtonIndices = selectedButtonIndices,
                isReducedMotion = isReducedMotion,
                onEditRow = onEditRow,
                onEditButton = onEditButton
            )
        } else {
            renderLinearGrid(
                item = pageToShow,
                actions = actions,
                gridState = gridState,
                buttonReorderState = buttonReorderState,
                sizeInfo = sizeInfo,
                dimensions = dimensions,
                density = density,
                gridSpacingPx = gridSpacingPx,
                availablePages = availablePages,
                pageMetrics = pageMetrics,
                isEditPreviewActive = isEditPreviewActive,
                isMultiSelectMode = isMultiSelectMode,
                selectedButtonIndices = selectedButtonIndices,
                isReducedMotion = isReducedMotion,
                onEditButton = onEditButton
            )
        }
    }
}

@Composable
private fun EditorButtonCell(
    localIndex: Int,
    globalIndex: Int,
    buttonConfig: ButtonConfig?,
    reorderState: ReorderableState,
    isTarget: Boolean,
    width: Dp,
    height: Dp,
    numCols: Int,
    gridSpacing: Dp,
    targetPageName: String? = null,
    heatmapIntensity: Float? = null,
    effortMetrics: com.andreas_kratzer.ghosttalk.core.model.ButtonEffortMetrics? = null,
    isEditPreviewActive: Boolean = false,
    isMultiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    isReducedMotion: Boolean = false,
    onDragEnd: (Int) -> Unit,
    onClick: () -> Unit
) {
    val dragDropState = LocalDragDropState.current
    val isDragging = if (isEditPreviewActive) false else dragDropState.isDragging
    val isDraggedHovered = if (isEditPreviewActive) false else dragDropState.currentHoveredTarget == GridCellTarget(globalIndex)
    
    
    val targetScale = when {
        !isMultiSelectMode -> 1.0f
        isSelected -> 0.90f
        else -> 0.96f
    }
    
    val cellScale by if (isReducedMotion) {
        remember(targetScale) { mutableStateOf(targetScale) }
    } else {
        animateFloatAsState(
            targetValue = targetScale,
            animationSpec = tween(durationMillis = 180),
            label = "cellScale"
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    
    val isHighlighted = isTarget || isDraggedHovered
 
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .semantics {
                selected = isSelected
            }
            .run {
                if (!isEditPreviewActive && !isMultiSelectMode) reorderableItemVisuals(reorderState, localIndex)
                else this
            }
            .run {
                if (!isEditPreviewActive && !isMultiSelectMode) {
                    dragHandle(
                        state = reorderState,
                        index = localIndex,
                        onDragEnd = { fromIdx ->
                            if (fromIdx != null) {
                                onDragEnd(fromIdx)
                            }
                        }
                    )
                } else this
            }
            .run {
                if (!isEditPreviewActive && !isMultiSelectMode) dropTarget(key = GridCellTarget(globalIndex))
                else this
            }
            .run {
                if (buttonConfig != null && !isEditPreviewActive && !isMultiSelectMode) {
                    dragSource(item = DraggedGridCell(globalIndex, buttonConfig), longPress = true)
                } else this
            }
    ) {
        GridButton(
            buttonConfig = buttonConfig,
            isFocused = false,
            targetPageName = targetPageName,
            heatmapIntensity = heatmapIntensity,
            effortMetrics = effortMetrics,
            isMultiSelectMode = isMultiSelectMode,
            onClick = onClick,
            modifier = Modifier
                .scale(cellScale)
                .fillMaxSize()
                .background(
                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    else if (isHighlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                    else Color.Transparent,
                    shape = MaterialTheme.shapes.small
                )
                .border(
                    width = if (isSelected) 2.dp else if (isDraggedHovered) 3.dp else if (isTarget) 2.dp else 0.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                            else if (isDraggedHovered) MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)
                            else if (isTarget) MaterialTheme.colorScheme.primary
                            else Color.Transparent,
                    shape = MaterialTheme.shapes.small
                )
        )

        if (isMultiSelectMode && buttonConfig != null && isSelected) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp)
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.surface,
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 1.dp
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.bulk_action_selected),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(2.dp).fillMaxSize()
                )
            }
        }

        if (isDragging) {
            val leftTarget = InsertTarget(globalIndex)
            val isLeftHovered = dragDropState.currentHoveredTarget == leftTarget
            val halfGap = gridSpacing / 2
            
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = -(12.dp + halfGap))
                    .width(24.dp)
                    .fillMaxHeight()
                    .dropTarget(key = leftTarget),
                contentAlignment = Alignment.Center
            ) {
                if (isLeftHovered) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeight(0.85f)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = MaterialTheme.shapes.extraSmall
                            )
                    )
                }
            }

            val isLastCol = (globalIndex % numCols) == numCols - 1
            if (isLastCol) {
                val rightTarget = InsertTarget(globalIndex + 1)
                val isRightHovered = dragDropState.currentHoveredTarget == rightTarget
                
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = 12.dp + halfGap)
                        .width(24.dp)
                        .fillMaxHeight()
                        .dropTarget(key = rightTarget),
                    contentAlignment = Alignment.Center
                ) {
                    if (isRightHovered) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .fillMaxHeight(0.85f)
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = MaterialTheme.shapes.extraSmall
                                )
                        )
                    }
                }
            }
        }
    }
}

private fun LazyGridScope.renderRowByRowGrid(
    item: GridItem,
    actions: GridEditorActions,
    gridState: LazyGridState,
    rowReorderState: ReorderableState,
    buttonReorderState: ReorderableState,
    sizeInfo: GridSizeInfo,
    dimensions: Dimensions,
    density: Float,
    gridSpacingPx: Float,
    availablePages: List<Page>,
    pageMetrics: Map<String, com.andreas_kratzer.ghosttalk.core.model.ButtonEffortMetrics>,
    isEditPreviewActive: Boolean,
    isMultiSelectMode: Boolean,
    selectedButtonIndices: Set<Int>,
    isReducedMotion: Boolean,
    onEditRow: (Int) -> Unit,
    onEditButton: (Int) -> Unit
) {
    val rowTargetIndex = if (isEditPreviewActive) -1 else rowReorderState.findTargetIndexForGrid(gridState)
    val buttonTargetIndex = if (isEditPreviewActive) -1 else buttonReorderState.findTargetButtonIndex(
        gridState = gridState,
        numCols = item.columns,
        isRowByRow = true,
        density = density,
        gridSpacingPx = gridSpacingPx
    )

    for (r in 0 until item.rows) {
        item(span = { GridItemSpan(item.columns) }) {
            val isRowTarget = rowTargetIndex == r
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .run {
                        if (!isEditPreviewActive) reorderableItemVisuals(rowReorderState, r)
                        else this
                    }
                    .background(
                        if (isRowTarget) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else Color.Transparent
                    )
                    .border(
                        width = if (isRowTarget) 3.dp else 2.dp,
                        color = if (isRowTarget) MaterialTheme.colorScheme.primary 
                                 else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.small
                    )
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Row Drag Handle & Edit Icon
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .fillMaxHeight()
                        .run {
                            if (!isEditPreviewActive) {
                                dragHandle(
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
                                .clickable { onEditRow(r) }
                            } else this
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (!isEditPreviewActive) {
                        Icon(
                            imageVector = GhostTalkIcons.Edit,
                            contentDescription = stringResource(R.string.page_editor_row_name_label),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Row Buttons
                Column(
                    modifier = Modifier.weight(1f).padding(dimensions.paddingMedium)
                ) {
                    val rowName = item.rowNames.getOrNull(r)
                        ?: if (item.id.startsWith("static_row_")) "Statische Zeile"
                           else stringResource(R.string.page_row_label).format(r + 1)
                    Text(
                        text = rowName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = dimensions.paddingSmall)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(dimensions.gridSpacing)
                    ) {
                        for (c in 0 until item.columns) {
                            val globalIndex = GridUtils.getGlobalIndex(r, c)
                            val buttonConfig = item.buttonConfigs.getOrNull(globalIndex)
                            val targetPageName = (buttonConfig?.buttonAction as? NavigateToPageButtonAction)?.let { action ->
                                if (action.pageId.isEmpty()) stringResource(R.string.button_action_navigate_to_start_page)
                                else availablePages.find { it.id == action.pageId }?.name
                            }
                            
                            val metrics = buttonConfig?.let { pageMetrics[it.id] }
                            val isSelected = selectedButtonIndices.contains(globalIndex)
                            EditorButtonCell(
                                localIndex = globalIndex,
                                globalIndex = globalIndex,
                                buttonConfig = buttonConfig,
                                reorderState = buttonReorderState,
                                isTarget = buttonTargetIndex == globalIndex,
                                width = sizeInfo.optimalWidth,
                                height = sizeInfo.optimalHeight,
                                numCols = item.columns,
                                gridSpacing = dimensions.gridSpacing,
                                targetPageName = targetPageName,
                                heatmapIntensity = metrics?.heatmapIntensity,
                                effortMetrics = metrics,
                                isEditPreviewActive = isEditPreviewActive,
                                isMultiSelectMode = isMultiSelectMode,
                                isSelected = isSelected,
                                isReducedMotion = isReducedMotion,
                                onDragEnd = { fromIdx ->
                                    val to = buttonReorderState.findTargetButtonIndex(
                                        gridState = gridState,
                                        numCols = item.columns,
                                        isRowByRow = true,
                                        density = density,
                                        gridSpacingPx = gridSpacingPx
                                    )
                                    if (to != null && to != fromIdx) {
                                        actions.moveButton(item.id, fromIdx, to)
                                    }
                                },
                                onClick = {
                                    if (isMultiSelectMode) {
                                        onEditButton(globalIndex)
                                    } else {
                                        if (!isEditPreviewActive) {
                                            onEditButton(globalIndex)
                                        } else if (buttonConfig != null) {
                                            actions.executeButtonAction(buttonConfig)
                                        }
                                    }
                                }
                            )
                        }
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
    dimensions: Dimensions,
    density: Float,
    gridSpacingPx: Float,
    availablePages: List<Page>,
    pageMetrics: Map<String, com.andreas_kratzer.ghosttalk.core.model.ButtonEffortMetrics>,
    isEditPreviewActive: Boolean,
    isMultiSelectMode: Boolean,
    selectedButtonIndices: Set<Int>,
    isReducedMotion: Boolean,
    onEditButton: (Int) -> Unit
) {
    val buttonTargetIndex = if (isEditPreviewActive) -1 else buttonReorderState.findTargetButtonIndex(
        gridState = gridState,
        numCols = item.columns,
        isRowByRow = false,
        density = density,
        gridSpacingPx = gridSpacingPx
    )
    
    items(item.rows * item.columns) { localIndex ->
        val globalIndex = GridUtils.localToGlobalIndex(localIndex, item.columns)
        val buttonConfig = item.buttonConfigs.getOrNull(globalIndex)
        val targetPageName = (buttonConfig?.buttonAction as? NavigateToPageButtonAction)?.let { action ->
            if (action.pageId.isEmpty()) stringResource(R.string.button_action_navigate_to_start_page)
            else availablePages.find { it.id == action.pageId }?.name
        }

        val metrics = buttonConfig?.let { pageMetrics[it.id] }
        val isSelected = selectedButtonIndices.contains(globalIndex)
        EditorButtonCell(
            localIndex = localIndex,
            globalIndex = globalIndex,
            buttonConfig = buttonConfig,
            reorderState = buttonReorderState,
            isTarget = buttonTargetIndex == globalIndex,
            width = sizeInfo.optimalWidth,
            height = sizeInfo.optimalHeight,
            numCols = item.columns,
            gridSpacing = dimensions.gridSpacing,
            targetPageName = targetPageName,
            heatmapIntensity = metrics?.heatmapIntensity,
            effortMetrics = metrics,
            isEditPreviewActive = isEditPreviewActive,
            isMultiSelectMode = isMultiSelectMode,
            isSelected = isSelected,
            isReducedMotion = isReducedMotion,
            onDragEnd = { fromLocalIdx ->
                val toGlobal = buttonReorderState.findTargetButtonIndex(
                    gridState = gridState,
                    numCols = item.columns,
                    isRowByRow = false,
                    density = density,
                    gridSpacingPx = gridSpacingPx
                )
                if (toGlobal != null) {
                    val fromGlobal = GridUtils.localToGlobalIndex(fromLocalIdx, item.columns)
                    if (toGlobal != fromGlobal) {
                        actions.moveButton(item.id, fromGlobal, toGlobal)
                    }
                }
            },
            onClick = {
                if (isMultiSelectMode) {
                    onEditButton(globalIndex)
                } else {
                    if (!isEditPreviewActive) {
                        onEditButton(globalIndex)
                    } else if (buttonConfig != null) {
                        actions.executeButtonAction(buttonConfig)
                    }
                }
            }
        )
    }
}

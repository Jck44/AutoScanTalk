package com.andreas_kratzer.ghosttalk.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex

/**
 * A simplified reorderable state manager for LazyLists and LazyGrids.
 */
class ReorderableState {
    var draggedIndex by mutableStateOf<Int?>(null)
    var dragOffset by mutableStateOf(Offset.Zero)

    fun onDragStart(index: Int) {
        draggedIndex = index
    }

    fun onDrag(offset: Offset) {
        dragOffset += offset
    }

    fun onDragEnd() {
        draggedIndex = null
        dragOffset = Offset.Zero
    }

    /**
     * Finds the index of the item that the dragged item is currently hovering over for LazyVerticalGrid.
     */
    fun findTargetIndexForGrid(gridState: LazyGridState): Int? {
        val draggedIdx = draggedIndex ?: return null
        val info = gridState.layoutInfo
        val draggedItem = info.visibleItemsInfo.find { it.index == draggedIdx } ?: return null
        
        // Calculate center of dragged item in its current position
        val draggedCenterX = draggedItem.offset.x + draggedItem.size.width / 2 + dragOffset.x
        val draggedCenterY = draggedItem.offset.y + draggedItem.size.height / 2 + dragOffset.y
        
        // Find if center is within another item's original bounds
        return info.visibleItemsInfo.find { item ->
            val startX = item.offset.x.toFloat()
            val endX = (item.offset.x + item.size.width).toFloat()
            val startY = item.offset.y.toFloat()
            val endY = (item.offset.y + item.size.height).toFloat()
            
            item.index != draggedIdx && 
            draggedCenterX in startX..endX && 
            draggedCenterY in startY..endY
        }?.index
    }

    /**
     * Finds the index of the button (global index) even if it's nested in a row-item.
     * @param gridState The state of the LazyVerticalGrid.
     * @param numCols The number of columns currently displayed.
     * @param isRowByRow Whether the grid is in row-by-row mode (rows are grid items).
     * @param density The screen density for DP to PX conversion.
     * @param gridSpacingPx The spacing between grid items in pixels.
     * @return The global target index (7-wide storage) or null.
     */
    fun findTargetButtonIndex(
        gridState: LazyGridState,
        numCols: Int,
        isRowByRow: Boolean,
        density: Float = 1f,
        gridSpacingPx: Float = 0f
    ): Int? {
        val draggedIdx = draggedIndex ?: return null
        val info = gridState.layoutInfo
        
        if (isRowByRow) {
            // In row-by-row mode, draggedIdx is the global index (7-wide).
            // Rows are grid items, so their index matches the storage row index.
            val draggedRow = draggedIdx / 7
            val draggedCol = draggedIdx % 7
            val draggedItem = info.visibleItemsInfo.find { it.index == draggedRow } ?: return null
            
            // Edit handle is 48dp, row padding is 8dp. Total leading space is 56dp.
            val handleWidthPx = 48 * density
            val paddingPx = 8 * density
            
            // Calculate row content width (excluding handle and padding)
            // Note: calculateGridSize uses 64dp (48+8+8) for rowHandleWidth.
            val rowContentWidth = draggedItem.size.width - handleWidthPx - (paddingPx * 2)
            
            // Button width accounting for spacing
            val buttonWidth = (rowContentWidth - (gridSpacingPx * (numCols - 1))) / numCols
            
            // Calculate center of dragged button in global coordinates
            val colStartOffset = draggedCol * (buttonWidth + gridSpacingPx)
            val draggedCenterX = draggedItem.offset.x + handleWidthPx + paddingPx + 
                                colStartOffset + (buttonWidth / 2) + dragOffset.x
            val draggedCenterY = draggedItem.offset.y + draggedItem.size.height / 2 + dragOffset.y

            // Find target row
            val targetRowInfo = info.visibleItemsInfo.find { item ->
                draggedCenterY in item.offset.y.toFloat()..(item.offset.y + item.size.height).toFloat()
            } ?: return null
            
            val targetRow = targetRowInfo.index
            val targetRowContentAreaWidth = targetRowInfo.size.width - handleWidthPx - (paddingPx * 2)
            val relativeX = draggedCenterX - targetRowInfo.offset.x - handleWidthPx - paddingPx
            
            // Calculate target column by reverse-engineering the position with spacing
            // x = col * (buttonWidth + spacing) -> col = x / (buttonWidth + spacing)
            val targetColWidthWithSpacing = (targetRowContentAreaWidth + gridSpacingPx) / numCols
            val targetCol = (relativeX / targetColWidthWithSpacing).toInt().coerceIn(0, numCols - 1)
            
            // Always return global index (7-wide storage)
            return targetRow * 7 + targetCol
        } else {
            // In linear mode, draggedIdx should be the local grid index (index in LazyVerticalGrid).
            val localIndex = findTargetIndexForGrid(gridState) ?: return null
            val r = localIndex / numCols
            val c = localIndex % numCols
            // Map back to 7-wide global storage
            return r * 7 + c
        }
    }

    /**
     * Finds the index of the item that the dragged item is currently hovering over for LazyColumn.
     */
    fun findTargetIndexForList(listState: LazyListState): Int? {
        val draggedIdx = draggedIndex ?: return null
        val info = listState.layoutInfo
        val draggedItem = info.visibleItemsInfo.find { it.index == draggedIdx } ?: return null
        
        // Calculate center of dragged item in its current position
        val draggedCenterY = draggedItem.offset + draggedItem.size / 2 + dragOffset.y
        
        // Find if center is within another item's original bounds
        return info.visibleItemsInfo.find { item ->
            val startY = item.offset.toFloat()
            val endY = (item.offset + item.size).toFloat()
            
            item.index != draggedIdx && draggedCenterY in startY..endY
        }?.index
    }
}

@Composable
fun rememberReorderableState(): ReorderableState {
    return remember { ReorderableState() }
}

/**
 * Handles the visual transformation of a reorderable item.
 */
fun Modifier.reorderableItemVisuals(
    state: ReorderableState,
    index: Int
): Modifier = this.graphicsLayer {
    if (state.draggedIndex == index) {
        translationX = state.dragOffset.x
        translationY = state.dragOffset.y
        scaleX = 1.05f
        scaleY = 1.05f
        alpha = 0.9f
    }
}.zIndex(if (state.draggedIndex == index) 1f else 0f)

/**
 * Handles the drag gesture for a specific index.
 */
fun Modifier.dragHandle(
    state: ReorderableState,
    index: Int,
    onDragStart: () -> Unit = {},
    onDragEnd: (Int?) -> Unit = {},
    onDrag: () -> Unit = {}
): Modifier = this.pointerInput(index) {
    detectDragGesturesAfterLongPress(
        onDragStart = {
            state.onDragStart(index)
            onDragStart()
        },
        onDragEnd = {
            onDragEnd(state.draggedIndex)
            state.onDragEnd()
        },
        onDragCancel = {
            state.onDragEnd()
            onDragEnd(null)
        },
        onDrag = { change, dragAmount ->
            change.consume()
            state.onDrag(dragAmount)
            onDrag()
        }
    )
}

/**
 * Compatibility wrapper for reorderableItem.
 */
fun Modifier.reorderableItem(
    state: ReorderableState,
    index: Int,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDrag: () -> Unit = {}
): Modifier = this
    .reorderableItemVisuals(state, index)
    .dragHandle(
        state = state,
        index = index,
        onDragStart = onDragStart,
        onDragEnd = { _ -> onDragEnd() },
        onDrag = onDrag
    )

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
 * Custom modifier to handle drag and drop logic for a specific item.
 */
fun Modifier.reorderableItem(
    state: ReorderableState,
    index: Int,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDrag: () -> Unit = {}
): Modifier = this.pointerInput(Unit) {
    detectDragGesturesAfterLongPress(
        onDragStart = {
            state.onDragStart(index)
            onDragStart()
        },
        onDragEnd = {
            state.onDragEnd()
            onDragEnd()
        },
        onDragCancel = {
            state.onDragEnd()
            onDragEnd()
        },
        onDrag = { change, dragAmount ->
            change.consume()
            state.onDrag(dragAmount)
            onDrag()
        }
    )
}
.graphicsLayer {
    if (state.draggedIndex == index) {
        translationX = state.dragOffset.x
        translationY = state.dragOffset.y
        scaleX = 1.05f
        scaleY = 1.05f
        alpha = 0.9f
    }
}
.zIndex(if (state.draggedIndex == index) 1f else 0f)

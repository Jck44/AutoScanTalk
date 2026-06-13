package com.andreas_kratzer.ghosttalk.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

class ChipDragDropState {
    var draggedKey by mutableStateOf<Any?>(null)
    var draggedLabel by mutableStateOf("")
    var dragSourceCategory by mutableStateOf<String?>(null)

    val targetBounds = mutableStateMapOf<String, Rect>()
    val dragStartCenter = mutableStateOf(Offset.Zero)
    var dragOffset by mutableStateOf(Offset.Zero)
    var draggedSize by mutableStateOf(Offset.Zero)
    var rootBoxBounds by mutableStateOf<Rect?>(null)

    val dragGlobalPos: Offset get() = dragStartCenter.value + dragOffset

    fun onDragStart(key: Any, label: String, sourceCategory: String?, center: Offset, size: Offset) {
        draggedKey = key
        draggedLabel = label
        dragSourceCategory = sourceCategory
        dragStartCenter.value = center
        draggedSize = size
        dragOffset = Offset.Zero
    }

    fun onDrag(amount: Offset) {
        dragOffset += amount
    }

    fun clear() {
        draggedKey = null
        dragSourceCategory = null
    }

    fun clearTargets() {
        targetBounds.clear()
    }
}

@Composable
fun rememberChipDragDropState(resetKey: Any? = null): ChipDragDropState {
    val state = remember { ChipDragDropState() }
    LaunchedEffect(resetKey) {
        state.clearTargets()
        state.clear()
    }
    return state
}

fun Modifier.chipDropTarget(state: ChipDragDropState, key: String): Modifier = this.onGloballyPositioned { layoutCoordinates ->
    state.targetBounds[key] = layoutCoordinates.boundsInRoot()
}

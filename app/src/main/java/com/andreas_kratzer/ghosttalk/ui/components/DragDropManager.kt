package com.andreas_kratzer.ghosttalk.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex

val LocalDragDropState = staticCompositionLocalOf<DragDropState> {
    error("No DragDropState provided")
}

class DragDropState {
    var isDragging by mutableStateOf(false)
    var dragItem by mutableStateOf<Any?>(null)
    var dragPosition by mutableStateOf(Offset.Zero) // top-left of the item in window coordinates
    var dragOffset by mutableStateOf(Offset.Zero) // accumulated drag displacement
    var draggedSize by mutableStateOf(IntSize.Zero)
    var touchOffset by mutableStateOf(Offset.Zero) // touch point offset relative to top-left of the item

    // Tracks bounds of drop targets
    private val targets = mutableStateMapOf<Any, Rect>()
    var currentHoveredTarget by mutableStateOf<Any?>(null)

    // Global callback for drops
    var onDropCallback by mutableStateOf<((Any, Any) -> Unit)?>(null)

    fun registerTarget(key: Any, bounds: Rect) {
        targets[key] = bounds
    }

    fun unregisterTarget(key: Any) {
        targets.remove(key)
    }

    fun onDragStart(item: Any, initialPosition: Offset, size: IntSize, initialTouchOffset: Offset) {
        dragItem = item
        dragPosition = initialPosition
        dragOffset = Offset.Zero
        draggedSize = size
        touchOffset = initialTouchOffset
        isDragging = true
        currentHoveredTarget = null
    }

    fun onDrag(dragAmount: Offset) {
        dragOffset += dragAmount
        // Use the exact touch point (pointer position) in window coordinates for hover detection
        val point = dragPosition + touchOffset + dragOffset

        // Sort matching targets by area (width * height) and take the smallest (most specific/inner)
        currentHoveredTarget = targets.entries
            .filter { entry -> entry.value.contains(point) }
            .minByOrNull { entry -> entry.value.width * entry.value.height }
            ?.key
    }

    fun onDragEnd() {
        val item = dragItem
        val target = currentHoveredTarget
        val callback = onDropCallback
        if (item != null && target != null && callback != null) {
            callback(item, target)
        }
        reset()
    }

    fun onDragCancel() {
        reset()
    }

    private fun reset() {
        isDragging = false
        dragItem = null
        dragPosition = Offset.Zero
        dragOffset = Offset.Zero
        draggedSize = IntSize.Zero
        touchOffset = Offset.Zero
        currentHoveredTarget = null
    }
}

@Composable
fun rememberDragDropState(): DragDropState {
    return remember { DragDropState() }
}

@Composable
fun DragDropContainer(
    state: DragDropState,
    onDrop: (Any, Any) -> Unit,
    modifier: Modifier = Modifier,
    floatingPreview: @Composable ((Any) -> Unit)? = null,
    content: @Composable () -> Unit
) {
    state.onDropCallback = onDrop
    var containerPositionInWindow by remember { mutableStateOf(Offset.Zero) }

    CompositionLocalProvider(LocalDragDropState provides state) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .onGloballyPositioned { layoutCoordinates ->
                    containerPositionInWindow = layoutCoordinates.positionInWindow()
                }
        ) {
            content()
            
            if (state.isDragging && state.dragItem != null && floatingPreview != null) {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            val currentPos = state.dragPosition + state.dragOffset
                            // Subtract container's window offset so translation aligns exactly with container coordinates
                            translationX = currentPos.x - containerPositionInWindow.x
                            translationY = currentPos.y - containerPositionInWindow.y
                            scaleX = 1.08f
                            scaleY = 1.08f
                            alpha = 0.85f
                        }
                        .zIndex(9999f)
                ) {
                    floatingPreview(state.dragItem!!)
                }
            }
        }
    }
}

fun Modifier.dragSource(
    item: Any,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    longPress: Boolean = true
): Modifier = this.composed {
    val state = LocalDragDropState.current
    var itemPosition by remember { mutableStateOf(Offset.Zero) }
    var itemSize by remember { mutableStateOf(IntSize.Zero) }

    this
        .onGloballyPositioned { layoutCoordinates ->
            itemPosition = layoutCoordinates.positionInWindow()
            itemSize = layoutCoordinates.size
        }
        .pointerInput(item) {
            if (longPress) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        // Pass itemPosition as the base top-left and offset as touchOffset relative to base
                        state.onDragStart(item, itemPosition, itemSize, offset)
                        onDragStart()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        state.onDrag(dragAmount)
                    },
                    onDragEnd = {
                        state.onDragEnd()
                        onDragEnd()
                    },
                    onDragCancel = {
                        state.onDragCancel()
                        onDragEnd()
                    }
                )
            } else {
                detectDragGestures(
                    onDragStart = { offset ->
                        state.onDragStart(item, itemPosition, itemSize, offset)
                        onDragStart()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        state.onDrag(dragAmount)
                    },
                    onDragEnd = {
                        state.onDragEnd()
                        onDragEnd()
                    },
                    onDragCancel = {
                        state.onDragCancel()
                        onDragEnd()
                    }
                )
            }
        }
}

fun Modifier.dropTarget(
    key: Any
): Modifier = this.composed {
    val state = LocalDragDropState.current

    DisposableEffect(key) {
        onDispose {
            state.unregisterTarget(key)
        }
    }

    this.onGloballyPositioned { layoutCoordinates ->
        val bounds = layoutCoordinates.boundsInWindow()
        state.registerTarget(key, bounds)
    }
}

data class TemplateDropTarget(val template: com.andreas_kratzer.ghosttalk.core.model.ButtonTemplate)
data class CategoryHeaderDropTarget(val groupName: String)


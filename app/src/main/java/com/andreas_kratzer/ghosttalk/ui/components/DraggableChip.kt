package com.andreas_kratzer.ghosttalk.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.ui.theme.ActionVisualTokens

/**
 * Ein flexibler, via Long-Press ziehbarer Chip für die Kachel-Labels.
 */
@Composable
fun DraggableChip(
    label: String,
    isDragged: Boolean,
    onDragStart: (center: Offset, size: Offset) -> Unit, // liefert die initiale globale Mitte und Größe
    onDrag: (Offset) -> Unit,      // liefert das Drag-Delta
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit = {},
    action: ButtonAction? = null,
    onClick: (() -> Unit)? = null
) {
    var globalPos by remember { mutableStateOf(Offset.Zero) }
    var chipSize by remember { mutableStateOf(Offset.Zero) }

    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)

    val isDark = isSystemInDarkTheme()
    val (bgColor, textColor) = remember(action, isDark) {
        ActionVisualTokens.getColors(action, isDark)
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (isDragged) {
            bgColor.copy(alpha = 0.3f)
        } else {
            bgColor
        },
        tonalElevation = 0.dp,
        modifier = Modifier
            .onGloballyPositioned { layoutCoordinates ->
                val bounds = layoutCoordinates.boundsInRoot()
                globalPos = bounds.center
                chipSize = Offset(bounds.width, bounds.height)
            }
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { _ -> currentOnDragStart(globalPos, chipSize) },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        currentOnDrag(dragAmount)
                    },
                    onDragEnd = { currentOnDragEnd() },
                    onDragCancel = { currentOnDragCancel() }
                )
            }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (isDragged) {
                textColor.copy(alpha = 0.3f)
            } else {
                textColor
            }
        )
    }
}

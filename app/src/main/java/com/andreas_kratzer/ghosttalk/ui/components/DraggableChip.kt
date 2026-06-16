package com.andreas_kratzer.ghosttalk.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
    modifier: Modifier = Modifier,
    action: ButtonAction? = null,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
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
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        tonalElevation = 0.dp,
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
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

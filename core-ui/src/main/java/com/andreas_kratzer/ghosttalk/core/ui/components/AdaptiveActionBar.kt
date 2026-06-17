package com.andreas_kratzer.ghosttalk.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

data class EditorAction(
    val key: String,
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val tint: Color? = null,
    val backgroundColor: Color? = null,
    val testTag: String? = null,
    val priority: Int = 1 // 1 = P1 (adaptiv), 2 = P2 (immer Overflow)
)

@Composable
fun ActionIconButton(
    action: EditorAction,
    modifier: Modifier = Modifier
) {
    val baseModifier = modifier.then(
        if (action.backgroundColor != null) {
            Modifier.background(action.backgroundColor, shape = androidx.compose.foundation.shape.CircleShape)
        } else {
            Modifier
        }
    ).then(
        if (action.testTag != null) Modifier.testTag(action.testTag) else Modifier
    )

    IconButton(
        onClick = action.onClick,
        enabled = action.enabled,
        modifier = baseModifier
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = action.label,
            tint = action.tint ?: (if (action.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f))
        )
    }
}

@Composable
fun AdaptiveActionBar(
    actions: List<EditorAction>,
    modifier: Modifier = Modifier,
    isTablet: Boolean = false,
    overflowTestTag: String = "editor_overflow_menu_trigger"
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val p1Actions = actions.filter { it.priority == 1 }
    val p2Actions = actions.filter { it.priority == 2 }

    BoxWithConstraints(modifier = modifier) {
        val maxW = maxWidth
        // Estimate widths: IconButton is 48.dp.
        val itemWidth = 48.dp
        val overflowButtonWidth = 48.dp

        val capacity = (maxW / itemWidth).toInt()
        val willNeedOverflow = p2Actions.isNotEmpty() || capacity < p1Actions.size
        
        val visibleCount = if (willNeedOverflow) {
            val availableForP1 = maxW - overflowButtonWidth
            (availableForP1 / itemWidth).toInt().coerceAtLeast(0).coerceAtMost(p1Actions.size)
        } else {
            p1Actions.size
        }

        val visibleP1 = p1Actions.take(visibleCount)
        val overflowActions = p1Actions.drop(visibleCount) + p2Actions

        Row(verticalAlignment = Alignment.CenterVertically) {
            visibleP1.forEach { action ->
                ActionIconButton(action = action)
            }
            if (overflowActions.isNotEmpty()) {
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.testTag(overflowTestTag)
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Mehr Optionen")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        overflowActions.forEach { action ->
                            DropdownMenuItem(
                                text = { Text(action.label) },
                                onClick = {
                                    menuExpanded = false
                                    action.onClick()
                                },
                                enabled = action.enabled,
                                leadingIcon = {
                                    Icon(
                                        imageVector = action.icon,
                                        contentDescription = null,
                                        tint = action.tint ?: MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                modifier = if (action.testTag != null) Modifier.testTag(action.testTag) else Modifier
                            )
                        }
                    }
                }
            }
        }
    }
}

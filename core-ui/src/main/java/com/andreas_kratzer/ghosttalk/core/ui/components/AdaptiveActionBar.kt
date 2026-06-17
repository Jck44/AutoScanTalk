package com.andreas_kratzer.ghosttalk.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
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
    val priority: Int = 1, // 1 = highest priority to stay inline
    val alwaysOverflow: Boolean = false
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
fun AdaptiveEditorBar(
    titleContent: @Composable RowScope.() -> Unit,
    modeSwitcher: (@Composable () -> Unit)?,
    actions: List<EditorAction>,
    onExitEditor: (() -> Unit)?,
    overflowTestTag: String,
    exitTestTag: String,
    exitContentDescription: String,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val overflowActions = mutableListOf<EditorAction>()

    SubcomposeLayout(modifier = modifier) { constraints ->
        val maxW = constraints.maxWidth

        // 1. Measure modeSwitcher
        val switcherPlaceables = subcompose("switcher") {
            if (modeSwitcher != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(16.dp))
                    modeSwitcher()
                }
            }
        }.map { it.measure(Constraints()) }
        val switcherWidth = switcherPlaceables.sumOf { it.width }

        // 2. Measure Exit button
        val exitPlaceables = subcompose("exit") {
            if (onExitEditor != null) {
                IconButton(onClick = onExitEditor, modifier = Modifier.testTag(exitTestTag)) {
                    Icon(Icons.Default.Close, contentDescription = exitContentDescription)
                }
            }
        }.map { it.measure(Constraints()) }
        val exitWidth = exitPlaceables.sumOf { it.width }

        // 3. Measure Overflow button
        val overflowButtonPlaceables = subcompose("overflowBtn") {
            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.testTag(overflowTestTag)) {
                Icon(Icons.Default.MoreVert, contentDescription = "Mehr Optionen")
            }
        }.map { it.measure(Constraints()) }
        val overflowButtonWidth = overflowButtonPlaceables.sumOf { it.width }

        // We need a minimum width for the Title
        val minTitleWidth = 0
        
        // Width available for actions
        var availableForActions = maxW - switcherWidth - exitWidth - minTitleWidth

        val inlineActions = mutableListOf<EditorAction>()
        overflowActions.clear() // In case it's called multiple times during measurement

        // Pre-sort actions
        val sortedActions = actions.sortedBy { it.priority }
        
        // Measure actions greedily
        var needOverflow = false
        val actionPlaceablesMap = mutableMapOf<EditorAction, androidx.compose.ui.layout.Placeable>()

        for (action in sortedActions) {
            if (action.alwaysOverflow) {
                overflowActions.add(action)
                needOverflow = true
                continue
            }
            
            val placeables = subcompose(action.key) {
                ActionIconButton(action = action)
            }.map { it.measure(Constraints()) }
            val actionW = placeables.sumOf { it.width }
            
            if (availableForActions >= actionW) {
                inlineActions.add(action)
                actionPlaceablesMap[action] = placeables.first()
                availableForActions -= actionW
            } else {
                overflowActions.add(action)
                needOverflow = true
            }
        }

        // If we need overflow, we must subtract the overflow button width.
        // This might cause the last inline action to not fit anymore.
        if (needOverflow) {
            availableForActions -= overflowButtonWidth
            while (availableForActions < 0 && inlineActions.isNotEmpty()) {
                val lastAction = inlineActions.removeLast()
                val removedW = actionPlaceablesMap[lastAction]?.width ?: 0
                availableForActions += removedW
                overflowActions.add(lastAction)
            }
        }
        
        // If we still don't have enough space for the Exit button, hide it and add to overflow
        var hideExit = false
        if (availableForActions < 0 && onExitEditor != null) {
            hideExit = true
            availableForActions += exitWidth
            
            val exitAction = EditorAction(
                key = "exit_overflow",
                icon = Icons.Default.Close,
                label = exitContentDescription,
                onClick = onExitEditor,
                priority = 999,
                alwaysOverflow = true
            )
            overflowActions.add(exitAction)
            needOverflow = true // If Exit goes to overflow, we definitely need the overflow menu
        }

        val activeExitPlaceables = if (hideExit) emptyList() else exitPlaceables
        
        // Re-sort overflow actions by their original priority
        overflowActions.sortBy { it.priority }

        // Measure actual title with the remaining space
        val usedSpace = switcherWidth + (if (!hideExit) exitWidth else 0) + (if (needOverflow) overflowButtonWidth else 0) + inlineActions.sumOf { actionPlaceablesMap[it]?.width ?: 0 }
        val finalTitleMaxWidth = (maxW - usedSpace).coerceAtLeast(0)
        
        val titlePlaceables = subcompose("title") {
            Row(verticalAlignment = Alignment.CenterVertically) { titleContent() }
        }.map { it.measure(Constraints(maxWidth = finalTitleMaxWidth)) }
        val titleWidth = titlePlaceables.sumOf { it.width }
        val titleHeight = titlePlaceables.maxOfOrNull { it.height } ?: 0

        val layoutHeight = maxOf(
            titleHeight,
            switcherPlaceables.maxOfOrNull { it.height } ?: 0,
            activeExitPlaceables.maxOfOrNull { it.height } ?: 0,
            overflowButtonPlaceables.maxOfOrNull { it.height } ?: 0,
            if (actionPlaceablesMap.isNotEmpty()) actionPlaceablesMap.values.maxOf { it.height } else 0
        )

        layout(maxW, layoutHeight) {
            var currentX = 0
            
            // 1. Title
            titlePlaceables.forEach {
                it.placeRelative(currentX, (layoutHeight - it.height) / 2)
                currentX += it.width
            }
            // 2. Switcher
            switcherPlaceables.forEach {
                it.placeRelative(currentX, (layoutHeight - it.height) / 2)
                currentX += it.width
            }
            
            // Actions, Overflow, Exit are pushed to the right
            var rightX = maxW
            
            // Place Exit (rightmost)
            activeExitPlaceables.reversed().forEach {
                rightX -= it.width
                it.placeRelative(maxOf(currentX, rightX), (layoutHeight - it.height) / 2)
            }
            
            // Place Overflow
            if (needOverflow) {
                overflowButtonPlaceables.forEach {
                    rightX -= it.width
                    it.placeRelative(maxOf(currentX, rightX), (layoutHeight - it.height) / 2)
                }
            }
            
            // Place Inline Actions
            inlineActions.reversed().forEach { action ->
                val p = actionPlaceablesMap[action]
                if (p != null) {
                    rightX -= p.width
                    p.placeRelative(maxOf(currentX, rightX), (layoutHeight - p.height) / 2)
                }
            }
        }
    }
    
    // Render the Overflow Menu outside of SubcomposeLayout coordinates using a Box
    if (menuExpanded) {
        Box(modifier = Modifier.width(0.dp)) {
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                val overflowSorted = overflowActions
                overflowSorted.forEach { action ->
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

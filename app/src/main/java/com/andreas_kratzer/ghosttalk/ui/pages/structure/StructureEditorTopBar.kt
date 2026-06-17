package com.andreas_kratzer.ghosttalk.ui.pages.structure

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.ui.components.EditorAction
import com.andreas_kratzer.ghosttalk.core.ui.components.EditorTopBar
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.ui.components.BulkActionTopBar
import com.andreas_kratzer.ghosttalk.core.ui.R as CoreR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StructureEditorTopBar(
    state: StructureEditorState,
    localName: String,
    isTablet: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onNavigateBack: () -> Unit,
    onExitEditor: (() -> Unit)?,
    modeSwitcher: (@Composable () -> Unit)?,
    onSplitWizardClick: () -> Unit,
    onUndoClick: () -> Unit,
    onRedoClick: () -> Unit,
    onShowIncomingLinks: () -> Unit,
    viewMode: StructureViewMode,
    onStyleToggleClick: (() -> Unit)?
) {
    if (state.isMultiSelectMode) {
        BulkActionTopBar(
            selectedCount = state.selectedCount,
            onCancel = { state.clearSelection() },
            onMove = { state.showBulkMoveDialog = true },
            onCopy = { state.showBulkCopyDialog = true },
            onDelete = { state.showBulkDeleteConfirm = true }
        )
    } else {
        val showStyleToggle = viewMode == StructureViewMode.CARDS || viewMode == StructureViewMode.GRAPH
        val actionsList = buildList {
            if (showStyleToggle) {
                add(
                    EditorAction(
                        key = "style_toggle",
                        icon = if (viewMode == StructureViewMode.GRAPH) GhostTalkIcons.Description else GhostTalkIcons.Sitemap,
                        label = if (viewMode == StructureViewMode.GRAPH) "Karten-Ansicht" else "Graph-Ansicht",
                        onClick = { onStyleToggleClick?.invoke() },
                        priority = 1,
                        testTag = "structure_editor_style_toggle"
                    )
                )
            }
            add(
                EditorAction(
                    key = "assistant",
                    icon = GhostTalkIcons.AutoAwesome,
                    label = "Assistent",
                    onClick = onSplitWizardClick,
                    tint = MaterialTheme.colorScheme.primary,
                    priority = 1,
                    testTag = "structure_editor_split_wizard_trigger_menu"
                )
            )
            add(
                EditorAction(
                    key = "undo",
                    icon = GhostTalkIcons.Undo,
                    label = stringResource(R.string.structure_action_undo),
                    onClick = onUndoClick,
                    enabled = canUndo,
                    tint = if (canUndo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    priority = 1,
                    testTag = "structure_editor_undo_button"
                )
            )
            add(
                EditorAction(
                    key = "multiselect",
                    icon = GhostTalkIcons.CheckCircle,
                    label = stringResource(R.string.bulk_action_toggle_multi_select),
                    onClick = {
                        state.isMultiSelectMode = !state.isMultiSelectMode
                        if (state.isMultiSelectMode) {
                            state.sidePanelTab = SidePanelTab.TREE
                        } else {
                            state.selection = emptyMap()
                        }
                    },
                    tint = if (state.isMultiSelectMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    priority = 1,
                    testTag = "structure_editor_multiselect_toggle"
                )
            )
            add(
                EditorAction(
                    key = "templates",
                    icon = Icons.AutoMirrored.Filled.List,
                    label = stringResource(R.string.template_panel_title),
                    onClick = {
                        if (isTablet) {
                            // Open the unified left panel on the Templates tab (toggle off if already there).
                            if (state.sidePanelExpanded && state.sidePanelTab == SidePanelTab.TEMPLATES) {
                                state.sidePanelExpanded = false
                            } else {
                                state.sidePanelExpanded = true
                                state.sidePanelTab = SidePanelTab.TEMPLATES
                            }
                        } else {
                            state.showTemplatesBottomSheet = true
                        }
                    },
                    tint = if (isTablet && state.sidePanelExpanded && state.sidePanelTab == SidePanelTab.TEMPLATES) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    priority = 1,
                    testTag = "structure_editor_templates_button"
                )
            )
            add(
                EditorAction(
                    key = "redo",
                    icon = GhostTalkIcons.Redo,
                    label = stringResource(R.string.history_redo_action),
                    onClick = onRedoClick,
                    enabled = canRedo,
                    tint = if (canRedo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    priority = 2,
                    testTag = "structure_editor_redo_button"
                )
            )
            if (!isTablet) {
                add(
                    EditorAction(
                        key = "tree_toggle",
                        icon = Icons.Default.Menu,
                        label = stringResource(R.string.structure_tree_toggle),
                        onClick = { state.showBottomSheet = true },
                        priority = 2
                    )
                )
            }
            add(
                EditorAction(
                    key = "history",
                    icon = GhostTalkIcons.History,
                    label = stringResource(R.string.history_panel_title),
                    onClick = { state.showHistoryPanel = true },
                    priority = 2,
                    testTag = "structure_editor_history_button"
                )
            )
            add(
                EditorAction(
                    key = "incoming_links",
                    icon = GhostTalkIcons.Link,
                    label = stringResource(R.string.page_incoming_links_title),
                    onClick = onShowIncomingLinks,
                    priority = 2,
                    testTag = "structure_editor_incoming_links"
                )
            )
            add(
                EditorAction(
                    key = "rename",
                    icon = GhostTalkIcons.Edit,
                    label = stringResource(R.string.page_dialog_rename_title),
                    onClick = { state.showRenameDialog = true },
                    priority = 2
                )
            )
            if (onExitEditor != null) {
                add(
                    EditorAction(
                        key = "exit",
                        icon = Icons.Default.Close,
                        label = stringResource(CoreR.string.editor_exit),
                        onClick = { onExitEditor() },
                        priority = 2,
                        testTag = "structure_editor_exit_button"
                    )
                )
            }
        }

        EditorTopBar(
            titleContent = {
                Text(
                    text = localName,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .testTag("structure_editor_title")
                )
            },
            onNavigateBack = onNavigateBack,
            onExitEditor = null,
            modeSwitcher = modeSwitcher,
            actions = actionsList,
            isTablet = isTablet,
            overflowTestTag = "structure_editor_overflow_menu_trigger"
        )
    }
}

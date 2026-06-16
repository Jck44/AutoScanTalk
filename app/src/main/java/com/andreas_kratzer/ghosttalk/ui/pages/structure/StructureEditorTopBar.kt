package com.andreas_kratzer.ghosttalk.ui.pages.structure

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.ui.components.EditorTopBar
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.ui.components.EditorAssistantButton
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
    onShowIncomingLinks: () -> Unit
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
            onExitEditor = null, // Disable top-right exit button in TopBar
            modeSwitcher = modeSwitcher,
            actions = {
                // Multi-select toggle button
                IconButton(
                    onClick = {
                        state.isMultiSelectMode = !state.isMultiSelectMode
                        if (state.isMultiSelectMode) {
                            state.templatesPanelExpanded = false
                        } else {
                            state.selection = emptyMap()
                        }
                    },
                    modifier = Modifier.testTag("structure_editor_multiselect_toggle")
                ) {
                    Icon(
                        imageVector = GhostTalkIcons.CheckCircle,
                        contentDescription = stringResource(R.string.bulk_action_toggle_multi_select),
                        tint = if (state.isMultiSelectMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Inline Action 1: Assistent (icon)
                EditorAssistantButton(
                    onClick = onSplitWizardClick,
                    compact = true,
                    testTag = "structure_editor_split_wizard_trigger_menu"
                )

                // Inline Action 2: Undo
                IconButton(
                    onClick = onUndoClick,
                    enabled = canUndo,
                    modifier = Modifier.testTag("structure_editor_undo_button")
                ) {
                    Icon(
                        imageVector = GhostTalkIcons.Undo,
                        contentDescription = stringResource(R.string.structure_action_undo),
                        tint = if (canUndo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    )
                }

                // Inline Action 3: Templates panel toggle
                IconButton(
                    onClick = {
                        if (isTablet) {
                            state.templatesPanelExpanded = !state.templatesPanelExpanded
                        } else {
                            state.showTemplatesBottomSheet = true
                        }
                    },
                    modifier = Modifier.testTag("structure_editor_templates_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = stringResource(R.string.template_panel_title),
                        tint = if (state.templatesPanelExpanded && isTablet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box {
                    IconButton(
                        onClick = { state.showOverflowMenu = true },
                        modifier = Modifier.testTag("structure_editor_overflow_menu_trigger")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.structure_more_options),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = state.showOverflowMenu,
                        onDismissRequest = { state.showOverflowMenu = false }
                    ) {
                        // Redo (Always in ⋮)
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.history_redo_action)) },
                            onClick = {
                                state.showOverflowMenu = false
                                onRedoClick()
                            },
                            enabled = canRedo,
                            leadingIcon = {
                                Icon(
                                    imageVector = GhostTalkIcons.Redo,
                                    contentDescription = null,
                                    tint = if (canRedo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                )
                            },
                            modifier = Modifier.testTag("structure_editor_redo_button")
                        )

                        // Tree Toggle (Always in ⋮ on phone)
                        if (!isTablet) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.structure_tree_toggle)) },
                                onClick = {
                                    state.showOverflowMenu = false
                                    state.showBottomSheet = true
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                        }

                        // Verlauf / History (Always in ⋮)
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.history_panel_title)) },
                            onClick = {
                                state.showOverflowMenu = false
                                state.showHistoryPanel = true
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = GhostTalkIcons.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.testTag("structure_editor_history_button")
                        )

                        // Eingehende Links / Incoming Links (Always in ⋮)
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.page_incoming_links_title)) },
                            onClick = {
                                state.showOverflowMenu = false
                                onShowIncomingLinks()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = GhostTalkIcons.Link,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.testTag("structure_editor_incoming_links")
                        )

                        // Umbenennen / Rename (Always in ⋮)
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.page_dialog_rename_title)) },
                            onClick = {
                                state.showOverflowMenu = false
                                state.showRenameDialog = true
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = GhostTalkIcons.Edit,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )

                        // Editor beenden / Exit Editor (Always in ⋮)
                        if (onExitEditor != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(CoreR.string.editor_exit)) },
                                onClick = {
                                    state.showOverflowMenu = false
                                    onExitEditor()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                modifier = Modifier.testTag("structure_editor_exit_button")
                            )
                        }
                    }
                }
            }
        )
    }
}

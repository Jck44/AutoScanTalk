package com.andreas_kratzer.ghosttalk.ui.pages.structure

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonToPageUseCase.MoveResult
import com.andreas_kratzer.ghosttalk.core.domain.pages.UsageLocation
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ButtonTemplate
import com.andreas_kratzer.ghosttalk.core.model.GridSettingsUpdate
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkDialog
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.ui.components.ValidatedTextField
import com.andreas_kratzer.ghosttalk.ui.pages.ButtonConfigDialog
import com.andreas_kratzer.ghosttalk.ui.pages.IncomingReferencesDialog
import com.andreas_kratzer.ghosttalk.ui.pages.TargetPageSelectionDialog
import com.andreas_kratzer.ghosttalk.ui.pages.history.EditIcon
import com.andreas_kratzer.ghosttalk.ui.pages.history.HistoryState
import com.andreas_kratzer.ghosttalk.ui.pages.pagesplit.PageSplitManualPromptDialog
import com.andreas_kratzer.ghosttalk.ui.pages.pagesplit.PageSplitOptInDialog
import com.andreas_kratzer.ghosttalk.ui.pages.resolveEditLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StructureEditorDialogs(
    state: StructureEditorState,
    pages: List<Page>,
    templates: List<PageTemplate>,
    startPageId: String?,
    pageNames: Map<String, String>,
    historyState: HistoryState,
    onSetAcceptedPageSplitOptIn: (Boolean) -> Unit,
    onGeneratePageSplitProposal: (String) -> Unit,
    onShouldFilterButtonFromSplit: (ButtonConfig?, String?, String) -> Boolean,
    onGeneratePageSplitPrompt: (List<String>) -> String,
    onParsePageSplitProposal: (String) -> Unit,
    onUpdateGridSettings: (String, GridSettingsUpdate) -> Unit,
    onUpdateButtonConfig: (String, Int, ButtonConfig?) -> Unit,
    onInsertButtonConfig: (String, Int, ButtonConfig, Boolean, (Boolean) -> Unit) -> Unit,
    onExecuteButtonAction: (ButtonConfig) -> Unit,
    onSuggestButtonLabel: ((ButtonConfig, (String) -> Unit) -> Unit)?,
    onUpdateButtonTemplate: (ButtonTemplate) -> Unit,
    onDeleteButtonTemplate: (ButtonTemplate) -> Unit,
    onSaveButtonAsTemplate: (String, ButtonConfig) -> Unit,
    onUndoTo: (Int) -> Unit,
    onAddConnectionBetween: (String, String) -> Unit,
    onBulkDelete: (Map<String, List<Int>>) -> Unit,
    onBulkMove: (Map<String, List<Int>>, String, Boolean, (MoveResult) -> Unit) -> Unit,
    onBulkCopy: (Map<String, List<Int>>, String, Boolean, (MoveResult) -> Unit) -> Unit,
    onCreatePage: (String, Int, Int, String?, (String) -> Unit) -> Unit,
    isTextCached: (String) -> Boolean,
    onPrefetchText: (String, () -> Unit) -> Unit,
    showSuccessSnackbarWithUndo: (Int) -> Unit,
    showSnackbar: (Int) -> Unit
) {
    val context = LocalContext.current
    val focusedPage = remember(pages, state.focusedPageId) { pages.find { it.id == state.focusedPageId } }

    // 1. Remove Connection Confirmation Dialog
    if (state.pageToRemoveConnectionByButtonIndex != null) {
        GhostTalkDialog(
            title = stringResource(R.string.structure_remove_connection_title),
            confirmText = stringResource(R.string.structure_action_remove),
            dismissText = stringResource(R.string.action_cancel),
            onConfirm = {
                val index = state.pageToRemoveConnectionByButtonIndex!!
                state.pageToRemoveConnectionByButtonIndex = null
                state.pageToRemoveConnectionTargetName = ""
                onUpdateButtonConfig(state.pageToRemoveConnectionFromPageId, index, null)
                state.pageToRemoveConnectionFromPageId = ""
                showSuccessSnackbarWithUndo(R.string.button_delete_success)
            },
            onDismiss = {
                state.pageToRemoveConnectionByButtonIndex = null
                state.pageToRemoveConnectionTargetName = ""
                state.pageToRemoveConnectionFromPageId = ""
            },
            content = {
                Text(stringResource(R.string.structure_remove_connection_msg, state.pageToRemoveConnectionTargetName))
            }
        )
    }

    // 2. Connect Orphan Confirmation Dialog
    if (state.orphanToConnectId != null) {
        val orphanName = pageNames[state.orphanToConnectId] ?: state.orphanToConnectId!!
        val currentPageName = pageNames[state.focusedPageId] ?: state.focusedPageId
        GhostTalkDialog(
            title = stringResource(R.string.structure_quick_fix_connect_orphan_title),
            confirmText = "",
            dismissText = stringResource(R.string.action_cancel),
            onConfirm = {},
            onDismiss = {
                state.orphanToConnectId = null
            },
            content = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.structure_quick_fix_connect_orphan_msg, orphanName),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.material3.Button(
                        onClick = {
                            val targetId = state.orphanToConnectId!!
                            state.orphanToConnectId = null
                            val startId = startPageId ?: pages.minByOrNull { it.orderIndex }?.id
                            if (startId != null) {
                                onAddConnectionBetween(startId, targetId)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.structure_quick_fix_connect_to_start))
                    }
                    androidx.compose.material3.Button(
                        onClick = {
                            val targetId = state.orphanToConnectId!!
                            state.orphanToConnectId = null
                            onAddConnectionBetween(state.focusedPageId, targetId)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.structure_quick_fix_connect_to_focused, currentPageName))
                    }
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            val targetId = state.orphanToConnectId!!
                            state.orphanToConnectId = null
                            state.navigateToPage(targetId)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.structure_view_page))
                    }
                }
            }
        )
    }

    // 3. Page Split Opt-In Dialog
    if (state.showOptInDialog) {
        PageSplitOptInDialog(
            onConfirmCloud = { rememberDecision ->
                state.showOptInDialog = false
                if (rememberDecision) {
                    onSetAcceptedPageSplitOptIn(true)
                }
                onGeneratePageSplitProposal(state.focusedPageId)
            },
            onConfirmManual = {
                state.showOptInDialog = false
                val page = pages.find { it.id == state.focusedPageId }
                if (page != null) {
                    val labels = page.buttonConfigs
                        .filter { !onShouldFilterButtonFromSplit(it, startPageId, page.id) }
                        .map { it!!.label }
                    state.manualPromptText = onGeneratePageSplitPrompt(labels)
                    state.showManualPromptDialog = true
                }
            },
            onDismiss = { state.showOptInDialog = false }
        )
    }

    // 4. Page Split Manual Prompt Dialog
    if (state.showManualPromptDialog) {
        PageSplitManualPromptDialog(
            promptText = state.manualPromptText,
            onEvaluateResponse = { response ->
                onParsePageSplitProposal(response)
                state.showManualPromptDialog = false
            },
            onDismiss = { state.showManualPromptDialog = false }
        )
    }

    // 5. Rename Dialog
    if (state.showRenameDialog) {
        var tempRenameName by remember(focusedPage?.name) { mutableStateOf(focusedPage?.name ?: "") }
        GhostTalkDialog(
            title = stringResource(R.string.page_dialog_rename_title),
            onDismiss = { state.showRenameDialog = false },
            onConfirm = {
                if (tempRenameName.isNotBlank()) {
                    if (focusedPage != null) {
                        onUpdateGridSettings(focusedPage.id, GridSettingsUpdate(name = tempRenameName))
                    }
                    state.showRenameDialog = false
                }
            },
            confirmText = stringResource(R.string.action_save),
            dismissText = stringResource(R.string.action_cancel)
        ) {
            ValidatedTextField(
                value = tempRenameName,
                onValueChange = { tempRenameName = it },
                isRequired = true,
                errorMessage = stringResource(R.string.error_page_name_required),
                placeholder = { Text(stringResource(R.string.page_name_label)) },
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .testTag("structure_editor_name_field")
            )
        }
    }

    // 6. Incoming References Dialog
    if (state.showIncomingLinksDialog) {
        val pageName = pageNames[state.focusedPageId] ?: ""
        IncomingReferencesDialog(
            pageName = pageName,
            usages = state.incomingUsages,
            onDismiss = { state.showIncomingLinksDialog = false },
            onNavigateToUsage = { usage ->
                state.showIncomingLinksDialog = false
                if (usage is UsageLocation.PageUsage) {
                    state.navigateToPage(usage.id)
                } else {
                    Toast.makeText(context, R.string.page_incoming_links_template_toast, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // 7. History Panel (Bottom Sheet)
    if (state.showHistoryPanel) {
        ModalBottomSheet(
            onDismissRequest = { state.showHistoryPanel = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.history_panel_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (historyState.entries.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.history_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(historyState.entries.size) { index ->
                            val entry = historyState.entries[index]
                            Surface(
                                onClick = {
                                    onUndoTo(index)
                                    state.showHistoryPanel = false
                                },
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    val icon = when (entry.icon) {
                                        EditIcon.DELETE -> Icons.Default.Close
                                        EditIcon.MOVE -> GhostTalkIcons.DragHandle
                                        EditIcon.EDIT -> GhostTalkIcons.AutoAwesome
                                        EditIcon.REORDER -> GhostTalkIcons.Sort
                                        EditIcon.PAGE -> GhostTalkIcons.GridView
                                        EditIcon.BOOK -> GhostTalkIcons.Book
                                    }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = resolveEditLabel(entry.label),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 8. Button Config Dialog (Editing)
    if (state.editTarget != null) {
        val (pageId, index) = state.editTarget!!
        val page = pages.find { it.id == pageId }
        val buttonConfig = page?.buttonConfigs?.getOrNull(index) ?: ButtonConfig()
        ButtonConfigDialog(
            buttonConfig = buttonConfig,
            pages = pages,
            templates = templates,
            defaultStartPageId = startPageId,
            onDismiss = { state.editTarget = null },
            onSave = { newConfig ->
                onUpdateButtonConfig(pageId, index, newConfig)
                state.editTarget = null
            },
            onTest = { config ->
                onExecuteButtonAction(config)
            },
            onSuggestLabel = onSuggestButtonLabel,
            onDelete = {
                onUpdateButtonConfig(pageId, index, null)
                state.editTarget = null
                showSuccessSnackbarWithUndo(R.string.button_delete_success)
            },
            onCreatePage = onCreatePage,
            isTextCached = isTextCached,
            onPrefetchText = onPrefetchText,
            onSaveAsTemplate = { config ->
                state.showSaveTemplateDialogConfig = config
                state.newTemplateName = config.label
            }
        )
    }

    // 9. Button Config Dialog (Adding)
    if (state.addTargetPageId != null) {
        val pageId = state.addTargetPageId!!
        val page = pages.find { it.id == pageId }
        ButtonConfigDialog(
            buttonConfig = ButtonConfig(id = java.util.UUID.randomUUID().toString()),
            pages = pages,
            templates = templates,
            defaultStartPageId = startPageId,
            onDismiss = { state.addTargetPageId = null },
            onSave = { newConfig ->
                val firstFreeIndex = page?.buttonConfigs?.indexOfFirst { it == null || !it.isActive } ?: -1
                val targetIndex = if (firstFreeIndex != -1) firstFreeIndex else page?.buttonConfigs?.size ?: 0
                onInsertButtonConfig(pageId, targetIndex, newConfig, false) { success ->
                    if (success) {
                        showSuccessSnackbarWithUndo(R.string.button_add_success)
                    } else {
                        showSnackbar(R.string.structure_page_full)
                    }
                }
                state.addTargetPageId = null
            },
            onTest = { config ->
                onExecuteButtonAction(config)
            },
            onSuggestLabel = onSuggestButtonLabel,
            onDelete = {
                state.addTargetPageId = null
            },
            onCreatePage = onCreatePage,
            isTextCached = isTextCached,
            onPrefetchText = onPrefetchText,
            onSaveAsTemplate = { config ->
                state.showSaveTemplateDialogConfig = config
                state.newTemplateName = config.label
            }
        )
    }

    // 10. Button Config Dialog (Editing Template)
    if (state.editingTemplate != null) {
        val template = state.editingTemplate!!
        ButtonConfigDialog(
            buttonConfig = template.buttonConfig,
            pages = pages,
            templates = templates,
            defaultStartPageId = startPageId,
            onDismiss = { state.editingTemplate = null },
            onSave = { newConfig ->
                onUpdateButtonTemplate(template.copy(name = newConfig.label, buttonConfig = newConfig))
                state.editingTemplate = null
            },
            onTest = { config ->
                onExecuteButtonAction(config)
            },
            onDelete = {
                onDeleteButtonTemplate(template)
                state.editingTemplate = null
            },
            onCreatePage = onCreatePage,
            isTextCached = isTextCached,
            onPrefetchText = onPrefetchText,
            onSaveAsTemplate = {}
        )
    }

    // 11. Save Button As Template Dialog
    if (state.showSaveTemplateDialogConfig != null) {
        GhostTalkDialog(
            title = stringResource(R.string.template_save_as_title),
            confirmText = stringResource(R.string.action_save),
            dismissText = stringResource(R.string.action_cancel),
            onConfirm = {
                val config = state.showSaveTemplateDialogConfig
                if (config != null && state.newTemplateName.isNotBlank()) {
                    onSaveButtonAsTemplate(state.newTemplateName, config)
                    Toast.makeText(context, R.string.editor_template_saved, Toast.LENGTH_SHORT).show()
                }
                state.showSaveTemplateDialogConfig = null
            },
            onDismiss = { state.showSaveTemplateDialogConfig = null },
            confirmEnabled = state.newTemplateName.isNotBlank(),
            content = {
                Column {
                    Text(stringResource(R.string.template_enter_name_prompt))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.newTemplateName,
                        onValueChange = { state.newTemplateName = it },
                        label = { Text(stringResource(R.string.template_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        )
    }

    // 12. Bulk Delete Dialog
    if (state.showBulkDeleteConfirm) {
        GhostTalkDialog(
            title = stringResource(R.string.bulk_action_delete),
            confirmText = stringResource(R.string.bulk_action_delete),
            dismissText = stringResource(R.string.action_cancel),
            isDestructive = true,
            onConfirm = {
                val listSelection = state.selection.mapValues { it.value.toList() }
                onBulkDelete(listSelection)
                showSuccessSnackbarWithUndo(R.string.button_delete_success)
                state.clearSelection()
                state.showBulkDeleteConfirm = false
            },
            onDismiss = { state.showBulkDeleteConfirm = false },
            content = {
                Text(stringResource(R.string.bulk_action_confirm_delete))
            }
        )
    }

    // 13. Bulk Move Target Page Selection Dialog
    if (state.showBulkMoveDialog) {
        TargetPageSelectionDialog(
            availablePages = pages,
            templates = templates,
            onCreatePage = onCreatePage,
            onDismiss = { state.showBulkMoveDialog = false },
            onPageSelected = { targetPage ->
                val targetPageId = targetPage.id
                val listSelection = state.selection.mapValues { it.value.toList() }
                onBulkMove(listSelection, targetPageId, false) { result ->
                    if (result is MoveResult.Success) {
                        showSuccessSnackbarWithUndo(R.string.button_move_success)
                        state.clearSelection()
                    } else if (result is MoveResult.TargetFull) {
                        showSnackbar(R.string.structure_target_full)
                    }
                }
                state.showBulkMoveDialog = false
            }
        )
    }

    // 14. Bulk Copy Target Page Selection Dialog
    if (state.showBulkCopyDialog) {
        TargetPageSelectionDialog(
            availablePages = pages,
            templates = templates,
            onCreatePage = onCreatePage,
            onDismiss = { state.showBulkCopyDialog = false },
            onPageSelected = { targetPage ->
                val targetPageId = targetPage.id
                val listSelection = state.selection.mapValues { it.value.toList() }
                onBulkCopy(listSelection, targetPageId, false) { result ->
                    if (result is MoveResult.Success) {
                        showSuccessSnackbarWithUndo(R.string.button_duplicate_success)
                        state.clearSelection()
                    } else if (result is MoveResult.TargetFull) {
                        showSnackbar(R.string.structure_target_full)
                    }
                }
                state.showBulkCopyDialog = false
            }
        )
    }

    // 15. Quick Connect Target Selection Dialog
    if (state.quickConnectForPageId != null) {
        val sourcePageId = state.quickConnectForPageId!!
        TargetPageSelectionDialog(
            availablePages = pages.filter { it.id != sourcePageId },
            templates = templates,
            onCreatePage = onCreatePage,
            onDismiss = { state.quickConnectForPageId = null },
            onPageSelected = { targetPage ->
                onAddConnectionBetween(sourcePageId, targetPage.id)
                state.quickConnectForPageId = null
            }
        )
    }
}

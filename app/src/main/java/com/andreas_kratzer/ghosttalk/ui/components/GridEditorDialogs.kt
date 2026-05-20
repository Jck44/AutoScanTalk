package com.andreas_kratzer.ghosttalk.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.cloud.GoogleHomeManager
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.GridItem
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import com.andreas_kratzer.ghosttalk.ui.pages.ButtonConfigDialog
import com.andreas_kratzer.ghosttalk.ui.pages.MoveHiddenPromptDialog
import com.andreas_kratzer.ghosttalk.ui.pages.TargetPageSelectionDialog
import com.andreas_kratzer.ghosttalk.ui.util.GridEditorActions
import com.andreas_kratzer.ghosttalk.ui.pages.PageViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun EditorDialogs(
    item: GridItem,
    actions: GridEditorActions,
    availablePages: List<Page>,
    templates: List<PageTemplate>,
    featureGuard: com.andreas_kratzer.ghosttalk.feature.settings.domain.FeatureGuard,
    editingRowIndex: Int?,
    showRowEditDialog: Boolean,
    selectedButtonIndex: Int?,
    showDialog: Boolean,
    showMoveDialog: Boolean,
    showDuplicateDialog: Boolean,
    isDuplicating: Boolean,
    showHiddenPrompt: com.andreas_kratzer.ghosttalk.domain.pages.MoveButtonToPageUseCase.MoveResult.NeedsConfirmation?,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    onShowMoveDialog: (Boolean) -> Unit,
    onShowDuplicateDialog: (Boolean) -> Unit,
    onShowHiddenPrompt: (com.andreas_kratzer.ghosttalk.domain.pages.MoveButtonToPageUseCase.MoveResult.NeedsConfirmation?) -> Unit,
    onDismissRowDialog: () -> Unit,
    onDismissButtonDialog: () -> Unit,
    onEditPage: ((String) -> Unit)?,
    googleHomeManager: GoogleHomeManager? = null,
    googleHomeProjectId: String = ""
) {
    val context = LocalContext.current
    val moveSuccessText = stringResource(R.string.button_move_success)
    val duplicateSuccessText = stringResource(R.string.button_duplicate_success)

    var showSaveTemplateDialogConfig by remember { mutableStateOf<ButtonConfig?>(null) }
    var newTemplateName by remember { mutableStateOf("") }
    
    if (showRowEditDialog && editingRowIndex != null) {
        RowEditDialog(
            initialName = item.rowNames.getOrNull(editingRowIndex) ?: stringResource(R.string.page_row_label).format(editingRowIndex + 1),
            onDismiss = onDismissRowDialog,
            onSave = { newName ->
                actions.updateRowName(item.id, editingRowIndex, newName)
                onDismissRowDialog()
            }
        )
    }

    if (showDialog && selectedButtonIndex != null) {
        val buttonConfig = item.buttonConfigs.getOrNull(selectedButtonIndex)
        ButtonConfigDialog(
            buttonConfig = buttonConfig ?: ButtonConfig(),
            pages = availablePages,
            templates = templates,
            onDismiss = onDismissButtonDialog,
            onSave = { newConfig ->
                actions.updateButtonConfig(item.id, selectedButtonIndex, newConfig)
            },
            onTest = { config ->
                actions.executeButtonAction(config)
            },
            onMove = {
                onShowMoveDialog(true)
                onDismissButtonDialog()
                android.widget.Toast.makeText(context, R.string.button_move_select_target, android.widget.Toast.LENGTH_SHORT).show()
            },
            onDuplicate = {
                onShowDuplicateDialog(true)
                onDismissButtonDialog()
                android.widget.Toast.makeText(context, R.string.button_duplicate_select_target, android.widget.Toast.LENGTH_SHORT).show()
            },
            onDelete = {
                actions.updateButtonConfig(item.id, selectedButtonIndex, null)
                onDismissButtonDialog()
            },
            onNavigateToPage = onEditPage,
            availableGeminiTools = actions.availableGeminiTools,
            onCreatePage = { name, r, c, t, callback ->
                val bookId = (item as? Page)?.bookId
                if (bookId != null) {
                    actions.createNewPage(name, r, c, bookId, t, callback)
                }
            },
            currentPageId = item.id,
            isTextCached = { actions.isTextCached(it) },
            onPrefetchText = { text, onComplete -> actions.prefetchText(text, onComplete) },
            googleHomeManager = googleHomeManager,
            googleHomeProjectId = googleHomeProjectId,
            featureGuard = featureGuard,
            onPlayTts = { text, onDone -> actions.speakTtsPreview(text, onDone) },
            onStopTts = { actions.stopTtsPreview() },
            isTtsElevenLabs = { actions.isTtsElevenLabs() },
            onSaveAsTemplate = { config ->
                showSaveTemplateDialogConfig = config
                newTemplateName = config.label
                onDismissButtonDialog()
            }
        )
    }

    if (showMoveDialog && selectedButtonIndex != null) {
        TargetPageSelectionDialog(
            availablePages = availablePages.filter { it.id != item.id },
            onPageSelected = { targetPage ->
                val sourceIndex = selectedButtonIndex
                onShowMoveDialog(false)
                // We DON'T clear selectedButtonIndex yet, because we might need it for forceMove
                actions.moveButtonToPage(item.id, sourceIndex, targetPage.id) { result -> 
                    when (result) {
                        is com.andreas_kratzer.ghosttalk.domain.pages.MoveButtonToPageUseCase.MoveResult.Success -> {
                            onDismissButtonDialog()
                            android.widget.Toast.makeText(context, R.string.button_move_success, android.widget.Toast.LENGTH_SHORT).show()
                        }
                        is com.andreas_kratzer.ghosttalk.domain.pages.MoveButtonToPageUseCase.MoveResult.NeedsConfirmation -> {
                            onShowHiddenPrompt(result)
                        }
                        is com.andreas_kratzer.ghosttalk.domain.pages.MoveButtonToPageUseCase.MoveResult.TargetFull -> {
                            onDismissButtonDialog()
                            scope.launch {
                                snackbarHostState.showSnackbar("Zielseite ist voll")
                            }
                        }
                        else -> {
                            onDismissButtonDialog()
                        }
                    }
                }
            },
            onDismiss = { 
                onShowMoveDialog(false)
                onDismissButtonDialog()
            }
        )
    }

    if (showDuplicateDialog && selectedButtonIndex != null) {
        TargetPageSelectionDialog(
            availablePages = availablePages, // Allow duplicating to same page too? The requirement says "in gleicher Art und Weise eine Zielseite ausgewählt werden". For Move we filter it out.
            // Actually, if I duplicate to the same page, I need to make sure I find a DIFFERENT slot.
            // MoveButtonToPageUseCase.execute fails if fromPageId == toPageId.
            // DuplicateButtonToPageUseCase.execute should also probably handle same page if we want that.
            // BUT for now I'll follow the "gleiche Art und Weise" which likely means other pages.
            onPageSelected = { targetPage ->
                val sourceIndex = selectedButtonIndex
                onShowDuplicateDialog(false)
                actions.duplicateButtonToPage(item.id, sourceIndex, targetPage.id) { result -> 
                    when (result) {
                        is com.andreas_kratzer.ghosttalk.domain.pages.MoveButtonToPageUseCase.MoveResult.Success -> {
                            onDismissButtonDialog()
                            android.widget.Toast.makeText(context, R.string.button_duplicate_success, android.widget.Toast.LENGTH_SHORT).show()
                        }
                        is com.andreas_kratzer.ghosttalk.domain.pages.MoveButtonToPageUseCase.MoveResult.NeedsConfirmation -> {
                            onShowHiddenPrompt(result)
                        }
                        is com.andreas_kratzer.ghosttalk.domain.pages.MoveButtonToPageUseCase.MoveResult.TargetFull -> {
                            onDismissButtonDialog()
                            scope.launch {
                                snackbarHostState.showSnackbar("Zielseite ist voll")
                            }
                        }
                        else -> {
                            onDismissButtonDialog()
                        }
                    }
                }
            },
            onDismiss = { 
                onShowDuplicateDialog(false)
                onDismissButtonDialog()
            }
        )
    }

    if (showHiddenPrompt != null) {
        val promptData = showHiddenPrompt
        MoveHiddenPromptDialog(
            requiredRows = if (promptData.requiredRows > promptData.targetPage.rows) promptData.requiredRows else 0,
            requiredCols = if (promptData.requiredCols > promptData.targetPage.columns) promptData.requiredCols else 0,
            onConfirm = {
                val targetId = promptData.targetPage.id
                onShowHiddenPrompt(null)
                if (isDuplicating) {
                    actions.duplicateButtonToPage(item.id, selectedButtonIndex!!, targetId, forceMove = true) { result ->
                        onDismissButtonDialog()
                        if (result is com.andreas_kratzer.ghosttalk.domain.pages.MoveButtonToPageUseCase.MoveResult.Success) {
                            scope.launch {
                                snackbarHostState.showSnackbar(duplicateSuccessText)
                            }
                        }
                    }
                } else {
                    actions.moveButtonToPage(item.id, selectedButtonIndex!!, targetId, forceMove = true) { result ->
                        onDismissButtonDialog()
                        if (result is com.andreas_kratzer.ghosttalk.domain.pages.MoveButtonToPageUseCase.MoveResult.Success) {
                            scope.launch {
                                snackbarHostState.showSnackbar(moveSuccessText)
                            }
                        }
                    }
                }
            },
            onDismiss = { 
                onShowHiddenPrompt(null)
                onDismissButtonDialog()
            }
        )
    }

    if (showSaveTemplateDialogConfig != null) {
        AlertDialog(
            onDismissRequest = { showSaveTemplateDialogConfig = null },
            title = { Text("Als Vorlage speichern") },
            text = {
                Column {
                    Text("Geben Sie einen Namen für die Button-Vorlage ein:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newTemplateName,
                        onValueChange = { newTemplateName = it },
                        label = { Text("Name der Vorlage") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val config = showSaveTemplateDialogConfig
                        if (config != null && newTemplateName.isNotBlank()) {
                            (actions as? PageViewModel)?.saveButtonAsTemplate(newTemplateName, config)
                            android.widget.Toast.makeText(context, "Vorlage gespeichert", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        showSaveTemplateDialogConfig = null
                    }
                ) {
                    Text("Speichern")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveTemplateDialogConfig = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

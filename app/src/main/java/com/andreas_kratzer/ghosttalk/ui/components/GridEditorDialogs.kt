package com.andreas_kratzer.ghosttalk.ui.components

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun EditorDialogs(
    item: GridItem,
    actions: GridEditorActions,
    availablePages: List<Page>,
    templates: List<PageTemplate>,
    featureGuard: com.andreas_kratzer.ghosttalk.feature.settings.domain.FeatureGuard,
    isExecuting: Boolean,
    editingRowIndex: Int?,
    showRowEditDialog: Boolean,
    selectedButtonIndex: Int?,
    showDialog: Boolean,
    showMoveDialog: Boolean,
    showHiddenPrompt: com.andreas_kratzer.ghosttalk.domain.pages.MoveButtonToPageUseCase.MoveResult.NeedsConfirmation?,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    onShowMoveDialog: (Boolean) -> Unit,
    onShowHiddenPrompt: (com.andreas_kratzer.ghosttalk.domain.pages.MoveButtonToPageUseCase.MoveResult.NeedsConfirmation?) -> Unit,
    onDismissRowDialog: () -> Unit,
    onDismissButtonDialog: () -> Unit,
    onEditPage: ((String) -> Unit)?,
    googleHomeManager: GoogleHomeManager? = null,
    googleHomeProjectId: String = ""
) {
    val context = LocalContext.current
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
                onDismissButtonDialog()
            },
            onTest = { config ->
                actions.executeButtonAction(config)
            },
            onMove = {
                onShowMoveDialog(true)
                onDismissButtonDialog()
                android.widget.Toast.makeText(context, R.string.button_move_select_target, android.widget.Toast.LENGTH_SHORT).show()
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
            googleHomeManager = googleHomeManager,
            googleHomeProjectId = googleHomeProjectId,
            featureGuard = featureGuard
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

    if (showHiddenPrompt != null) {
        val promptData = showHiddenPrompt
        MoveHiddenPromptDialog(
            requiredRows = if (promptData.requiredRows > promptData.targetPage.rows) promptData.requiredRows else 0,
            requiredCols = if (promptData.requiredCols > promptData.targetPage.columns) promptData.requiredCols else 0,
            onConfirm = {
                val targetId = promptData.targetPage.id
                onShowHiddenPrompt(null)
                actions.moveButtonToPage(item.id, selectedButtonIndex!!, targetId, forceMove = true) { result ->
                    onDismissButtonDialog()
                    if (result is com.andreas_kratzer.ghosttalk.domain.pages.MoveButtonToPageUseCase.MoveResult.Success) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Button verschoben")
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
}

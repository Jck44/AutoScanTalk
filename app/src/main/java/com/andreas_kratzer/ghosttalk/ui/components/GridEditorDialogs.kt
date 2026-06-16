@file:Suppress("UNUSED_VALUE")
package com.andreas_kratzer.ghosttalk.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.cloud.PhilipsHueManager
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.GridItem
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkDialog
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
    editingRowIndex: Int?,
    showRowEditDialog: Boolean,
    selectedButtonIndex: Int?,
    selectedButtonIndices: Set<Int> = emptySet(),
    onClearSelectedButtonIndices: () -> Unit = {},
    showDialog: Boolean,
    showMoveDialog: Boolean,
    showDuplicateDialog: Boolean,
    isDuplicating: Boolean,
    showHiddenPrompt: com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonToPageUseCase.MoveResult.NeedsConfirmation?,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    onShowMoveDialog: (Boolean) -> Unit,
    onShowDuplicateDialog: (Boolean) -> Unit,
    onShowHiddenPrompt: (com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonToPageUseCase.MoveResult.NeedsConfirmation?) -> Unit,
    onDismissRowDialog: () -> Unit,
    onDismissButtonDialog: () -> Unit,
    onEditPage: ((String, String?) -> Unit)?,
    philipsHueManager: PhilipsHueManager? = null
) {
    val context = LocalContext.current
    val moveSuccessText = stringResource(R.string.button_move_success)
    val duplicateSuccessText = stringResource(R.string.button_duplicate_success)

    val spotifyPlaylists by actions.spotifyPlaylists.collectAsState(emptyList())
    val isSpotifyLoadingPlaylists by actions.isLoadingPlaylists.collectAsState(false)
    val spotifyUserDisplayName by actions.spotifyUserDisplayName.collectAsState(null)

    val showSaveTemplateDialogConfig = remember { mutableStateOf<ButtonConfig?>(null) }
    var newTemplateName by remember { mutableStateOf("") }
    
    if (showRowEditDialog && editingRowIndex != null) {
        val defaultRowName = if (item.id.startsWith("static_row_")) "Statische Zeile" 
                             else stringResource(R.string.page_row_label).format(editingRowIndex + 1)
        RowEditDialog(
            initialName = item.rowNames.getOrNull(editingRowIndex) ?: defaultRowName,
            onDismiss = onDismissRowDialog,
            onSave = { newName ->
                actions.updateRowName(item.id, editingRowIndex, newName)
                onDismissRowDialog()
            },
            onSuggestName = if (actions.isGeminiEnabled) { callback ->
                actions.suggestRowName(item.id, editingRowIndex, callback)
            } else null
        )
    }

    val buttonHistoryList by actions.buttonHistory.collectAsState(emptyList())
    val pageMetricsMap by actions.pageMetrics.collectAsState(emptyMap())
    val shortcutRecommendations by actions.shortcutRecommendations.collectAsState(emptyList())

    if (showDialog && selectedButtonIndex != null) {
        val buttonConfig = item.buttonConfigs.getOrNull(selectedButtonIndex) ?: ButtonConfig()
        val buttonMetrics = pageMetricsMap[buttonConfig.id]
        val buttonHistory = buttonHistoryList.filter { it.buttonId == buttonConfig.id }
        val buttonRecommendations = shortcutRecommendations.filter { it.targetButtonConfig.id == buttonConfig.id }

        ButtonConfigDialog(
            buttonConfig = buttonConfig,
            pages = availablePages,
            templates = templates,
            defaultStartPageId = actions.settingsRepository.defaultStartPageId,
            onDismiss = onDismissButtonDialog,
            onSave = { newConfig ->
                actions.updateButtonConfig(item.id, selectedButtonIndex, newConfig)
            },
            onTest = { config ->
                actions.executeButtonAction(config)
            },
            onSuggestLabel = if (actions.isGeminiEnabled) { config, callback ->
                actions.suggestButtonLabel(config, callback)
            } else null,
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
                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    val result = snackbarHostState.showSnackbar(
                        message = "Button gelöscht",
                        actionLabel = "Rückgängig",
                        duration = androidx.compose.material3.SnackbarDuration.Long
                    )
                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                        actions.undo { undoMsg ->
                            scope.launch {
                                snackbarHostState.showSnackbar(undoMsg)
                            }
                        }
                    }
                }
            },
            onNavigateToPage = { targetPageId ->
                onEditPage?.invoke(targetPageId, buttonConfig.id)
            },
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
            philipsHueManager = philipsHueManager,
            hueBridgeIp = actions.settingsRepository.hueBridgeIp,
            hueUsername = actions.settingsRepository.hueUsername,
            hueCachedDevices = actions.settingsRepository.hueCachedDevices,
            onRefreshHueCache = { silent, callback -> actions.refreshHueDevicesCache(silent, callback) },
            featureGuard = featureGuard,
            onPlayTts = { text, onDone -> actions.speakTtsPreview(text, onDone) },
            onStopTts = { actions.stopTtsPreview() },
            isTtsElevenLabs = { actions.isTtsElevenLabs() },
            spotifyPlaylists = spotifyPlaylists,
            isLoadingSpotifyPlaylists = isSpotifyLoadingPlaylists,
            spotifyUserDisplayName = spotifyUserDisplayName,
            onConnectSpotify = { actions.connectSpotify(context) },
            onDisconnectSpotify = { actions.disconnectSpotify() },
            onLoadSpotifyPlaylists = { actions.loadSpotifyPlaylists() },
            onSaveAsTemplate = { config ->
                onDismissButtonDialog()
                newTemplateName = config.label
                showSaveTemplateDialogConfig.value = config
            },
            metrics = buttonMetrics,
            historyEvents = buttonHistory,
            recommendations = buttonRecommendations,
            loadMarkovSuccessors = { actions.getMarkovSuccessors(it) },
            onApplyRecommendation = { recommendation ->
                actions.applyShortcutRecommendation(recommendation) { success, msg ->
                    if (success) {
                        onDismissButtonDialog()
                    }
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    if (showMoveDialog && (selectedButtonIndex != null || selectedButtonIndices.isNotEmpty())) {
        TargetPageSelectionDialog(
            availablePages = availablePages.filter { it.id != item.id },
            templates = templates,
            onCreatePage = { name, r, c, t, callback ->
                val bookId = (item as? Page)?.bookId
                if (bookId != null) {
                    actions.createNewPage(name, r, c, bookId, t, callback)
                }
            },
            onPageSelected = { targetPage ->
                val sourceIndex = selectedButtonIndex
                onShowMoveDialog(false)
                if (sourceIndex != null) {
                    // We DON'T clear selectedButtonIndex yet, because we might need it for forceMove
                    actions.moveButtonToPage(item.id, listOf(sourceIndex), targetPage.id) { result -> 
                        when (result) {
                            is com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonToPageUseCase.MoveResult.Success -> {
                                onDismissButtonDialog()
                                scope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    val result = snackbarHostState.showSnackbar(
                                        message = moveSuccessText,
                                        actionLabel = "Rückgängig",
                                        duration = androidx.compose.material3.SnackbarDuration.Long
                                    )
                                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                        actions.undo { undoMsg ->
                                            scope.launch {
                                                snackbarHostState.showSnackbar(undoMsg)
                                            }
                                        }
                                    }
                                }
                            }
                            is com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonToPageUseCase.MoveResult.NeedsConfirmation -> {
                                onShowHiddenPrompt(result)
                            }
                            is com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonToPageUseCase.MoveResult.TargetFull -> {
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
                } else if (selectedButtonIndices.isNotEmpty()) {
                    actions.moveButtonToPage(item.id, selectedButtonIndices.toList(), targetPage.id) { result ->
                        when (result) {
                            is com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonToPageUseCase.MoveResult.Success -> {
                                onClearSelectedButtonIndices()
                                onDismissButtonDialog()
                                scope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    val snackResult = snackbarHostState.showSnackbar(
                                        message = moveSuccessText,
                                        actionLabel = "Rückgängig",
                                        duration = androidx.compose.material3.SnackbarDuration.Long
                                    )
                                    if (snackResult == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                        actions.undo { undoMsg ->
                                            scope.launch {
                                                snackbarHostState.showSnackbar(undoMsg)
                                            }
                                        }
                                    }
                                }
                            }
                            is com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonToPageUseCase.MoveResult.NeedsConfirmation -> {
                                onShowHiddenPrompt(result)
                            }
                            is com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonToPageUseCase.MoveResult.TargetFull -> {
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
                }
            },
            onDismiss = { 
                onShowMoveDialog(false)
                onDismissButtonDialog()
            }
        )
    }

    if (showDuplicateDialog && (selectedButtonIndex != null || selectedButtonIndices.isNotEmpty())) {
        TargetPageSelectionDialog(
            availablePages = availablePages,
            templates = templates,
            onCreatePage = { name, r, c, t, callback ->
                val bookId = (item as? Page)?.bookId
                if (bookId != null) {
                    actions.createNewPage(name, r, c, bookId, t, callback)
                }
            },
            currentPageId = item.id,
            onPageSelected = { targetPage ->
                val sourceIndex = selectedButtonIndex
                onShowDuplicateDialog(false)
                if (sourceIndex != null) {
                    actions.duplicateButtonToPage(item.id, listOf(sourceIndex), targetPage.id) { result -> 
                        when (result) {
                            is com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonToPageUseCase.MoveResult.Success -> {
                                onDismissButtonDialog()
                                scope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    val result = snackbarHostState.showSnackbar(
                                        message = duplicateSuccessText,
                                        actionLabel = "Rückgängig",
                                        duration = androidx.compose.material3.SnackbarDuration.Long
                                    )
                                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                        actions.undo { undoMsg ->
                                            scope.launch {
                                                snackbarHostState.showSnackbar(undoMsg)
                                            }
                                        }
                                    }
                                }
                            }
                            is com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonToPageUseCase.MoveResult.NeedsConfirmation -> {
                                onShowHiddenPrompt(result)
                            }
                            is com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonToPageUseCase.MoveResult.TargetFull -> {
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
                } else if (selectedButtonIndices.isNotEmpty()) {
                    actions.duplicateButtonToPage(item.id, selectedButtonIndices.toList(), targetPage.id) { result ->
                        when (result) {
                            is com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonToPageUseCase.MoveResult.Success -> {
                                onClearSelectedButtonIndices()
                                onDismissButtonDialog()
                                scope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    val snackResult = snackbarHostState.showSnackbar(
                                        message = duplicateSuccessText,
                                        actionLabel = "Rückgängig",
                                        duration = androidx.compose.material3.SnackbarDuration.Long
                                    )
                                    if (snackResult == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                        actions.undo { undoMsg ->
                                            scope.launch {
                                                snackbarHostState.showSnackbar(undoMsg)
                                            }
                                        }
                                    }
                                }
                            }
                            is com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonToPageUseCase.MoveResult.NeedsConfirmation -> {
                                onShowHiddenPrompt(result)
                            }
                            is com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonToPageUseCase.MoveResult.TargetFull -> {
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
                val indices = if (selectedButtonIndex != null) listOf(selectedButtonIndex) else selectedButtonIndices.toList()
                if (isDuplicating) {
                    actions.duplicateButtonToPage(item.id, indices, targetId, forceMove = true) { result ->
                        onClearSelectedButtonIndices()
                        onDismissButtonDialog()
                        if (result is com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonToPageUseCase.MoveResult.Success) {
                            scope.launch {
                                snackbarHostState.showSnackbar(duplicateSuccessText)
                            }
                        }
                    }
                } else {
                    actions.moveButtonToPage(item.id, indices, targetId, forceMove = true) { result ->
                        onClearSelectedButtonIndices()
                        onDismissButtonDialog()
                        if (result is com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonToPageUseCase.MoveResult.Success) {
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

    if (showSaveTemplateDialogConfig.value != null) {
        GhostTalkDialog(
            title = "Als Vorlage speichern",
            onDismiss = { showSaveTemplateDialogConfig.value = null },
            confirmText = "Speichern",
            onConfirm = {
                val config = showSaveTemplateDialogConfig.value
                if (config != null && newTemplateName.isNotBlank()) {
                    actions.saveButtonAsTemplate(newTemplateName, config)
                    android.widget.Toast.makeText(context, "Vorlage gespeichert", android.widget.Toast.LENGTH_SHORT).show()
                }
                showSaveTemplateDialogConfig.value = null
            },
            dismissText = "Abbrechen"
        ) {
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
        }
    }
}

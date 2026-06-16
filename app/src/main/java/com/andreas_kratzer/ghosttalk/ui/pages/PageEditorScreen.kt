package com.andreas_kratzer.ghosttalk.ui.pages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.domain.pages.UsageLocation
import com.andreas_kratzer.ghosttalk.core.model.GridSettingsUpdate
import com.andreas_kratzer.ghosttalk.core.ui.components.EditorTopBar
import com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkDialog
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.ui.components.EditorAssistantButton
import com.andreas_kratzer.ghosttalk.ui.components.GridEditorContent
import com.andreas_kratzer.ghosttalk.ui.components.ValidatedTextField
import com.andreas_kratzer.ghosttalk.ui.pages.history.EditIcon
import com.andreas_kratzer.ghosttalk.ui.pages.history.EditLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.andreas_kratzer.ghosttalk.core.ui.R as CoreR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageEditorScreen(
    pageId: String,
    initialButtonId: String? = null,
    pageViewModel: PageViewModel,
    gridEditorViewModel: GridEditorViewModel = hiltViewModel(),
    pageSplitViewModel: PageSplitViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onEditPage: ((String, String?) -> Unit)? = null,
    onExitEditor: (() -> Unit)? = null,
    onOpenStructureEditor: ((String, Boolean) -> Unit)? = null,
    modeSwitcher: (@Composable () -> Unit)? = null,
    initialOpenAssistant: Boolean = false,
    onAssistantConsumed: () -> Unit = {}
) {
    val allPages by pageViewModel.allPages.collectAsState()
    val unfilteredPages by pageViewModel.unfilteredPages.collectAsState()
    val bookDefaultScanPattern by pageViewModel.defaultScanPattern.collectAsState()
    val page = allPages.find { it.id == pageId }

    LaunchedEffect(initialOpenAssistant) {
        if (initialOpenAssistant) {
            onAssistantConsumed()
        }
    }

    LaunchedEffect(page) {
        if (page != null) {
            pageViewModel.loadPage(page)
        }
    }

    if (page == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.page_editor_loading))
        }
        return
    }

    var localName by remember(page.name) { mutableStateOf(page.name) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var tempName by remember(localName) { mutableStateOf(localName) }
    var showIncomingLinksDialog by remember { mutableStateOf(false) }
    var incomingUsages by remember { mutableStateOf<List<UsageLocation>>(emptyList()) }
    val context = LocalContext.current
    
    // Layout & Page Split Dialog States
    val showLayoutAssistantDialog = rememberSaveable(initialOpenAssistant) { mutableStateOf(initialOpenAssistant) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showHistoryPanel by remember { mutableStateOf(false) }

    val isMultiSelectModeState = rememberSaveable { mutableStateOf(false) }
    val selectedButtonIndicesState = rememberSaveable { mutableStateOf(emptySet<Int>()) }
    val showMoveDialogState = rememberSaveable { mutableStateOf(false) }
    val showDuplicateDialogState = rememberSaveable { mutableStateOf(false) }
    val showConfirmDeleteDialogState = rememberSaveable { mutableStateOf(false) }
    
    var isMultiSelectMode by isMultiSelectModeState
    var selectedButtonIndices by selectedButtonIndicesState

    val handleNavigateBack = {
        if (localName.isNotBlank()) {
            onNavigateBack()
        }
    }

    BackHandler {
        handleNavigateBack()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val resolvedPage by pageViewModel.resolvedPage.collectAsState()
    LaunchedEffect(resolvedPage) {
        gridEditorViewModel.setResolvedPage(resolvedPage)
    }

    LaunchedEffect(localName) {
        if (localName != page.name && localName.isNotBlank()) {
            delay(500)
            gridEditorViewModel.updateGridSettings(
                itemId = page.id,
                update = GridSettingsUpdate(name = localName)
            )
        }
    }

    Scaffold(
        topBar = {
            if (isMultiSelectMode) {
                com.andreas_kratzer.ghosttalk.ui.components.BulkActionTopBar(
                    selectedCount = selectedButtonIndices.size,
                    onCancel = {
                        isMultiSelectMode = false
                        selectedButtonIndices = emptySet()
                    },
                    onMove = { showMoveDialogState.value = true },
                    onCopy = { showDuplicateDialogState.value = true },
                    onDelete = { showConfirmDeleteDialogState.value = true }
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
                                .testTag("page_editor_title")
                        )
                    },
                    onNavigateBack = handleNavigateBack,
                    onExitEditor = null, // Disable top-right exit button in TopBar
                modeSwitcher = modeSwitcher,
                actions = {
                    val isEditPreviewActive by pageViewModel.isEditPreviewActive.collectAsState()
                    val historyState by gridEditorViewModel.historyState.collectAsState()

                    // Inline Action 1: Assistent (icon)
                    EditorAssistantButton(
                        onClick = {
                            showLayoutAssistantDialog.value = true
                        },
                        compact = true,
                        testTag = "page_editor_split_wizard_trigger_menu"
                    )

                    // Inline Action: Multi-Select Mode Toggle
                    if (!isEditPreviewActive) {
                        IconButton(
                            onClick = {
                                isMultiSelectMode = !isMultiSelectMode
                                if (!isMultiSelectMode) {
                                    selectedButtonIndices = emptySet()
                                }
                            },
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .background(
                                    if (isMultiSelectMode) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons.CheckCircle,
                                contentDescription = stringResource(R.string.bulk_action_toggle_multi_select),
                                tint = if (isMultiSelectMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Inline Action 2: Undo
                    IconButton(
                        onClick = {
                            gridEditorViewModel.undo { message ->
                                coroutineScope.launch { snackbarHostState.showSnackbar(message) }
                            }
                        },
                        enabled = historyState.canUndo,
                        modifier = Modifier.testTag("page_editor_undo_button")
                    ) {
                        val nextUndoLabel = historyState.entries.firstOrNull()?.let { resolveEditLabel(it.label) } ?: ""
                        val tooltipText = if (nextUndoLabel.isNotEmpty()) {
                            stringResource(R.string.history_undo_tooltip, nextUndoLabel)
                        } else {
                            stringResource(R.string.structure_action_undo)
                        }
                        Icon(
                            imageVector = GhostTalkIcons.Undo,
                            contentDescription = tooltipText,
                            tint = if (historyState.canUndo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier.testTag("page_editor_overflow_menu_trigger")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Mehr Optionen",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            // Redo (Always in ⋮)
                            DropdownMenuItem(
                                text = {
                                    val nextRedoLabel = historyState.nextRedoLabel?.let { resolveEditLabel(it) } ?: ""
                                    val labelText = if (nextRedoLabel.isNotEmpty()) {
                                        stringResource(R.string.history_redo_tooltip, nextRedoLabel)
                                    } else {
                                        stringResource(R.string.history_redo_action)
                                    }
                                    Text(labelText)
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    gridEditorViewModel.redo { message ->
                                        coroutineScope.launch { snackbarHostState.showSnackbar(message) }
                                    }
                                },
                                enabled = historyState.canRedo,
                                leadingIcon = {
                                    Icon(
                                        imageVector = GhostTalkIcons.Redo,
                                        contentDescription = null,
                                        tint = if (historyState.canRedo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                    )
                                },
                                modifier = Modifier.testTag("page_editor_redo_button")
                            )

                            // Verlauf / History (Always in ⋮)
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.history_panel_title)) },
                                onClick = {
                                    showOverflowMenu = false
                                    showHistoryPanel = true
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = GhostTalkIcons.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                modifier = Modifier.testTag("page_editor_history_button")
                            )

                            // Vorschau / Preview (Always in ⋮)
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.page_editor_preview_toggle)) },
                                onClick = {
                                    showOverflowMenu = false
                                    pageViewModel.toggleEditPreviewActive()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isEditPreviewActive) GhostTalkIcons.Visibility else GhostTalkIcons.VisibilityOff,
                                        contentDescription = null,
                                        tint = if (isEditPreviewActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                modifier = Modifier.testTag("page_editor_preview_toggle")
                            )

                            // Eingehende Links / Incoming Links (Always in ⋮)
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.page_incoming_links_title)) },
                                onClick = {
                                    showOverflowMenu = false
                                    coroutineScope.launch {
                                        incomingUsages = pageViewModel.getPageUsages(page.id)
                                        showIncomingLinksDialog = true
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = GhostTalkIcons.Link,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                modifier = Modifier.testTag("page_editor_incoming_links")
                            )

                            // Analytics Overlay (Always in ⋮)
                            val isAnalyticsEnabled by pageViewModel.isAnalyticsOverlayEnabled.collectAsState()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.page_editor_analytics_toggle)) },
                                onClick = {
                                    showOverflowMenu = false
                                    pageViewModel.toggleAnalyticsOverlay()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = GhostTalkIcons.BarChart,
                                        contentDescription = null,
                                        tint = if (isAnalyticsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                modifier = Modifier.testTag("page_editor_analytics_toggle_menu")
                            )

                            // Umbenennen / Rename (Always in ⋮)
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.page_dialog_rename_title)) },
                                onClick = {
                                    showOverflowMenu = false
                                    showRenameDialog = true
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
                                        showOverflowMenu = false
                                        onExitEditor()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    modifier = Modifier.testTag("page_editor_exit_button")
                                )
                            }
                        }
                    }
                }) // Close EditorTopBar
            } // Close `if` / `else` for topBar
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        val templates by pageViewModel.templates.collectAsState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val startPageId by pageViewModel.defaultStartPageIdFlow.collectAsState(initial = null)
            Box(modifier = Modifier.weight(1f)) {
                GridEditorContent(
                    item = page,
                    actions = gridEditorViewModel,
                    availablePages = unfilteredPages,
                    templates = templates,
                    featureGuard = pageViewModel.featureGuard,
                    bookDefaultScanPattern = bookDefaultScanPattern,
                    paddingValues = PaddingValues(0.dp),
                    onEditPage = onEditPage,
                    initialButtonId = initialButtonId,
                    defaultStartPageId = startPageId,
                    isMultiSelectModeState = isMultiSelectModeState,
                    selectedButtonIndicesState = selectedButtonIndicesState,
                    showMoveDialogState = showMoveDialogState,
                    showDuplicateDialogState = showDuplicateDialogState,
                    showConfirmDeleteDialogState = showConfirmDeleteDialogState
                )
            }
        }

        // Render Layout & Split Dialogs
        if (showLayoutAssistantDialog.value) {
            PageLayoutAssistantDialog(
                page = page,
                pageSplitViewModel = pageSplitViewModel,
                onStartPageSplit = {
                    onOpenStructureEditor?.invoke(page.id, true)
                },
                onStartMagicCleanup = {
                    pageSplitViewModel.magicCleanup(page.id) {
                        coroutineScope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "Magische Bereinigung erfolgreich abgeschlossen!",
                                actionLabel = "Rückgängig",
                                duration = SnackbarDuration.Long
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                gridEditorViewModel.undo { undoMsg ->
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(undoMsg)
                                    }
                                }
                            }
                        }
                    }
                },
                onDismiss = { showLayoutAssistantDialog.value = false }
            )
        }

        val magicCleanupProgress by pageSplitViewModel.magicCleanupProgress.collectAsState()

        magicCleanupProgress?.let { progressMessage ->
            Dialog(
                onDismissRequest = {},
                properties = DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false
                )
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier.width(280.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Magische Bereinigung läuft...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = progressMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        if (showIncomingLinksDialog) {
            IncomingReferencesDialog(
                pageName = page.name,
                usages = incomingUsages,
                onDismiss = { showIncomingLinksDialog = false },
                onNavigateToUsage = { usage ->
                    showIncomingLinksDialog = false
                    if (usage is UsageLocation.PageUsage) {
                        onEditPage?.invoke(usage.id, null)
                    } else {
                        android.widget.Toast.makeText(context, R.string.page_incoming_links_template_toast, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        if (showRenameDialog) {
            GhostTalkDialog(
                title = stringResource(R.string.page_dialog_rename_title),
                onDismiss = { showRenameDialog = false },
                onConfirm = {
                    if (tempName.isNotBlank()) {
                        localName = tempName
                        gridEditorViewModel.updateGridSettings(
                            itemId = page.id,
                            update = GridSettingsUpdate(name = tempName)
                        )
                        showRenameDialog = false
                    }
                },
                confirmText = stringResource(R.string.action_save),
                dismissText = stringResource(R.string.action_cancel)
            ) {
                ValidatedTextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    isRequired = true,
                    errorMessage = stringResource(R.string.error_page_name_required),
                    placeholder = { Text(stringResource(R.string.page_name_label)) },
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .testTag("page_editor_name_field")
                )
            }
        }

        if (showHistoryPanel) {
            val historyState by gridEditorViewModel.historyState.collectAsState()
            androidx.compose.material3.ModalBottomSheet(
                onDismissRequest = { showHistoryPanel = false }
            ) {
                androidx.compose.foundation.layout.Column(
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
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(historyState.entries.size) { index ->
                                val entry = historyState.entries[index]
                                androidx.compose.material3.Surface(
                                    onClick = {
                                        gridEditorViewModel.undoTo(index)
                                        showHistoryPanel = false
                                    },
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    androidx.compose.foundation.layout.Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
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
    }
}

@Composable
fun resolveEditLabel(label: EditLabel): String {
    val context = LocalContext.current
    val formatArgs = label.args.map { arg ->
        if (arg is EditLabel) {
            resolveEditLabel(arg)
        } else {
            arg
        }
    }.toTypedArray()
    return if (label.isPlural) {
        context.resources.getQuantityString(label.resId, label.quantity, *formatArgs)
    } else {
        context.getString(label.resId, *formatArgs)
    }
}

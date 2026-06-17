package com.andreas_kratzer.ghosttalk.ui.pages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.domain.pages.BookNavigationGraph
import com.andreas_kratzer.ghosttalk.core.domain.pages.UsageLocation
import com.andreas_kratzer.ghosttalk.core.model.GridSettingsUpdate
import com.andreas_kratzer.ghosttalk.core.ui.components.EditorAction
import com.andreas_kratzer.ghosttalk.core.ui.components.EditorTopBar
import com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkDialog
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.ui.components.GridEditorContent
import com.andreas_kratzer.ghosttalk.ui.components.ValidatedTextField
import com.andreas_kratzer.ghosttalk.ui.pages.history.EditIcon
import com.andreas_kratzer.ghosttalk.ui.pages.history.EditLabel
import com.andreas_kratzer.ghosttalk.ui.pages.structure.StructureLegend
import com.andreas_kratzer.ghosttalk.ui.pages.structure.WarningBadges
import com.andreas_kratzer.ghosttalk.ui.pages.structure.rememberStructureProblems
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Suppress("AssignedValueIsNeverRead")
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
    onAssistantConsumed: () -> Unit = {},
    isEmbedded: Boolean = false
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

    // Structure warnings (orphan / dead-end) for the page being edited, so the
    // same ⚠/⛔ hints are visible in the grid view too — not only in the graph.
    val structureStartPageId by pageViewModel.defaultStartPageIdFlow.collectAsState(initial = null)
    val navigationGraph = remember(unfilteredPages, structureStartPageId) {
        BookNavigationGraph.from(unfilteredPages, structureStartPageId)
    }
    val structureProblems = rememberStructureProblems(navigationGraph)
    val isPageOrphan = page.id in structureProblems.orphans
    val isPageDeadEnd = page.id in structureProblems.deadEnds
    
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

    val density = androidx.compose.ui.platform.LocalDensity.current
    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    val isTablet = remember(density, windowInfo) {
        with(density) { windowInfo.containerSize.width.toDp() } >= 600.dp
    }

    val handleNavigateBack = {
        // Back must always work — never silently swallow it. A blank name simply
        // isn't persisted (see LaunchedEffect below); fall back to the saved name.
        if (localName.isBlank()) localName = page.name
        onNavigateBack()
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
        contentWindowInsets = if (isEmbedded) WindowInsets(0.dp) else ScaffoldDefaults.contentWindowInsets,
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
                val isEditPreviewActive by pageViewModel.isEditPreviewActive.collectAsState()
                val historyState by gridEditorViewModel.historyState.collectAsState()
                val isAnalyticsEnabled by pageViewModel.isAnalyticsOverlayEnabled.collectAsState()

                val nextUndoLabel = historyState.entries.firstOrNull()?.let { resolveEditLabel(it.label) } ?: ""
                val undoTooltipText = if (nextUndoLabel.isNotEmpty()) {
                    stringResource(R.string.history_undo_tooltip, nextUndoLabel)
                } else {
                    stringResource(R.string.structure_action_undo)
                }

                val nextRedoLabel = historyState.nextRedoLabel?.let { resolveEditLabel(it) } ?: ""
                val redoTooltipText = if (nextRedoLabel.isNotEmpty()) {
                    stringResource(R.string.history_redo_tooltip, nextRedoLabel)
                } else {
                    stringResource(R.string.history_redo_action)
                }

                val actionsList = buildList {
                    add(
                        EditorAction(
                            key = "assistant",
                            icon = GhostTalkIcons.AutoAwesome,
                            label = "Assistent",
                            onClick = { showLayoutAssistantDialog.value = true },
                            tint = MaterialTheme.colorScheme.primary,
                            priority = 1,
                            testTag = "page_editor_split_wizard_trigger_menu"
                        )
                    )
                    if (!isEditPreviewActive) {
                        add(
                            EditorAction(
                                key = "multiselect",
                                icon = GhostTalkIcons.CheckCircle,
                                label = stringResource(R.string.bulk_action_toggle_multi_select),
                                onClick = {
                                    isMultiSelectMode = !isMultiSelectMode
                                    if (!isMultiSelectMode) {
                                        selectedButtonIndices = emptySet()
                                    }
                                },
                                tint = if (isMultiSelectMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                backgroundColor = if (isMultiSelectMode) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                priority = 1
                            )
                        )
                    }
                    add(
                        EditorAction(
                            key = "undo",
                            icon = GhostTalkIcons.Undo,
                            label = undoTooltipText,
                            onClick = {
                                gridEditorViewModel.undo { message ->
                                    coroutineScope.launch { snackbarHostState.showSnackbar(message) }
                                }
                            },
                            enabled = historyState.canUndo,
                            tint = if (historyState.canUndo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                            priority = 1,
                            testTag = "page_editor_undo_button"
                        )
                    )
                    add(
                        EditorAction(
                            key = "redo",
                            icon = GhostTalkIcons.Redo,
                            label = redoTooltipText,
                            onClick = {
                                gridEditorViewModel.redo { message ->
                                    coroutineScope.launch { snackbarHostState.showSnackbar(message) }
                                }
                            },
                            enabled = historyState.canRedo,
                            tint = if (historyState.canRedo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                            priority = 2,
                            testTag = "page_editor_redo_button"
                        )
                    )
                    add(
                        EditorAction(
                            key = "history",
                            icon = GhostTalkIcons.History,
                            label = stringResource(R.string.history_panel_title),
                            onClick = { showHistoryPanel = true },
                            priority = 2,
                            alwaysOverflow = true,
                            testTag = "page_editor_history_button"
                        )
                    )
                    add(
                        EditorAction(
                            key = "preview",
                            icon = if (isEditPreviewActive) GhostTalkIcons.Visibility else GhostTalkIcons.VisibilityOff,
                            label = stringResource(R.string.page_editor_preview_toggle),
                            onClick = { pageViewModel.toggleEditPreviewActive() },
                            tint = if (isEditPreviewActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            priority = 2,
                            testTag = "page_editor_preview_toggle"
                        )
                    )
                    add(
                        EditorAction(
                            key = "incoming_links",
                            icon = GhostTalkIcons.Link,
                            label = stringResource(R.string.page_incoming_links_title),
                            onClick = {
                                coroutineScope.launch {
                                    incomingUsages = pageViewModel.getPageUsages(page.id)
                                    showIncomingLinksDialog = true
                                }
                            },
                            priority = 2,
                            alwaysOverflow = true,
                            testTag = "page_editor_incoming_links"
                        )
                    )
                    add(
                        EditorAction(
                            key = "analytics",
                            icon = GhostTalkIcons.BarChart,
                            label = stringResource(R.string.page_editor_analytics_toggle),
                            onClick = { pageViewModel.toggleAnalyticsOverlay() },
                            tint = if (isAnalyticsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            priority = 2,
                            testTag = "page_editor_analytics_toggle_menu"
                        )
                    )
                    add(
                        EditorAction(
                            key = "rename",
                            icon = GhostTalkIcons.Edit,
                            label = stringResource(R.string.page_dialog_rename_title),
                            onClick = { showRenameDialog = true },
                            priority = 2,
                            alwaysOverflow = true
                        )
                    )
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
                                .testTag("page_editor_title")
                        )
                        WarningBadges(
                            isOrphan = isPageOrphan,
                            isDeadEnd = isPageDeadEnd,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    },
                    onNavigateBack = handleNavigateBack,
                    onExitEditor = onExitEditor,
                    modeSwitcher = modeSwitcher,
                    actions = actionsList,
                    overflowTestTag = "page_editor_overflow_menu_trigger",
                    windowInsets = if (isEmbedded) WindowInsets(0.dp) else TopAppBarDefaults.windowInsets
                ) // Close EditorTopBar
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

                StructureLegend(
                    showOrphan = isPageOrphan,
                    showDeadEnd = isPageDeadEnd,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
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
            ModalBottomSheet(
                onDismissRequest = { showHistoryPanel = false }
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
    }
}

@Composable
fun resolveEditLabel(label: EditLabel): String {
    val formatArgs = label.args.map { arg ->
        if (arg is EditLabel) {
            resolveEditLabel(arg)
        } else {
            arg
        }
    }.toTypedArray()
    return if (label.isPlural) {
        androidx.compose.ui.res.pluralStringResource(label.resId, label.quantity, *formatArgs)
    } else {
        stringResource(label.resId, *formatArgs)
    }
}

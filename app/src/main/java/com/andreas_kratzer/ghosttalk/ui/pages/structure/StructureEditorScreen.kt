package com.andreas_kratzer.ghosttalk.ui.pages.structure

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.domain.pages.BookNavigationGraph
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.GridSettingsUpdate
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.ui.components.EditorTopBar
import com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkDialog
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.ui.components.EditorAssistantButton
import com.andreas_kratzer.ghosttalk.ui.components.ValidatedTextField
import com.andreas_kratzer.ghosttalk.ui.pages.GridEditorViewModel
import com.andreas_kratzer.ghosttalk.ui.pages.IncomingReferencesDialog
import com.andreas_kratzer.ghosttalk.ui.pages.PageSplitViewModel
import com.andreas_kratzer.ghosttalk.ui.pages.PageViewModel
import com.andreas_kratzer.ghosttalk.ui.pages.history.EditIcon
import com.andreas_kratzer.ghosttalk.ui.pages.pagesplit.PageSplitManualPromptDialog
import com.andreas_kratzer.ghosttalk.ui.pages.pagesplit.PageSplitOptInDialog
import com.andreas_kratzer.ghosttalk.ui.pages.resolveEditLabel
import kotlinx.coroutines.launch
import com.andreas_kratzer.ghosttalk.core.ui.R as CoreR
import androidx.compose.runtime.CompositionLocalProvider
import com.andreas_kratzer.ghosttalk.ui.components.DragDropContainer
import com.andreas_kratzer.ghosttalk.ui.components.rememberDragDropState
import com.andreas_kratzer.ghosttalk.ui.components.LocalDragDropState
import com.andreas_kratzer.ghosttalk.ui.components.StructureButtonDrag
import com.andreas_kratzer.ghosttalk.ui.components.StructureNodeTarget
import com.andreas_kratzer.ghosttalk.ui.components.StructureDeleteTarget
import com.andreas_kratzer.ghosttalk.ui.components.StructureSlotTarget
import com.andreas_kratzer.ghosttalk.ui.components.SplitWizardButtonDrag
import com.andreas_kratzer.ghosttalk.ui.components.SplitWizardCategoryTarget
import com.andreas_kratzer.ghosttalk.ui.components.SplitWizardUnassignedTarget
import com.andreas_kratzer.ghosttalk.ui.templates.ButtonTemplatesPanel
import com.andreas_kratzer.ghosttalk.ui.pages.ButtonConfigDialog
import androidx.compose.material.icons.filled.List
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.height

enum class StructureViewMode {
    CARDS, GRAPH
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StructureEditorScreen(
    pageViewModel: PageViewModel,
    gridEditorViewModel: GridEditorViewModel,
    initialFocusedPageId: String? = null,
    initialTriggerSplit: Boolean = false,
    pageSplitViewModel: PageSplitViewModel = hiltViewModel(),
    onEditPageInGrid: (pageId: String) -> Unit,
    onNavigateBack: () -> Unit,
    viewMode: StructureViewMode = StructureViewMode.CARDS,
    modeSwitcher: (@Composable () -> Unit)? = null,
    onExitEditor: (() -> Unit)? = null
) {
    val pages by pageViewModel.unfilteredPages.collectAsState()
    val templates by pageViewModel.templates.collectAsState(initial = emptyList())
    val startPageId by pageViewModel.defaultStartPageIdFlow.collectAsState(initial = null)
    val activeBookId by pageViewModel.activeBookId.collectAsState(initial = null)
    val context = LocalContext.current
    val dragDropState = rememberDragDropState()

    val graph = remember(pages, startPageId) {
        BookNavigationGraph.from(pages, startPageId)
    }

    val pageNames = remember(pages) {
        pages.associate { it.id to it.name }
    }

    val initialFocusedId = remember(pages, startPageId, initialFocusedPageId) {
        initialFocusedPageId?.takeIf { id -> pages.any { it.id == id } }
            ?: startPageId?.takeIf { id -> pages.any { it.id == id } }
            ?: pages.minByOrNull { it.orderIndex }?.id
            ?: ""
    }

    var focusedPageId by rememberSaveable {
        mutableStateOf(initialFocusedPageId ?: "")
    }

    var focusHistory by rememberSaveable {
        mutableStateOf(emptyList<String>())
    }

    var sidePanelExpanded by rememberSaveable { mutableStateOf(true) }

    val navigateToPage = { newPageId: String ->
        if (newPageId != focusedPageId && newPageId.isNotBlank()) {
            focusHistory = focusHistory + focusedPageId
            focusedPageId = newPageId
        }
    }

    BackHandler(enabled = focusHistory.isNotEmpty()) {
        val lastPageId = focusHistory.lastOrNull()
        if (lastPageId != null) {
            focusedPageId = lastPageId
            focusHistory = focusHistory.dropLast(1)
        }
    }

    val pageSplitProposal by pageSplitViewModel.pageSplitProposal.collectAsState()
    val isPageSplitLoading by pageSplitViewModel.isPageSplitLoading.collectAsState()

    val showOptInDialog = remember { mutableStateOf(false) }
    val showManualPromptDialog = remember { mutableStateOf(false) }
    var manualPromptText by remember { mutableStateOf("") }

    var hasTriggeredInitialSplit by rememberSaveable(initialFocusedId) { mutableStateOf(false) }
    LaunchedEffect(initialTriggerSplit, initialFocusedId, pages) {
        if (initialTriggerSplit && !hasTriggeredInitialSplit && initialFocusedId.isNotBlank() && pages.isNotEmpty()) {
            hasTriggeredInitialSplit = true
            val page = pages.find { it.id == initialFocusedId }
            if (page != null) {
                val accepted = pageSplitViewModel.hasAcceptedPageSplitOptIn
                if (accepted) {
                    pageSplitViewModel.generatePageSplitProposal(page.id)
                } else {
                    showOptInDialog.value = true
                }
            }
        }
    }

    LaunchedEffect(initialFocusedId, pages) {
        if (focusedPageId.isBlank() || pages.none { it.id == focusedPageId }) {
            focusedPageId = initialFocusedId
        }
    }

    val focusedPage = remember(pages, focusedPageId) { pages.find { it.id == focusedPageId } }
    var localName by remember(focusedPage?.name) { mutableStateOf(focusedPage?.name ?: "") }
    var showRenameDialog by remember { mutableStateOf(false) }
    var tempName by remember(localName) { mutableStateOf(localName) }

    LaunchedEffect(localName) {
        if (focusedPage != null && localName != focusedPage.name && localName.isNotBlank()) {
            kotlinx.coroutines.delay(500)
            gridEditorViewModel.updateGridSettings(
                itemId = focusedPage.id,
                update = GridSettingsUpdate(name = localName)
            )
        }
    }

    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var showBottomSheet by remember { mutableStateOf(false) }
    var templatesPanelExpanded by rememberSaveable { mutableStateOf(false) }
    var showTemplatesBottomSheet by remember { mutableStateOf(false) }

    var editTarget by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var addTargetPageId by remember { mutableStateOf<String?>(null) }
    var editingTemplate by remember { mutableStateOf<com.andreas_kratzer.ghosttalk.core.model.ButtonTemplate?>(null) }
    val showSaveTemplateDialogConfig = remember { mutableStateOf<com.andreas_kratzer.ghosttalk.core.model.ButtonConfig?>(null) }
    var newTemplateName by remember { mutableStateOf("") }

    var onSplitWizardDropCallback by remember { mutableStateOf<((SplitWizardButtonDrag, Any) -> Unit)?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    var showHistoryPanel by remember { mutableStateOf(false) }
    var showIncomingLinksDialog by remember { mutableStateOf(false) }
    var incomingUsages by remember { mutableStateOf<List<com.andreas_kratzer.ghosttalk.core.domain.pages.UsageLocation>>(emptyList()) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<Map<String, Set<Int>>>(emptyMap()) }
    var showBulkMoveDialog by remember { mutableStateOf(false) }
    var showBulkCopyDialog by remember { mutableStateOf(false) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    val selectedCount = remember(selection) { selection.values.sumOf { it.size } }
    val clearSelection = { selection = emptyMap(); isMultiSelectMode = false }

    var pageToRemoveConnectionFromPageId by remember { mutableStateOf("") }
    var pageToRemoveConnectionByButtonIndex by remember { mutableStateOf<Int?>(null) }
    var pageToRemoveConnectionTargetName by remember { mutableStateOf("") }
    var orphanToConnectId by remember { mutableStateOf<String?>(null) }

    val showSuccessSnackbarWithUndo = { messageResId: Int ->
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val app = pageViewModel.getApplication<android.app.Application>()
            val snackbarResult = snackbarHostState.showSnackbar(
                message = app.getString(messageResId),
                actionLabel = app.getString(R.string.structure_action_undo),
                duration = SnackbarDuration.Long
            )
            if (snackbarResult == SnackbarResult.ActionPerformed) {
                gridEditorViewModel.undo { undoMsg ->
                    scope.launch {
                        snackbarHostState.showSnackbar(undoMsg)
                    }
                }
            }
        }
    }

    val onMoveButton = { fromPageId: String, fromIndex: Int, targetPageId: String ->
        gridEditorViewModel.moveButtonToPage(
            fromPageId = fromPageId,
            fromIndices = listOf(fromIndex),
            toPageId = targetPageId,
            forceMove = false
        ) { result ->
            when (result) {
                is com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonToPageUseCase.MoveResult.Success -> {
                    showSuccessSnackbarWithUndo(R.string.button_move_success)
                }
                is com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonToPageUseCase.MoveResult.TargetFull -> {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = pageViewModel.getApplication<android.app.Application>().getString(R.string.structure_target_full)
                        )
                    }
                }
                else -> {}
            }
        }
    }
    val onTemplateClick: (com.andreas_kratzer.ghosttalk.core.model.ButtonTemplate) -> Unit = { template ->
        val page = pages.find { it.id == focusedPageId }
        val firstFreeIndex = page?.buttonConfigs?.indexOfFirst { it == null || !it.isActive } ?: -1
        val targetIndex = if (firstFreeIndex != -1) firstFreeIndex else page?.buttonConfigs?.size ?: 0
        val newConfig = template.buttonConfig.copy(
            id = java.util.UUID.randomUUID().toString()
        )
        gridEditorViewModel.insertButtonConfig(focusedPageId, targetIndex, newConfig, false) { success ->
            if (success) {
                showSuccessSnackbarWithUndo(R.string.template_insert_success)
            } else {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = pageViewModel.getApplication<android.app.Application>().getString(R.string.structure_page_full)
                    )
                }
            }
        }
    }

    val onDrop: (Any, Any) -> Unit = { item, target ->
        if (item is SplitWizardButtonDrag) {
            onSplitWizardDropCallback?.invoke(item, target)
        } else {
            StructureDragDropHandler.handleDrop(
                draggedItem = item,
                target = target,
                onMoveButton = { fromPageId, fromIndex, targetPageId ->
                    onMoveButton(fromPageId, fromIndex, targetPageId)
                },
                onMoveButtonToSlot = { fromPageId, fromIndex, targetPageId, targetIndex ->
                    if (fromPageId == targetPageId) {
                        gridEditorViewModel.moveButton(fromPageId, fromIndex, targetIndex)
                    } else {
                        val draggedButton = pages.find { it.id == fromPageId }?.buttonConfigs?.getOrNull(fromIndex)
                        val buttonId = draggedButton?.id
                        gridEditorViewModel.moveButtonToPage(
                            fromPageId = fromPageId,
                            fromIndices = listOf(fromIndex),
                            toPageId = targetPageId,
                            forceMove = false
                        ) { result ->
                            if (result is com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonToPageUseCase.MoveResult.Success) {
                                val actualPlacedIdx = result.toPage.buttonConfigs.indexOfFirst { it?.id == buttonId }
                                if (actualPlacedIdx != -1 && actualPlacedIdx != targetIndex) {
                                    gridEditorViewModel.moveButton(targetPageId, actualPlacedIdx, targetIndex)
                                }
                                showSuccessSnackbarWithUndo(R.string.button_move_success)
                            } else if (result is com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonToPageUseCase.MoveResult.TargetFull) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = pageViewModel.getApplication<android.app.Application>().getString(R.string.structure_target_full)
                                    )
                                }
                            }
                        }
                    }
                },
                onDeleteButton = { pageId, index ->
                    gridEditorViewModel.updateButtonConfig(pageId, index, null)
                    showSuccessSnackbarWithUndo(R.string.button_delete_success)
                },
                onInsertTemplate = { pageId, targetIndex, template ->
                    val newConfig = template.buttonConfig.copy(
                        id = java.util.UUID.randomUUID().toString()
                    )
                    val actualIndex = if (targetIndex != -1) targetIndex else {
                        val page = pages.find { it.id == pageId }
                        val firstFreeIndex = page?.buttonConfigs?.indexOfFirst { it == null || !it.isActive } ?: -1
                        if (firstFreeIndex != -1) firstFreeIndex else page?.buttonConfigs?.size ?: 0
                    }
                    gridEditorViewModel.insertButtonConfig(pageId, actualIndex, newConfig, false) { success ->
                        if (success) {
                            showSuccessSnackbarWithUndo(R.string.template_insert_success)
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = pageViewModel.getApplication<android.app.Application>().getString(R.string.structure_page_full)
                                )
                            }
                        }
                    }
                },
                showSnackbar = { msg ->
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                }
            )
        }
    }

    val performAddConnection = { targetPageId: String ->
        val currentPage = pages.find { it.id == focusedPageId }
        val targetPageName = pageNames[targetPageId] ?: targetPageId
        if (currentPage != null) {
            val alreadyConnected = graph.outgoing[focusedPageId].orEmpty().any { it.targetPageId == targetPageId }
            if (alreadyConnected) {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = pageViewModel.getApplication<android.app.Application>().getString(R.string.structure_connection_exists)
                    )
                }
            } else {
                val firstFreeIndex = currentPage.buttonConfigs.indexOfFirst { it == null || !it.isActive }
                val targetIndex = if (firstFreeIndex != -1) firstFreeIndex else currentPage.buttonConfigs.size
                val newConfig = ButtonConfig(
                    id = java.util.UUID.randomUUID().toString(),
                    label = targetPageName,
                    spokenText = "Öffne $targetPageName",
                    buttonAction = NavigateToPageButtonAction(pageId = targetPageId),
                    auditoryCue = com.andreas_kratzer.ghosttalk.core.model.AuditoryCue.TextToSpeechCue("Öffne $targetPageName")
                )
                gridEditorViewModel.insertButtonConfig(focusedPageId, targetIndex, newConfig, false) { success ->
                    if (success) {
                        showSuccessSnackbarWithUndo(R.string.button_add_success)
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = pageViewModel.getApplication<android.app.Application>().getString(R.string.structure_page_full)
                            )
                        }
                    }
                }
            }
        }
    }

    val onAddConnection = { targetPageId: String ->
        performAddConnection(targetPageId)
    }

    val onCreatePage: (String, Int, Int, String?, (String) -> Unit) -> Unit = { name, rows, cols, templateId, callback ->
        gridEditorViewModel.createNewPage(
            name = name,
            rows = rows,
            columns = cols,
            bookId = activeBookId ?: "book-default",
            templateId = templateId,
            onCreated = callback
        )
    }

    if (pages.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Scaffold(
        topBar = {
            if (isMultiSelectMode) {
                com.andreas_kratzer.ghosttalk.ui.components.BulkActionTopBar(
                    selectedCount = selectedCount,
                    onCancel = { clearSelection() },
                    onMove = { showBulkMoveDialog = true },
                    onCopy = { showBulkCopyDialog = true },
                    onDelete = { showBulkDeleteConfirm = true }
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
                        val historyState by gridEditorViewModel.historyState.collectAsState()

                        // Multi-select toggle button
                        IconButton(
                            onClick = {
                                isMultiSelectMode = !isMultiSelectMode
                                if (isMultiSelectMode) {
                                    templatesPanelExpanded = false
                                } else {
                                    selection = emptyMap()
                                }
                            },
                            modifier = Modifier.testTag("structure_editor_multiselect_toggle")
                        ) {
                            Icon(
                                imageVector = GhostTalkIcons.CheckCircle,
                                contentDescription = stringResource(R.string.bulk_action_toggle_multi_select),
                                tint = if (isMultiSelectMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Inline Action 1: Assistent (icon)
                        EditorAssistantButton(
                        onClick = {
                            val accepted = pageSplitViewModel.hasAcceptedPageSplitOptIn
                            if (accepted) {
                                pageSplitViewModel.generatePageSplitProposal(focusedPageId)
                            } else {
                                showOptInDialog.value = true
                            }
                        },
                        compact = true,
                        testTag = "structure_editor_split_wizard_trigger_menu"
                    )

                    // Inline Action 2: Undo
                    IconButton(
                        onClick = {
                            gridEditorViewModel.undo { message ->
                                scope.launch { snackbarHostState.showSnackbar(message) }
                            }
                        },
                        enabled = historyState.canUndo,
                        modifier = Modifier.testTag("structure_editor_undo_button")
                    ) {
                        Icon(
                            imageVector = GhostTalkIcons.Undo,
                            contentDescription = stringResource(R.string.structure_action_undo),
                            tint = if (historyState.canUndo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        )
                    }

                    // Inline Action 3: Templates panel toggle
                    IconButton(
                        onClick = {
                            if (isTablet) {
                                templatesPanelExpanded = !templatesPanelExpanded
                            } else {
                                showTemplatesBottomSheet = true
                            }
                        },
                        modifier = Modifier.testTag("structure_editor_templates_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = stringResource(R.string.template_panel_title),
                            tint = if (templatesPanelExpanded && isTablet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier.testTag("structure_editor_overflow_menu_trigger")
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
                                text = { Text(stringResource(R.string.history_redo_action)) },
                                onClick = {
                                    showOverflowMenu = false
                                    gridEditorViewModel.redo { message ->
                                        scope.launch { snackbarHostState.showSnackbar(message) }
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
                                modifier = Modifier.testTag("structure_editor_redo_button")
                            )

                            // Tree Toggle (Always in ⋮ on phone)
                            if (!isTablet) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.structure_tree_toggle)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        showBottomSheet = true
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
                                modifier = Modifier.testTag("structure_editor_history_button")
                            )

                            // Eingehende Links / Incoming Links (Always in ⋮)
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.page_incoming_links_title)) },
                                onClick = {
                                    showOverflowMenu = false
                                    scope.launch {
                                        incomingUsages = pageViewModel.getPageUsages(focusedPageId)
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
                                modifier = Modifier.testTag("structure_editor_incoming_links")
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
                                    modifier = Modifier.testTag("structure_editor_exit_button")
                                )
                            }
                        }
                    }
                }
            )
        }
    },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        DragDropContainer(
            state = dragDropState,
            onDrop = onDrop,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            floatingPreview = { draggedItem ->
                val label = when (draggedItem) {
                    is com.andreas_kratzer.ghosttalk.ui.components.StructureButtonDrag -> draggedItem.label
                    is com.andreas_kratzer.ghosttalk.core.model.ButtonTemplate -> draggedItem.buttonConfig.label
                    is com.andreas_kratzer.ghosttalk.ui.components.SplitWizardButtonDrag -> draggedItem.label
                    else -> ""
                }
                val action = when (draggedItem) {
                    is com.andreas_kratzer.ghosttalk.ui.components.StructureButtonDrag -> draggedItem.action
                    is com.andreas_kratzer.ghosttalk.core.model.ButtonTemplate -> draggedItem.buttonConfig.buttonAction
                    is com.andreas_kratzer.ghosttalk.ui.components.SplitWizardButtonDrag -> draggedItem.action
                    else -> null
                }
                val isDark = androidx.compose.foundation.isSystemInDarkTheme()
                val (bgColor, textColor) = remember(action, isDark) {
                    com.andreas_kratzer.ghosttalk.core.ui.theme.ActionVisualTokens.getColors(action, isDark)
                }
                Card(
                    shape = MaterialTheme.shapes.small,
                    colors = CardDefaults.cardColors(containerColor = bgColor),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    if (isTablet) {
                        if (sidePanelExpanded) {
                            // Left Column: TreeView (~34%)
                            Card(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(300.dp)
                                    .padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        IconButton(onClick = { sidePanelExpanded = false }) {
                                            Icon(
                                                imageVector = GhostTalkIcons.ArrowBack,
                                                contentDescription = stringResource(R.string.side_panel_collapse)
                                            )
                                        }
                                    }
                                    StructureTreeNavigator(
                                        graph = graph,
                                        pages = pages,
                                        pageNames = pageNames,
                                        focusedPageId = focusedPageId,
                                        onFocus = { navigateToPage(it) },
                                        onOrphanClick = { orphanId -> orphanToConnectId = orphanId },
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                    )
                                }
                            }
                        } else {
                            // Collapsed rail: button to re-open the side panel
                            Column(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(start = 8.dp, top = 16.dp)
                            ) {
                                IconButton(onClick = { sidePanelExpanded = true }) {
                                    Icon(
                                        imageVector = GhostTalkIcons.ArrowForward,
                                        contentDescription = stringResource(R.string.side_panel_expand)
                                    )
                                }
                            }
                        }

                        // Split divider
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // Right Column / Main: Focus Canvas
                    StructureFocusCanvas(
                        focusedPageId = focusedPageId,
                        pages = pages,
                        templates = templates,
                        graph = graph,
                        pageNames = pageNames,
                        viewMode = viewMode,
                        proposal = pageSplitProposal,
                        isSplitLoading = isPageSplitLoading,
                        onTriggerSplit = {
                            val accepted = pageSplitViewModel.hasAcceptedPageSplitOptIn
                            if (accepted) {
                                pageSplitViewModel.generatePageSplitProposal(focusedPageId)
                            } else {
                                showOptInDialog.value = true
                            }
                        },
                        onApplySplit = { proposalVal ->
                            pageSplitViewModel.applyPageSplit(focusedPageId, proposalVal)
                            pageSplitViewModel.clearPageSplitProposal()
                        },
                        onDiscardSplit = {
                            pageSplitViewModel.clearPageSplitProposal()
                        },
                        onFocus = { navigateToPage(it) },
                        onEditPageInGrid = onEditPageInGrid,
                        onMoveButton = onMoveButton,
                        onAddConnection = onAddConnection,
                        onRemoveConnection = { pageId, buttonIndex, targetPageName ->
                            pageToRemoveConnectionFromPageId = pageId
                            pageToRemoveConnectionByButtonIndex = buttonIndex
                            pageToRemoveConnectionTargetName = targetPageName
                        },
                        onCreatePage = onCreatePage,
                        onEditButton = { pageId, idx ->
                            if (isMultiSelectMode) {
                                val cur = selection[pageId].orEmpty()
                                val next = if (cur.contains(idx)) cur - idx else cur + idx
                                selection = if (next.isEmpty()) selection - pageId else selection + (pageId to next)
                            } else {
                                editTarget = pageId to idx
                            }
                        },
                        onAddButton = { pageId -> addTargetPageId = pageId },
                        onRegisterSplitWizardDropCallback = { callback -> onSplitWizardDropCallback = callback },
                        isMultiSelectMode = isMultiSelectMode,
                        selection = selection,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )

                    if (isTablet && templatesPanelExpanded) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(300.dp)
                                .padding(end = 16.dp, top = 16.dp, bottom = 16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.template_panel_title),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    IconButton(onClick = { templatesPanelExpanded = false }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Vorlagen schließen"
                                        )
                                    }
                                }
                                ButtonTemplatesPanel(
                                    actions = gridEditorViewModel,
                                    onEditTemplate = { template -> editingTemplate = template },
                                    onTemplateClick = onTemplateClick,
                                    modifier = Modifier.weight(1f).fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }

        // Drawer / BottomSheet for tree view on phones
        if (!isTablet && showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.85f)
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.structure_tree_toggle),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    StructureTreeNavigator(
                        graph = graph,
                        pages = pages,
                        pageNames = pageNames,
                        focusedPageId = focusedPageId,
                        onFocus = {
                            navigateToPage(it)
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                if (!sheetState.isVisible) {
                                    showBottomSheet = false
                                }
                            }
                        },
                        onOrphanClick = { orphanId ->
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                if (!sheetState.isVisible) {
                                    showBottomSheet = false
                                }
                            }
                            orphanToConnectId = orphanId
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    // Confirmation dialogs
    if (pageToRemoveConnectionByButtonIndex != null) {
        AlertDialog(
            onDismissRequest = {
                pageToRemoveConnectionByButtonIndex = null
                pageToRemoveConnectionTargetName = ""
                pageToRemoveConnectionFromPageId = ""
            },
            title = { Text(stringResource(R.string.structure_remove_connection_title)) },
            text = { Text(stringResource(R.string.structure_remove_connection_msg, pageToRemoveConnectionTargetName)) },
            confirmButton = {
                Button(
                    onClick = {
                        val index = pageToRemoveConnectionByButtonIndex!!
                        pageToRemoveConnectionByButtonIndex = null
                        pageToRemoveConnectionTargetName = ""
                        gridEditorViewModel.updateButtonConfig(pageToRemoveConnectionFromPageId, index, null)
                        pageToRemoveConnectionFromPageId = ""
                        showSuccessSnackbarWithUndo(R.string.button_delete_success)
                    }
                ) {
                    Text(stringResource(R.string.structure_action_remove))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pageToRemoveConnectionByButtonIndex = null
                        pageToRemoveConnectionTargetName = ""
                    }
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (orphanToConnectId != null) {
        val orphanName = pageNames[orphanToConnectId] ?: orphanToConnectId!!
        val currentPageName = pageNames[focusedPageId] ?: focusedPageId
        AlertDialog(
            onDismissRequest = { orphanToConnectId = null },
            title = { Text(stringResource(R.string.structure_connect_orphan_title)) },
            text = { Text(stringResource(R.string.structure_connect_orphan_msg, orphanName, currentPageName)) },
            confirmButton = {
                Button(
                    onClick = {
                        val targetId = orphanToConnectId!!
                        orphanToConnectId = null
                        performAddConnection(targetId)
                    }
                ) {
                    Text(stringResource(R.string.structure_action_connect))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val targetId = orphanToConnectId!!
                        orphanToConnectId = null
                        navigateToPage(targetId)
                    }
                ) {
                    Text(stringResource(R.string.structure_view_page))
                }
            }
        )
    }

    if (showOptInDialog.value) {
        PageSplitOptInDialog(
            onConfirmCloud = { rememberDecision ->
                showOptInDialog.value = false
                if (rememberDecision) {
                    pageSplitViewModel.hasAcceptedPageSplitOptIn = true
                }
                pageSplitViewModel.generatePageSplitProposal(focusedPageId)
            },
            onConfirmManual = {
                showOptInDialog.value = false
                val page = pages.find { it.id == focusedPageId }
                if (page != null) {
                    val defaultStartPageId = pageSplitViewModel.defaultStartPageId
                    val labels = page.buttonConfigs
                        .filter { !pageSplitViewModel.shouldFilterButtonFromSplit(it, defaultStartPageId, page.id) }
                        .map { it!!.label }
                    manualPromptText = pageSplitViewModel.generatePageSplitPrompt(labels)
                    showManualPromptDialog.value = true
                }
            },
            onDismiss = { showOptInDialog.value = false }
        )
    }

    if (showManualPromptDialog.value) {
        PageSplitManualPromptDialog(
            promptText = manualPromptText,
            onEvaluateResponse = { response ->
                pageSplitViewModel.parsePageSplitProposal(response)
                showManualPromptDialog.value = false
            },
            onDismiss = { showManualPromptDialog.value = false }
        )
    }

    if (showRenameDialog) {
        GhostTalkDialog(
            title = stringResource(R.string.page_dialog_rename_title),
            onDismiss = { showRenameDialog = false },
            onConfirm = {
                if (tempName.isNotBlank()) {
                    localName = tempName
                    if (focusedPage != null) {
                        gridEditorViewModel.updateGridSettings(
                            itemId = focusedPage.id,
                            update = GridSettingsUpdate(name = tempName)
                        )
                    }
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
                    .testTag("structure_editor_name_field")
            )
        }
    }

    if (showIncomingLinksDialog) {
        val pageName = pageNames[focusedPageId] ?: ""
        IncomingReferencesDialog(
            pageName = pageName,
            usages = incomingUsages,
            onDismiss = { showIncomingLinksDialog = false },
            onNavigateToUsage = { usage ->
                showIncomingLinksDialog = false
                if (usage is com.andreas_kratzer.ghosttalk.core.domain.pages.UsageLocation.PageUsage) {
                    navigateToPage(usage.id)
                } else {
                    Toast.makeText(context, R.string.page_incoming_links_template_toast, Toast.LENGTH_SHORT).show()
                }
            }
        )
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

    if (editTarget != null) {
        val (pageId, index) = editTarget!!
        val page = pages.find { it.id == pageId }
        val buttonConfig = page?.buttonConfigs?.getOrNull(index) ?: ButtonConfig()
        ButtonConfigDialog(
            buttonConfig = buttonConfig,
            pages = pages,
            templates = templates,
            defaultStartPageId = startPageId,
            onDismiss = { editTarget = null },
            onSave = { newConfig ->
                gridEditorViewModel.updateButtonConfig(pageId, index, newConfig)
                editTarget = null
            },
            onTest = { config ->
                gridEditorViewModel.executeButtonAction(config)
            },
            onSuggestLabel = if (gridEditorViewModel.isGeminiEnabled) { config, callback ->
                gridEditorViewModel.suggestButtonLabel(config, callback)
            } else null,
            onDelete = {
                gridEditorViewModel.updateButtonConfig(pageId, index, null)
                editTarget = null
                showSuccessSnackbarWithUndo(R.string.button_delete_success)
            },
            onCreatePage = onCreatePage,
            isTextCached = { gridEditorViewModel.isTextCached(it) },
            onPrefetchText = { text, onComplete -> gridEditorViewModel.prefetchText(text, onComplete) },
            onSaveAsTemplate = { config ->
                showSaveTemplateDialogConfig.value = config
                newTemplateName = config.label
            }
        )
    }

    if (addTargetPageId != null) {
        val pageId = addTargetPageId!!
        val page = pages.find { it.id == pageId }
        ButtonConfigDialog(
            buttonConfig = ButtonConfig(id = java.util.UUID.randomUUID().toString()),
            pages = pages,
            templates = templates,
            defaultStartPageId = startPageId,
            onDismiss = { addTargetPageId = null },
            onSave = { newConfig ->
                val firstFreeIndex = page?.buttonConfigs?.indexOfFirst { it == null || !it.isActive } ?: -1
                val targetIndex = if (firstFreeIndex != -1) firstFreeIndex else page?.buttonConfigs?.size ?: 0
                gridEditorViewModel.insertButtonConfig(pageId, targetIndex, newConfig, false) { success ->
                    if (success) {
                        showSuccessSnackbarWithUndo(R.string.button_add_success)
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = pageViewModel.getApplication<android.app.Application>().getString(R.string.structure_page_full)
                            )
                        }
                    }
                }
                addTargetPageId = null
            },
            onTest = { config ->
                gridEditorViewModel.executeButtonAction(config)
            },
            onSuggestLabel = if (gridEditorViewModel.isGeminiEnabled) { config, callback ->
                gridEditorViewModel.suggestButtonLabel(config, callback)
            } else null,
            onDelete = {
                addTargetPageId = null
            },
            onCreatePage = onCreatePage,
            isTextCached = { gridEditorViewModel.isTextCached(it) },
            onPrefetchText = { text, onComplete -> gridEditorViewModel.prefetchText(text, onComplete) },
            onSaveAsTemplate = { config ->
                showSaveTemplateDialogConfig.value = config
                newTemplateName = config.label
            }
        )
    }

    if (editingTemplate != null) {
        val template = editingTemplate!!
        ButtonConfigDialog(
            buttonConfig = template.buttonConfig,
            pages = pages,
            templates = templates,
            defaultStartPageId = startPageId,
            onDismiss = { editingTemplate = null },
            onSave = { newConfig ->
                gridEditorViewModel.updateButtonTemplate(template.copy(name = newConfig.label, buttonConfig = newConfig))
                editingTemplate = null
            },
            onTest = { config ->
                gridEditorViewModel.executeButtonAction(config)
            },
            onDelete = {
                gridEditorViewModel.deleteButtonTemplate(template)
                editingTemplate = null
            },
            onCreatePage = onCreatePage,
            isTextCached = { gridEditorViewModel.isTextCached(it) },
            onPrefetchText = { text, onComplete -> gridEditorViewModel.prefetchText(text, onComplete) },
            onSaveAsTemplate = {}
        )
    }

    if (showSaveTemplateDialogConfig.value != null) {
        AlertDialog(
            onDismissRequest = { showSaveTemplateDialogConfig.value = null },
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
                        val config = showSaveTemplateDialogConfig.value
                        if (config != null && newTemplateName.isNotBlank()) {
                            gridEditorViewModel.saveButtonAsTemplate(newTemplateName, config)
                            android.widget.Toast.makeText(context, "Vorlage gespeichert", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        showSaveTemplateDialogConfig.value = null
                    }
                ) {
                    Text("Speichern")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveTemplateDialogConfig.value = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    if (!isTablet && showTemplatesBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTemplatesBottomSheet = false },
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            CompositionLocalProvider(LocalDragDropState provides dragDropState) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.85f)
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.template_panel_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        IconButton(onClick = { showTemplatesBottomSheet = false }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Schließen")
                        }
                    }
                    ButtonTemplatesPanel(
                        actions = gridEditorViewModel,
                        onEditTemplate = { template ->
                            editingTemplate = template
                            showTemplatesBottomSheet = false
                        },
                        onTemplateClick = { template ->
                            onTemplateClick(template)
                            showTemplatesBottomSheet = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    if (showBulkDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            title = { Text(stringResource(R.string.bulk_action_delete)) },
            text = { Text(stringResource(R.string.bulk_action_confirm_delete)) },
            confirmButton = {
                Button(
                    onClick = {
                        val listSelection = selection.mapValues { it.value.toList() }
                        gridEditorViewModel.bulkDeleteButtonsBatch(listSelection)
                        showSuccessSnackbarWithUndo(R.string.button_delete_success)
                        clearSelection()
                        showBulkDeleteConfirm = false
                    }
                ) {
                    Text(stringResource(R.string.bulk_action_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBulkDeleteConfirm = false }
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showBulkMoveDialog) {
        SearchablePagePicker(
            title = stringResource(R.string.bulk_action_move),
            subtitle = null,
            excludePageId = "",
            pages = pages,
            onDismissRequest = { showBulkMoveDialog = false },
            onPageSelected = { targetPageId ->
                val listSelection = selection.mapValues { it.value.toList() }
                gridEditorViewModel.bulkMoveButtonsToPageBatch(
                    selection = listSelection,
                    toPageId = targetPageId,
                    forceMove = false
                ) { result ->
                    if (result is com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonToPageUseCase.MoveResult.Success) {
                        showSuccessSnackbarWithUndo(R.string.button_move_success)
                        clearSelection()
                    } else if (result is com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonToPageUseCase.MoveResult.TargetFull) {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = pageViewModel.getApplication<android.app.Application>()
                                    .getString(R.string.structure_target_full)
                            )
                        }
                    }
                }
                showBulkMoveDialog = false
            }
        )
    }

    if (showBulkCopyDialog) {
        SearchablePagePicker(
            title = stringResource(R.string.bulk_action_copy),
            subtitle = null,
            excludePageId = "",
            pages = pages,
            onDismissRequest = { showBulkCopyDialog = false },
            onPageSelected = { targetPageId ->
                val listSelection = selection.mapValues { it.value.toList() }
                gridEditorViewModel.bulkDuplicateButtonsToPageBatch(
                    selection = listSelection,
                    toPageId = targetPageId,
                    forceMove = false
                ) { result ->
                    if (result is com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonToPageUseCase.MoveResult.Success) {
                        showSuccessSnackbarWithUndo(R.string.button_duplicate_success)
                        clearSelection()
                    } else if (result is com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonToPageUseCase.MoveResult.TargetFull) {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = pageViewModel.getApplication<android.app.Application>()
                                    .getString(R.string.structure_target_full)
                            )
                        }
                    }
                }
                showBulkCopyDialog = false
            }
        )
    }
}

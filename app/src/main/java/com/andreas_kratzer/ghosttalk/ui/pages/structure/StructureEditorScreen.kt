package com.andreas_kratzer.ghosttalk.ui.pages.structure

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.domain.pages.BookNavigationGraph
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.GridSettingsUpdate
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.ui.components.EditorTopBar
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import androidx.compose.ui.text.style.TextOverflow
import com.andreas_kratzer.ghosttalk.core.ui.R as CoreR
import com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkDialog
import com.andreas_kratzer.ghosttalk.ui.components.ValidatedTextField
import com.andreas_kratzer.ghosttalk.ui.components.EditablePageTitle
import com.andreas_kratzer.ghosttalk.ui.components.EditorAssistantButton
import com.andreas_kratzer.ghosttalk.ui.pages.GridEditorViewModel
import com.andreas_kratzer.ghosttalk.ui.pages.IncomingReferencesDialog
import com.andreas_kratzer.ghosttalk.ui.pages.PageSplitViewModel
import com.andreas_kratzer.ghosttalk.ui.pages.PageViewModel
import com.andreas_kratzer.ghosttalk.ui.pages.history.EditIcon
import com.andreas_kratzer.ghosttalk.ui.pages.pagesplit.PageSplitManualPromptDialog
import com.andreas_kratzer.ghosttalk.ui.pages.pagesplit.PageSplitOptInDialog
import com.andreas_kratzer.ghosttalk.ui.pages.resolveEditLabel
import kotlinx.coroutines.launch

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
    modeSwitcher: (@Composable () -> Unit)? = null,
    onExitEditor: (() -> Unit)? = null
) {
    val pages by pageViewModel.unfilteredPages.collectAsState()
    val templates by pageViewModel.templates.collectAsState(initial = emptyList())
    val startPageId by pageViewModel.defaultStartPageIdFlow.collectAsState(initial = null)
    val activeBookId by pageViewModel.activeBookId.collectAsState(initial = null)
    val context = LocalContext.current

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

    val snackbarHostState = remember { SnackbarHostState() }

    var showHistoryPanel by remember { mutableStateOf(false) }
    var showIncomingLinksDialog by remember { mutableStateOf(false) }
    var incomingUsages by remember { mutableStateOf<List<com.andreas_kratzer.ghosttalk.core.domain.pages.UsageLocation>>(emptyList()) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    var pageToRemoveConnectionFromPageId by remember { mutableStateOf("") }
    var pageToRemoveConnectionByButtonIndex by remember { mutableStateOf<Int?>(null) }
    var pageToRemoveConnectionTargetName by remember { mutableStateOf("") }
    var orphanToConnectId by remember { mutableStateOf<String?>(null) }

    val showSuccessSnackbarWithUndo = {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val app = pageViewModel.getApplication<android.app.Application>()
            val snackbarResult = snackbarHostState.showSnackbar(
                message = app.getString(R.string.button_move_success),
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
            fromIndex = fromIndex,
            toPageId = targetPageId,
            forceMove = false
        ) { result ->
            when (result) {
                is com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonToPageUseCase.MoveResult.Success -> {
                    showSuccessSnackbarWithUndo()
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
                        showSuccessSnackbarWithUndo()
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                if (isTablet) {
                    // Left Column: TreeView (~34%)
                    Card(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(300.dp)
                            .padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        StructureTreeNavigator(
                            graph = graph,
                            pages = pages,
                            pageNames = pageNames,
                            focusedPageId = focusedPageId,
                            onFocus = { navigateToPage(it) },
                            onOrphanClick = { orphanId -> orphanToConnectId = orphanId },
                            modifier = Modifier.fillMaxSize()
                        )
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
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
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
                        showSuccessSnackbarWithUndo()
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
}

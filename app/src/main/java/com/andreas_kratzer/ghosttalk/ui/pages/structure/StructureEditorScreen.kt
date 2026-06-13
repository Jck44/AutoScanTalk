package com.andreas_kratzer.ghosttalk.ui.pages.structure

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.domain.pages.BookNavigationGraph
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkScaffold
import com.andreas_kratzer.ghosttalk.ui.pages.GridEditorViewModel
import com.andreas_kratzer.ghosttalk.ui.pages.PageViewModel
import com.andreas_kratzer.ghosttalk.ui.pages.PageSplitViewModel
import com.andreas_kratzer.ghosttalk.ui.pages.pagesplit.PageSplitOptInDialog
import com.andreas_kratzer.ghosttalk.ui.pages.pagesplit.PageSplitManualPromptDialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
    onNavigateBack: () -> Unit
) {
    val pages by pageViewModel.unfilteredPages.collectAsState(initial = emptyList())
    val templates by pageViewModel.templates.collectAsState(initial = emptyList())
    val startPageId by pageViewModel.defaultStartPageIdFlow.collectAsState(initial = null)
    val activeBookId by pageViewModel.activeBookId.collectAsState(initial = null)

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
        mutableStateOf("")
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

    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var showBottomSheet by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

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

    val onMoveButton = { fromIndex: Int, targetPageId: String ->
        gridEditorViewModel.moveButtonToPage(
            fromPageId = focusedPageId,
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

    GhostTalkScaffold(
        title = stringResource(R.string.structure_editor_title),
        onNavigateBack = onNavigateBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        actions = {
            if (!isTablet) {
                IconButton(onClick = { showBottomSheet = true }) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = stringResource(R.string.structure_tree_toggle)
                    )
                }
            }
        }
    ) { paddingValues ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
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
                        pageNames = pageNames,
                        focusedPageId = focusedPageId,
                        onFocus = { focusedPageId = it },
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
                onFocus = { focusedPageId = it },
                onEditPageInGrid = onEditPageInGrid,
                onMoveButton = onMoveButton,
                onAddConnection = onAddConnection,
                onRemoveConnection = { buttonIndex, targetPageName ->
                    pageToRemoveConnectionByButtonIndex = buttonIndex
                    pageToRemoveConnectionTargetName = targetPageName
                },
                onCreatePage = onCreatePage,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
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
                        pageNames = pageNames,
                        focusedPageId = focusedPageId,
                        onFocus = {
                            focusedPageId = it
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
            },
            title = { Text(stringResource(R.string.structure_remove_connection_title)) },
            text = { Text(stringResource(R.string.structure_remove_connection_msg, pageToRemoveConnectionTargetName)) },
            confirmButton = {
                Button(
                    onClick = {
                        val index = pageToRemoveConnectionByButtonIndex!!
                        pageToRemoveConnectionByButtonIndex = null
                        pageToRemoveConnectionTargetName = ""
                        gridEditorViewModel.updateButtonConfig(focusedPageId, index, null)
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
                        focusedPageId = targetId
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
}

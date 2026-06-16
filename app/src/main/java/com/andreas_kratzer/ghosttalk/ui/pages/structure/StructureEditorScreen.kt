package com.andreas_kratzer.ghosttalk.ui.pages.structure

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.domain.pages.BookNavigationGraph
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.GridSettingsUpdate
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.ui.components.SplitWizardButtonDrag
import com.andreas_kratzer.ghosttalk.ui.pages.GridEditorViewModel
import com.andreas_kratzer.ghosttalk.ui.pages.PageSplitViewModel
import com.andreas_kratzer.ghosttalk.ui.pages.PageViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PAGE_NAME_UPDATE_DEBOUNCE_MS = 500L

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
    onNavigateBack: () -> Unit,
    onFocusedPageChanged: (String) -> Unit = {},
    viewMode: StructureViewMode = StructureViewMode.CARDS,
    modeSwitcher: (@Composable () -> Unit)? = null,
    onExitEditor: (() -> Unit)? = null
) {
    val pages by pageViewModel.unfilteredPages.collectAsState()
    val templates by pageViewModel.templates.collectAsState(initial = emptyList())
    val startPageId by pageViewModel.defaultStartPageIdFlow.collectAsState(initial = null)
    val activeBookId by pageViewModel.activeBookId.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

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

    val state = rememberStructureEditorState(
        initialFocusedPageId = initialFocusedId
    )

    BackHandler(enabled = state.focusHistory.isNotEmpty()) {
        state.goBackHistory()
    }

    val pageSplitProposal by pageSplitViewModel.pageSplitProposal.collectAsState()

    var hasTriggeredInitialSplit by remember(initialFocusedId) { mutableStateOf(false) }
    LaunchedEffect(initialTriggerSplit, initialFocusedId, pages) {
        if (initialTriggerSplit && !hasTriggeredInitialSplit && initialFocusedId.isNotBlank() && pages.isNotEmpty()) {
            hasTriggeredInitialSplit = true
            val page = pages.find { it.id == initialFocusedId }
            if (page != null) {
                val accepted = pageSplitViewModel.hasAcceptedPageSplitOptIn
                if (accepted) {
                    pageSplitViewModel.generatePageSplitProposal(page.id)
                } else {
                    state.showOptInDialog = true
                }
            }
        }
    }

    LaunchedEffect(initialFocusedId, pages) {
        if (state.focusedPageId.isBlank() || pages.none { it.id == state.focusedPageId }) {
            state.focusedPageId = initialFocusedId
        }
    }

    LaunchedEffect(state.focusedPageId) {
        if (state.focusedPageId.isNotBlank()) {
            onFocusedPageChanged(state.focusedPageId)
        }
    }

    val focusedPage = remember(pages, state.focusedPageId) { pages.find { it.id == state.focusedPageId } }
    var localName by remember(focusedPage?.name) { mutableStateOf(focusedPage?.name ?: "") }

    LaunchedEffect(localName) {
        if (focusedPage != null && localName != focusedPage.name && localName.isNotBlank()) {
            delay(PAGE_NAME_UPDATE_DEBOUNCE_MS)
            gridEditorViewModel.updateGridSettings(
                itemId = focusedPage.id,
                update = GridSettingsUpdate(name = localName)
            )
        }
    }

    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val isTablet = with(density) { windowInfo.containerSize.width.toDp() } >= 600.dp

    val showSuccessSnackbarWithUndo: (Int) -> Unit = { messageResId: Int ->
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
        val page = pages.find { it.id == state.focusedPageId }
        val firstFreeIndex = page?.buttonConfigs?.indexOfFirst { it == null || !it.isActive } ?: -1
        val targetIndex = if (firstFreeIndex != -1) firstFreeIndex else page?.buttonConfigs?.size ?: 0
        val newConfig = template.buttonConfig.copy(
            id = java.util.UUID.randomUUID().toString()
        )
        gridEditorViewModel.insertButtonConfig(state.focusedPageId, targetIndex, newConfig, false) { success ->
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
            state.onSplitWizardDropCallback?.invoke(item, target)
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

    val onAddConnectionBetween = { sourcePageId: String, targetPageId: String ->
        val sourcePage = pages.find { it.id == sourcePageId }
        val targetPageName = pageNames[targetPageId] ?: targetPageId
        if (sourcePage != null) {
            val alreadyConnected = graph.outgoing[sourcePageId].orEmpty().any { it.targetPageId == targetPageId }
            if (alreadyConnected) {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = pageViewModel.getApplication<android.app.Application>().getString(R.string.structure_connection_exists)
                    )
                }
            } else {
                val firstFreeIndex = sourcePage.buttonConfigs.indexOfFirst { it == null || !it.isActive }
                val targetIndex = if (firstFreeIndex != -1) firstFreeIndex else sourcePage.buttonConfigs.size
                val newConfig = ButtonConfig(
                    id = java.util.UUID.randomUUID().toString(),
                    label = targetPageName,
                    spokenText = "Öffne $targetPageName",
                    buttonAction = NavigateToPageButtonAction(pageId = targetPageId),
                    auditoryCue = com.andreas_kratzer.ghosttalk.core.model.AuditoryCue.TextToSpeechCue("Öffne $targetPageName")
                )
                gridEditorViewModel.insertButtonConfig(sourcePageId, targetIndex, newConfig, false) { success ->
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
        onAddConnectionBetween(state.focusedPageId, targetPageId)
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

    val historyState by gridEditorViewModel.historyState.collectAsState()

    Scaffold(
        topBar = {
            StructureEditorTopBar(
                state = state,
                localName = localName,
                isTablet = isTablet,
                canUndo = historyState.canUndo,
                canRedo = historyState.canRedo,
                onNavigateBack = onNavigateBack,
                onExitEditor = onExitEditor,
                modeSwitcher = modeSwitcher,
                onSplitWizardClick = {
                    val accepted = pageSplitViewModel.hasAcceptedPageSplitOptIn
                    if (accepted) {
                        pageSplitViewModel.generatePageSplitProposal(state.focusedPageId)
                    } else {
                        state.showOptInDialog = true
                    }
                },
                onUndoClick = {
                    gridEditorViewModel.undo { message ->
                        scope.launch { snackbarHostState.showSnackbar(message) }
                    }
                },
                onRedoClick = {
                    gridEditorViewModel.redo { message ->
                        scope.launch { snackbarHostState.showSnackbar(message) }
                    }
                },
                onShowIncomingLinks = {
                    scope.launch {
                        state.incomingUsages = pageViewModel.getPageUsages(state.focusedPageId)
                        state.showIncomingLinksDialog = true
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        StructureEditorContent(
            state = state,
            graph = graph,
            pages = pages,
            templates = templates,
            pageNames = pageNames,
            viewMode = viewMode,
            isTablet = isTablet,
            pageSplitProposal = pageSplitProposal,
            onApplySplit = { proposalVal ->
                pageSplitViewModel.applyPageSplit(state.focusedPageId, proposalVal)
                pageSplitViewModel.clearPageSplitProposal()
            },
            onDiscardSplit = {
                pageSplitViewModel.clearPageSplitProposal()
            },
            onAddConnection = onAddConnection,
            onCreatePage = onCreatePage,
            onDrop = onDrop,
            onTemplateClick = onTemplateClick,
            onEditButtonTemplate = { template ->
                state.editingTemplate = template
            },
            onButtonTemplateClick = { template ->
                onTemplateClick(template)
                state.showTemplatesBottomSheet = false
            },
            templatesPanelActions = gridEditorViewModel,
            modifier = Modifier.padding(paddingValues)
        )

        StructureEditorDialogs(
            state = state,
            pages = pages,
            templates = templates,
            startPageId = startPageId,
            pageNames = pageNames,
            historyState = historyState,
            onSetAcceptedPageSplitOptIn = { accepted ->
                pageSplitViewModel.hasAcceptedPageSplitOptIn = accepted
            },
            onGeneratePageSplitProposal = { pageId ->
                pageSplitViewModel.generatePageSplitProposal(pageId)
            },
            onShouldFilterButtonFromSplit = { config, defaultStartId, pageId ->
                pageSplitViewModel.shouldFilterButtonFromSplit(config, defaultStartId, pageId)
            },
            onGeneratePageSplitPrompt = { labels ->
                pageSplitViewModel.generatePageSplitPrompt(labels)
            },
            onParsePageSplitProposal = { response ->
                pageSplitViewModel.parsePageSplitProposal(response)
            },
            onUpdateGridSettings = { itemId, update ->
                gridEditorViewModel.updateGridSettings(itemId, update)
                if (itemId == state.focusedPageId) {
                    localName = update.name ?: localName
                }
            },
            onUpdateButtonConfig = { pageId, index, config ->
                gridEditorViewModel.updateButtonConfig(pageId, index, config)
            },
            onInsertButtonConfig = { pageId, index, config, isUndo, onComplete ->
                gridEditorViewModel.insertButtonConfig(pageId, index, config, isUndo, onComplete)
            },
            onExecuteButtonAction = { config ->
                gridEditorViewModel.executeButtonAction(config)
            },
            onSuggestButtonLabel = if (gridEditorViewModel.isGeminiEnabled) { config, callback ->
                gridEditorViewModel.suggestButtonLabel(config, callback)
            } else null,
            onUpdateButtonTemplate = { template ->
                gridEditorViewModel.updateButtonTemplate(template)
            },
            onDeleteButtonTemplate = { template ->
                gridEditorViewModel.deleteButtonTemplate(template)
            },
            onSaveButtonAsTemplate = { name, config ->
                gridEditorViewModel.saveButtonAsTemplate(name, config)
            },
            onUndoTo = { index ->
                gridEditorViewModel.undoTo(index)
            },
            onAddConnectionBetween = { sourcePageId, targetPageId ->
                onAddConnectionBetween(sourcePageId, targetPageId)
            },
            onBulkDelete = { selectionMap ->
                gridEditorViewModel.bulkDeleteButtonsBatch(selectionMap)
            },
            onBulkMove = { selectionMap, toPageId, forceMove, onComplete ->
                gridEditorViewModel.bulkMoveButtonsToPageBatch(selectionMap, toPageId, forceMove, onComplete)
            },
            onBulkCopy = { selectionMap, toPageId, forceMove, onComplete ->
                gridEditorViewModel.bulkDuplicateButtonsToPageBatch(selectionMap, toPageId, forceMove, onComplete)
            },
            onCreatePage = onCreatePage,
            isTextCached = { text ->
                gridEditorViewModel.isTextCached(text)
            },
            onPrefetchText = { text, onComplete ->
                gridEditorViewModel.prefetchText(text, onComplete)
            },
            showSuccessSnackbarWithUndo = showSuccessSnackbarWithUndo,
            showSnackbar = { resId ->
                val msg = pageViewModel.getApplication<android.app.Application>().getString(resId)
                scope.launch { snackbarHostState.showSnackbar(msg) }
            }
        )
    }
}

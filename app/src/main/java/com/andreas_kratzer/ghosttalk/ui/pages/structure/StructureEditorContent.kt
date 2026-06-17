package com.andreas_kratzer.ghosttalk.ui.pages.structure

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.ai.domain.SplitPageUseCase.PageSplitProposal
import com.andreas_kratzer.ghosttalk.core.domain.pages.BookNavigationGraph
import com.andreas_kratzer.ghosttalk.core.domain.pages.PageSearchResult
import com.andreas_kratzer.ghosttalk.core.domain.pages.SearchPagesUseCase
import com.andreas_kratzer.ghosttalk.core.model.ButtonTemplate
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.ui.components.DragDropContainer
import com.andreas_kratzer.ghosttalk.ui.components.LocalDragDropState
import com.andreas_kratzer.ghosttalk.ui.components.SplitWizardButtonDrag
import com.andreas_kratzer.ghosttalk.ui.components.rememberDragDropState
import com.andreas_kratzer.ghosttalk.ui.templates.ButtonTemplatesPanel
import com.andreas_kratzer.ghosttalk.ui.util.GridEditorActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class, ExperimentalCoroutinesApi::class)
@Composable
fun StructureEditorContent(
    state: StructureEditorState,
    graph: BookNavigationGraph,
    overviewLayout: Map<String, androidx.compose.ui.geometry.Offset>,
    pages: List<Page>,
    templates: List<PageTemplate>,
    pageNames: Map<String, String>,
    viewMode: StructureViewMode,
    isTablet: Boolean,
    pageSplitProposal: PageSplitProposal?,
    onApplySplit: (PageSplitProposal) -> Unit,
    onDiscardSplit: () -> Unit,
    onAddConnection: (String) -> Unit,
    onCreatePage: (String, Int, Int, String?, (String) -> Unit) -> Unit,
    onDrop: (Any, Any) -> Unit,
    onTemplateClick: (ButtonTemplate) -> Unit,
    onEditButtonTemplate: (ButtonTemplate) -> Unit,
    onButtonTemplateClick: (ButtonTemplate) -> Unit,
    templatesPanelActions: GridEditorActions,
    modifier: Modifier = Modifier,
    onNavigateToGraph: (String) -> Unit = {},
    onZoomInto: (String) -> Unit = {}
) {
    val dragDropState = rememberDragDropState()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()

    val searchPagesUseCase = remember { SearchPagesUseCase() }
    val searchResults by produceState(emptyList<PageSearchResult>(), state.searchQuery, pages) {
        snapshotFlow { state.searchQuery }
            .debounce(200)
            .mapLatest { q ->
                if (q.isBlank()) emptyList()
                else withContext(Dispatchers.Default) {
                    searchPagesUseCase.execute(pages, q)
                }
            }
            .collect { value = it }
    }
    val matchingPageIds = remember(searchResults) {
        searchResults.map { it.pageId }.toSet()
    }

    DragDropContainer(
        state = dragDropState,
        onDrop = { item, target -> onDrop(item, target) },
        modifier = modifier.fillMaxSize(),
        floatingPreview = { draggedItem ->
            val label = when (draggedItem) {
                is com.andreas_kratzer.ghosttalk.ui.components.StructureButtonDrag -> draggedItem.label
                is ButtonTemplate -> draggedItem.buttonConfig.label
                is SplitWizardButtonDrag -> draggedItem.label
                else -> ""
            }
            val action = when (draggedItem) {
                is com.andreas_kratzer.ghosttalk.ui.components.StructureButtonDrag -> draggedItem.action
                is ButtonTemplate -> draggedItem.buttonConfig.buttonAction
                is SplitWizardButtonDrag -> draggedItem.action
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
                    if (state.sidePanelExpanded) {
                        // Unified left panel: one place for both Tree and Templates,
                        // switched via a segmented toggle (no second sidebar on the right).
                        com.andreas_kratzer.ghosttalk.core.ui.components.EditorSidePanel(
                            selectedTab = state.sidePanelTab,
                            onSelectTab = { state.sidePanelTab = it },
                            onCollapse = { state.sidePanelExpanded = false },
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
                            treeContent = {
                                StructureTreeNavigator(
                                    state = state,
                                    graph = graph,
                                    pages = pages,
                                    pageNames = pageNames,
                                    focusedPageId = state.focusedPageId,
                                    onFocus = { state.navigateToPage(it) },
                                    searchResults = searchResults,
                                    onOrphanClick = { orphanId -> state.orphanToConnectId = orphanId },
                                    modifier = Modifier.fillMaxSize()
                                )
                            },
                            templatesContent = {
                                ButtonTemplatesPanel(
                                    actions = templatesPanelActions,
                                    onEditTemplate = onEditButtonTemplate,
                                    onTemplateClick = onTemplateClick,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        )
                    } else {
                        // Collapsed rail: button to re-open the side panel
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(start = 8.dp, top = 16.dp)
                        ) {
                            IconButton(onClick = { state.sidePanelExpanded = true }) {
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

                // Right Column / Main: Focus Canvas or Overview Canvas
                if (viewMode == StructureViewMode.OVERVIEW) {
                    StructureOverviewCanvas(
                        focusedPageId = state.focusedPageId,
                        graph = graph,
                        overviewLayout = overviewLayout,
                        pageNames = pageNames,
                        onFocus = { state.navigateToPage(it) },
                        onNavigateToGraph = onNavigateToGraph,
                        matchingPageIds = matchingPageIds,
                        onZoomInto = onZoomInto,
                        selection = state.selection,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                } else {
                    StructureFocusCanvas(
                        focusedPageId = state.focusedPageId,
                        pages = pages,
                        graph = graph,
                        pageNames = pageNames,
                        onFocus = { state.navigateToPage(it) },
                        onAddConnection = onAddConnection,
                        matchingPageIds = matchingPageIds,
                        onRemoveConnection = { pageId, buttonIndex, targetPageName ->
                            state.pageToRemoveConnectionFromPageId = pageId
                            state.pageToRemoveConnectionByButtonIndex = buttonIndex
                            state.pageToRemoveConnectionTargetName = targetPageName
                        },
                        proposal = pageSplitProposal,
                        onApplySplit = onApplySplit,
                        onDiscardSplit = onDiscardSplit,
                        viewMode = viewMode,
                        onEditButton = { pageId, idx ->
                            if (state.isMultiSelectMode) {
                                val cur = state.selection[pageId].orEmpty()
                                val next = if (cur.contains(idx)) cur - idx else cur + idx
                                state.selection = if (next.isEmpty()) state.selection - pageId else state.selection + (pageId to next)
                            } else {
                                state.editTarget = pageId to idx
                            }
                        },
                        onAddButton = { pageId -> state.addTargetPageId = pageId },
                        onRegisterSplitWizardDropCallback = { callback -> state.onSplitWizardDropCallback = callback },
                        isMultiSelectMode = state.isMultiSelectMode,
                        selection = state.selection,
                        templates = templates,
                        onCreatePage = onCreatePage,
                        onZoomInto = onZoomInto,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }

            }
        }

        // Drawer / BottomSheet for tree view on phones
        if (!isTablet && state.showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { state.showBottomSheet = false },
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
                        state = state,
                        graph = graph,
                        pages = pages,
                        pageNames = pageNames,
                        focusedPageId = state.focusedPageId,
                        onFocus = {
                            state.navigateToPage(it)
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                if (!sheetState.isVisible) {
                                    state.showBottomSheet = false
                                }
                            }
                        },
                        searchResults = searchResults,
                        onOrphanClick = { orphanId ->
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                if (!sheetState.isVisible) {
                                    state.showBottomSheet = false
                                }
                            }
                            state.orphanToConnectId = orphanId
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Drawer / BottomSheet for templates on phones
        if (!isTablet && state.showTemplatesBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { state.showTemplatesBottomSheet = false },
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
                            IconButton(onClick = { state.showTemplatesBottomSheet = false }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                            }
                        }
                        ButtonTemplatesPanel(
                            actions = templatesPanelActions,
                            onEditTemplate = onEditButtonTemplate,
                            onTemplateClick = onButtonTemplateClick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

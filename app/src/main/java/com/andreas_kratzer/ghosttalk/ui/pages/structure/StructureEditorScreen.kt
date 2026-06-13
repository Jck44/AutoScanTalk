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
import com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkScaffold
import com.andreas_kratzer.ghosttalk.ui.pages.PageViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StructureEditorScreen(
    pageViewModel: PageViewModel,
    onEditPageInGrid: (pageId: String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val pages by pageViewModel.unfilteredPages.collectAsState(initial = emptyList())
    val startPageId by pageViewModel.defaultStartPageIdFlow.collectAsState(initial = null)

    val graph = remember(pages, startPageId) {
        BookNavigationGraph.from(pages, startPageId)
    }

    val pageNames = remember(pages) {
        pages.associate { it.id to it.name }
    }

    // Determine initial focusedPageId: startPageId, or fallback to the one with the smallest orderIndex
    val initialFocusedId = remember(pages, startPageId) {
        startPageId?.takeIf { id -> pages.any { it.id == id } }
            ?: pages.minByOrNull { it.orderIndex }?.id
            ?: ""
    }

    var focusedPageId by rememberSaveable {
        mutableStateOf("")
    }

    // Adjust focused page if it gets deleted or if graph changes and focusedPageId is empty
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

    GhostTalkScaffold(
        title = stringResource(R.string.structure_editor_title),
        onNavigateBack = onNavigateBack,
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
                graph = graph,
                pageNames = pageNames,
                onFocus = { focusedPageId = it },
                onEditPageInGrid = onEditPageInGrid,
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
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

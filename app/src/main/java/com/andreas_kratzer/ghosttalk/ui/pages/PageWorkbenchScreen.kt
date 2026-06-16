package com.andreas_kratzer.ghosttalk.ui.pages

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.andreas_kratzer.ghosttalk.core.ui.components.EditorMode
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.ui.pages.structure.StructureEditorScreen
import com.andreas_kratzer.ghosttalk.ui.pages.structure.StructureViewMode

/**
 * Unified Workbench Screen hosting both the Grid Page Editor ("Raster")
 * and the Graph/Tree Structure Editor ("Struktur") in a cohesive layout.
 */
@Suppress("AssignedValueIsNeverRead")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageWorkbenchScreen(
    pageId: String,
    initialMode: String = "raster",
    initialButtonId: String? = null,
    initialTriggerSplit: Boolean = false,
    initialOpenAssistant: Boolean = false,
    pageViewModel: PageViewModel,
    gridEditorViewModel: GridEditorViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onExitEditor: () -> Unit
) {
    var mode by rememberSaveable { mutableStateOf(EditorMode.fromRoute(initialMode)) }
    var structureView by rememberSaveable { mutableStateOf(StructureViewMode.CARDS) }
    var focusedPageId by rememberSaveable { mutableStateOf(pageId) }
    var currentButtonId by rememberSaveable { mutableStateOf(initialButtonId) }
    var triggerSplit by rememberSaveable { mutableStateOf(initialTriggerSplit) }
    var assistantPending by rememberSaveable { mutableStateOf(initialOpenAssistant) }

    val modeSwitcher = @Composable {
        SingleChoiceSegmentedButtonRow {
            SegmentedButton(
                selected = mode == EditorMode.RASTER,
                onClick = { mode = EditorMode.RASTER },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                icon = {}
            ) {
                Icon(
                    imageVector = GhostTalkIcons.GridView,
                    contentDescription = "Raster"
                )
            }
            SegmentedButton(
                selected = mode == EditorMode.STRUKTUR && structureView == StructureViewMode.CARDS,
                onClick = {
                    mode = EditorMode.STRUKTUR
                    structureView = StructureViewMode.CARDS
                },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                icon = {}
            ) {
                Icon(
                    imageVector = GhostTalkIcons.Description,
                    contentDescription = "Karten"
                )
            }
            SegmentedButton(
                selected = mode == EditorMode.STRUKTUR && structureView == StructureViewMode.GRAPH,
                onClick = {
                    mode = EditorMode.STRUKTUR
                    structureView = StructureViewMode.GRAPH
                },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                icon = {}
            ) {
                Icon(
                    imageVector = GhostTalkIcons.Sitemap,
                    contentDescription = "Graph"
                )
            }
        }
    }

    if (mode == EditorMode.RASTER) {
        PageEditorScreen(
            pageId = focusedPageId,
            initialButtonId = currentButtonId,
            initialOpenAssistant = assistantPending,
            onAssistantConsumed = { assistantPending = false },
            pageViewModel = pageViewModel,
            gridEditorViewModel = gridEditorViewModel,
            onNavigateBack = onNavigateBack,
            onExitEditor = onExitEditor,
            onEditPage = { targetPageId, btnId ->
                focusedPageId = targetPageId
                currentButtonId = btnId
            },
            onOpenStructureEditor = { targetPageId, isSplit ->
                focusedPageId = targetPageId
                triggerSplit = isSplit
                mode = EditorMode.STRUKTUR
            },
            modeSwitcher = modeSwitcher
        )
    } else {
        StructureEditorScreen(
            pageViewModel = pageViewModel,
            gridEditorViewModel = gridEditorViewModel,
            initialFocusedPageId = focusedPageId,
            initialTriggerSplit = triggerSplit,
            viewMode = structureView,
            onNavigateBack = onNavigateBack,
            modeSwitcher = modeSwitcher,
            onExitEditor = onExitEditor
        )
    }
}

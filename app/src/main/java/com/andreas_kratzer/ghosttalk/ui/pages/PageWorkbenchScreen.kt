package com.andreas_kratzer.ghosttalk.ui.pages

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.andreas_kratzer.ghosttalk.core.ui.components.EditorMode
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.ui.pages.structure.StructureEditorScreen
import com.andreas_kratzer.ghosttalk.ui.pages.structure.StructureViewMode

val SelectionSaver = androidx.compose.runtime.saveable.listSaver<Map<String, Set<Int>>, String>(
    save = { map ->
        map.entries.map { "${it.key}:${it.value.joinToString(",")}" }
    },
    restore = { list ->
        list.associate { entry ->
            val parts = entry.split(":", limit = 2)
            val pageId = parts[0]
            val indices = if (parts.size > 1 && parts[1].isNotEmpty()) {
                parts[1].split(",").map { it.toInt() }.toSet()
            } else {
                emptySet()
            }
            pageId to indices
        }
    }
)

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
    onExitEditor: () -> Unit,
    isEmbedded: Boolean = false
) {
    var mode by rememberSaveable { 
        mutableStateOf(if (initialMode == "global") EditorMode.STRUKTUR else EditorMode.fromRoute(initialMode)) 
    }
    var structureView by rememberSaveable { 
        mutableStateOf(if (initialMode == "global") StructureViewMode.OVERVIEW else StructureViewMode.GRAPH) 
    }
    var focusedPageId by rememberSaveable { mutableStateOf(pageId) }
    var currentButtonId by rememberSaveable { mutableStateOf(initialButtonId) }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isMultiSelectMode by rememberSaveable { mutableStateOf(false) }
    var selection by rememberSaveable(stateSaver = SelectionSaver) { mutableStateOf<Map<String, Set<Int>>>(emptyMap()) }
    var triggerSplit by rememberSaveable { mutableStateOf(initialTriggerSplit) }
    var assistantPending by rememberSaveable { mutableStateOf(initialOpenAssistant) }

    var preferredFocusedStyle by rememberSaveable { mutableStateOf(StructureViewMode.GRAPH) }

    val currentLevel = when {
        mode == EditorMode.RASTER -> ZoomLevel.GRID
        mode == EditorMode.STRUKTUR && structureView == StructureViewMode.OVERVIEW -> ZoomLevel.GLOBAL
        else -> ZoomLevel.FOCUSED
    }

    // The level the editor was opened at. Back from the entry level leaves the editor;
    // back from a level we zoomed *into* climbs one step out instead.
    val entryLevel = rememberSaveable { currentLevel }

    fun setLevel(level: ZoomLevel) {
        when (level) {
            ZoomLevel.GLOBAL -> {
                mode = EditorMode.STRUKTUR
                structureView = StructureViewMode.OVERVIEW
            }
            ZoomLevel.FOCUSED -> {
                mode = EditorMode.STRUKTUR
                structureView = preferredFocusedStyle
            }
            ZoomLevel.GRID -> {
                mode = EditorMode.RASTER
            }
        }
    }

    fun zoomInto(targetPageId: String) {
        focusedPageId = targetPageId
        when (currentLevel) {
            ZoomLevel.GLOBAL -> setLevel(ZoomLevel.FOCUSED)
            ZoomLevel.FOCUSED -> setLevel(ZoomLevel.GRID)
            ZoomLevel.GRID -> {}
        }
    }

    fun zoomOut() {
        when (currentLevel) {
            ZoomLevel.GRID -> setLevel(ZoomLevel.FOCUSED)
            ZoomLevel.FOCUSED -> setLevel(ZoomLevel.GLOBAL)
            ZoomLevel.GLOBAL -> {}
        }
    }

    val modeSwitcher = @Composable {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(
                    selected = currentLevel == ZoomLevel.GLOBAL,
                    onClick = { setLevel(ZoomLevel.GLOBAL) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                    icon = {}
                ) {
                    Icon(
                        imageVector = GhostTalkIcons.Book,
                        contentDescription = "Übersicht"
                    )
                }
                SegmentedButton(
                    selected = currentLevel == ZoomLevel.FOCUSED,
                    onClick = { setLevel(ZoomLevel.FOCUSED) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                    icon = {}
                ) {
                    Icon(
                        imageVector = GhostTalkIcons.Sitemap,
                        contentDescription = "Seite"
                    )
                }
                SegmentedButton(
                    selected = currentLevel == ZoomLevel.GRID,
                    onClick = { setLevel(ZoomLevel.GRID) },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                    icon = {}
                ) {
                    Icon(
                        imageVector = GhostTalkIcons.GridView,
                        contentDescription = "Raster"
                    )
                }
            }
        }
    }

    AnimatedContent(
        targetState = currentLevel,
        transitionSpec = {
            fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
        },
        label = "zoom_level_transition",
        modifier = Modifier.fillMaxSize()
    ) { level ->
        when (level) {
            ZoomLevel.GRID -> {
                PageEditorScreen(
                    pageId = focusedPageId,
                    initialButtonId = currentButtonId,
                    initialOpenAssistant = assistantPending,
                    onAssistantConsumed = { assistantPending = false },
                    pageViewModel = pageViewModel,
                    gridEditorViewModel = gridEditorViewModel,
                    onNavigateBack = {
                        // Entered directly at the grid → leave the editor.
                        // Reached the grid by zooming in → climb back out to the focused view.
                        if (entryLevel == ZoomLevel.GRID) onNavigateBack() else zoomOut()
                    },
                    onExitEditor = onExitEditor,
                    onEditPage = { targetPageId, btnId ->
                        focusedPageId = targetPageId
                        currentButtonId = btnId
                    },
                    onOpenStructureEditor = { targetPageId, isSplit ->
                        focusedPageId = targetPageId
                        triggerSplit = isSplit
                        setLevel(ZoomLevel.FOCUSED)
                    },
                    modeSwitcher = modeSwitcher,
                    isEmbedded = isEmbedded
                )
            }
            ZoomLevel.GLOBAL -> {
                StructureEditorScreen(
                    pageViewModel = pageViewModel,
                    gridEditorViewModel = gridEditorViewModel,
                    initialFocusedPageId = focusedPageId,
                    initialTriggerSplit = triggerSplit,
                    onFocusedPageChanged = {
                        focusedPageId = it
                    },
                    onNavigateToGraph = {
                        zoomInto(it)
                    },
                    onZoomInto = {
                        zoomInto(it)
                    },
                    onZoomOut = {
                        zoomOut()
                    },
                    viewMode = StructureViewMode.OVERVIEW,
                    onNavigateBack = onNavigateBack,
                    modeSwitcher = modeSwitcher,
                    onExitEditor = onExitEditor,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    isMultiSelectMode = isMultiSelectMode,
                    onMultiSelectModeChange = { isMultiSelectMode = it },
                    selection = selection,
                    onSelectionChange = { selection = it },
                    onStyleToggleClick = null,
                    isEmbedded = isEmbedded
                )
            }
            ZoomLevel.FOCUSED -> {
                StructureEditorScreen(
                    pageViewModel = pageViewModel,
                    gridEditorViewModel = gridEditorViewModel,
                    initialFocusedPageId = focusedPageId,
                    initialTriggerSplit = triggerSplit,
                    onFocusedPageChanged = {
                        focusedPageId = it
                    },
                    onNavigateToGraph = {
                        zoomInto(it)
                    },
                    onZoomInto = {
                        zoomInto(it)
                    },
                    onZoomOut = {
                        zoomOut()
                    },
                    viewMode = structureView,
                    onNavigateBack = onNavigateBack,
                    modeSwitcher = modeSwitcher,
                    onExitEditor = onExitEditor,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    isMultiSelectMode = isMultiSelectMode,
                    onMultiSelectModeChange = { isMultiSelectMode = it },
                    selection = selection,
                    onSelectionChange = { selection = it },
                    onStyleToggleClick = {
                        val nextView = if (structureView == StructureViewMode.GRAPH) {
                            StructureViewMode.CARDS
                        } else {
                            StructureViewMode.GRAPH
                        }
                        structureView = nextView
                        preferredFocusedStyle = nextView
                    },
                    isEmbedded = isEmbedded
                )
            }
        }
    }
}

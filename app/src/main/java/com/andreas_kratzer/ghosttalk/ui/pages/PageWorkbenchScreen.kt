package com.andreas_kratzer.ghosttalk.ui.pages

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.andreas_kratzer.ghosttalk.ui.pages.structure.StructureEditorScreen

/**
 * Unified Workbench Screen hosting both the Grid Page Editor ("Raster")
 * and the Graph/Tree Structure Editor ("Struktur") in a cohesive layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageWorkbenchScreen(
    pageId: String,
    initialMode: String = "raster",
    initialButtonId: String? = null,
    initialTriggerSplit: Boolean = false,
    pageViewModel: PageViewModel,
    gridEditorViewModel: GridEditorViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel(),
    onNavigateBack: () -> Unit,
    onExitEditor: () -> Unit
) {
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    var focusedPageId by rememberSaveable { mutableStateOf(pageId) }
    var currentButtonId by rememberSaveable { mutableStateOf(initialButtonId) }
    var triggerSplit by rememberSaveable { mutableStateOf(initialTriggerSplit) }

    val modeSwitcher = @Composable {
        SingleChoiceSegmentedButtonRow {
            SegmentedButton(
                selected = mode == "raster",
                onClick = { mode = "raster" },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) {
                Text("Raster")
            }
            SegmentedButton(
                selected = mode == "struktur",
                onClick = { mode = "struktur" },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) {
                Text("Struktur")
            }
        }
    }

    if (mode == "raster") {
        PageEditorScreen(
            pageId = focusedPageId,
            initialButtonId = currentButtonId,
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
                mode = "struktur"
            },
            modeSwitcher = modeSwitcher
        )
    } else {
        StructureEditorScreen(
            pageViewModel = pageViewModel,
            gridEditorViewModel = gridEditorViewModel,
            initialFocusedPageId = focusedPageId,
            initialTriggerSplit = triggerSplit,
            onEditPageInGrid = { targetPageId ->
                focusedPageId = targetPageId
                currentButtonId = null
                mode = "raster"
            },
            onNavigateBack = onNavigateBack,
            modeSwitcher = modeSwitcher
        )
    }
}

package com.andreas_kratzer.ghosttalk.ui.pages.structure

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.andreas_kratzer.ghosttalk.core.domain.pages.UsageLocation
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ButtonTemplate
import com.andreas_kratzer.ghosttalk.ui.components.SplitWizardButtonDrag

/** Which content the unified left side panel currently shows (tablet). */
enum class SidePanelTab { TREE, TEMPLATES }

@Stable
class StructureEditorState(
    initialFocusedPageId: String,
    initialFocusHistory: List<String> = emptyList(),
    initialSidePanelExpanded: Boolean = true,
    initialSidePanelTab: SidePanelTab = SidePanelTab.TREE,
    initialSearchQuery: String = "",
    initialIsMultiSelectMode: Boolean = false,
    initialSelection: Map<String, Set<Int>> = emptyMap()
) {
    var focusedPageId by mutableStateOf(initialFocusedPageId)
    var focusHistory by mutableStateOf(initialFocusHistory)
    var sidePanelExpanded by mutableStateOf(initialSidePanelExpanded)
    var sidePanelTab by mutableStateOf(initialSidePanelTab)

    var onSearchQueryChange: (String) -> Unit = {}
    var onIsMultiSelectModeChange: (Boolean) -> Unit = {}
    var onSelectionChange: (Map<String, Set<Int>>) -> Unit = {}

    var showOptInDialog by mutableStateOf(false)
    var showManualPromptDialog by mutableStateOf(false)
    var manualPromptText by mutableStateOf("")

    var showBottomSheet by mutableStateOf(false)
    var showTemplatesBottomSheet by mutableStateOf(false)

    var editTarget by mutableStateOf<Pair<String, Int>?>(null)

    private var _searchQuery by mutableStateOf(initialSearchQuery)
    var searchQuery: String
        get() = _searchQuery
        set(value) {
            if (_searchQuery != value) {
                _searchQuery = value
                onSearchQueryChange(value)
            }
        }

    var addTargetPageId by mutableStateOf<String?>(null)
    var editingTemplate by mutableStateOf<ButtonTemplate?>(null)
    var showSaveTemplateDialogConfig by mutableStateOf<ButtonConfig?>(null)
    var newTemplateName by mutableStateOf("")
    var showRenameDialog by mutableStateOf(false)

    var onSplitWizardDropCallback by mutableStateOf<((SplitWizardButtonDrag, Any) -> Unit)?>(null)

    var showHistoryPanel by mutableStateOf(false)
    var showIncomingLinksDialog by mutableStateOf(false)
    var incomingUsages by mutableStateOf<List<UsageLocation>>(emptyList())
    var showOverflowMenu by mutableStateOf(false)

    private var _isMultiSelectMode by mutableStateOf(initialIsMultiSelectMode)
    var isMultiSelectMode: Boolean
        get() = _isMultiSelectMode
        set(value) {
            if (_isMultiSelectMode != value) {
                _isMultiSelectMode = value
                onIsMultiSelectModeChange(value)
            }
        }

    private var _selection by mutableStateOf(initialSelection)
    var selection: Map<String, Set<Int>>
        get() = _selection
        set(value) {
            if (_selection != value) {
                _selection = value
                onSelectionChange(value)
            }
        }
    var showBulkMoveDialog by mutableStateOf(false)
    var showBulkCopyDialog by mutableStateOf(false)
    var showBulkDeleteConfirm by mutableStateOf(false)

    var pageToRemoveConnectionFromPageId by mutableStateOf("")
    var pageToRemoveConnectionByButtonIndex by mutableStateOf<Int?>(null)
    var pageToRemoveConnectionTargetName by mutableStateOf("")
    var orphanToConnectId by mutableStateOf<String?>(null)
    var quickConnectForPageId by mutableStateOf<String?>(null)

    val selectedCount: Int
        get() = selection.values.sumOf { it.size }

    fun navigateToPage(newPageId: String) {
        if (newPageId != focusedPageId && newPageId.isNotBlank()) {
            focusHistory = focusHistory + focusedPageId
            focusedPageId = newPageId
        }
    }

    fun goBackHistory(): Boolean {
        val lastPageId = focusHistory.lastOrNull()
        return if (lastPageId != null) {
            focusedPageId = lastPageId
            focusHistory = focusHistory.dropLast(1)
            true
        } else {
            false
        }
    }

    fun clearSelection() {
        selection = emptyMap()
        isMultiSelectMode = false
    }

    companion object {
        val Saver: Saver<StructureEditorState, *> = listSaver(
            save = { state ->
                listOf(
                    state.focusedPageId,
                    state.focusHistory,
                    state.sidePanelExpanded,
                    state.sidePanelTab.name
                )
            },
            restore = { saved ->
                @Suppress("UNCHECKED_CAST")
                StructureEditorState(
                    initialFocusedPageId = saved[0] as String,
                    initialFocusHistory = saved[1] as List<String>,
                    initialSidePanelExpanded = saved[2] as Boolean,
                    initialSidePanelTab = SidePanelTab.valueOf(saved[3] as String)
                )
            }
        )
    }
}

@Composable
fun rememberStructureEditorState(
    initialFocusedPageId: String,
    initialSidePanelExpanded: Boolean = true,
    initialSidePanelTab: SidePanelTab = SidePanelTab.TREE,
    initialSearchQuery: String = "",
    initialIsMultiSelectMode: Boolean = false,
    initialSelection: Map<String, Set<Int>> = emptyMap(),
    onSearchQueryChange: (String) -> Unit = {},
    onIsMultiSelectModeChange: (Boolean) -> Unit = {},
    onSelectionChange: (Map<String, Set<Int>>) -> Unit = {}
): StructureEditorState {
    val state = rememberSaveable(saver = StructureEditorState.Saver) {
        StructureEditorState(
            initialFocusedPageId = initialFocusedPageId,
            initialSidePanelExpanded = initialSidePanelExpanded,
            initialSidePanelTab = initialSidePanelTab,
            initialSearchQuery = initialSearchQuery,
            initialIsMultiSelectMode = initialIsMultiSelectMode,
            initialSelection = initialSelection
        )
    }
    state.onSearchQueryChange = onSearchQueryChange
    state.onIsMultiSelectModeChange = onIsMultiSelectModeChange
    state.onSelectionChange = onSelectionChange
    return state
}

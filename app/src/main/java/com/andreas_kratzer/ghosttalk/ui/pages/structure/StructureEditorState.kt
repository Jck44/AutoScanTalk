package com.andreas_kratzer.ghosttalk.ui.pages.structure

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.andreas_kratzer.ghosttalk.core.domain.pages.UsageLocation
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ButtonTemplate
import com.andreas_kratzer.ghosttalk.ui.components.SplitWizardButtonDrag

@Stable
class StructureEditorState(
    initialFocusedPageId: String,
    initialFocusHistory: List<String> = emptyList(),
    initialSidePanelExpanded: Boolean = true,
    initialTemplatesPanelExpanded: Boolean = false
) {
    var focusedPageId by mutableStateOf(initialFocusedPageId)
    var focusHistory by mutableStateOf(initialFocusHistory)
    var sidePanelExpanded by mutableStateOf(initialSidePanelExpanded)
    var templatesPanelExpanded by mutableStateOf(initialTemplatesPanelExpanded)

    var showOptInDialog by mutableStateOf(false)
    var showManualPromptDialog by mutableStateOf(false)
    var manualPromptText by mutableStateOf("")

    var showBottomSheet by mutableStateOf(false)
    var showTemplatesBottomSheet by mutableStateOf(false)

    var editTarget by mutableStateOf<Pair<String, Int>?>(null)
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

    var isMultiSelectMode by mutableStateOf(false)
    var selection by mutableStateOf<Map<String, Set<Int>>>(emptyMap())
    var showBulkMoveDialog by mutableStateOf(false)
    var showBulkCopyDialog by mutableStateOf(false)
    var showBulkDeleteConfirm by mutableStateOf(false)

    var pageToRemoveConnectionFromPageId by mutableStateOf("")
    var pageToRemoveConnectionByButtonIndex by mutableStateOf<Int?>(null)
    var pageToRemoveConnectionTargetName by mutableStateOf("")
    var orphanToConnectId by mutableStateOf<String?>(null)

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
                    state.templatesPanelExpanded
                )
            },
            restore = { saved ->
                @Suppress("UNCHECKED_CAST")
                StructureEditorState(
                    initialFocusedPageId = saved[0] as String,
                    initialFocusHistory = saved[1] as List<String>,
                    initialSidePanelExpanded = saved[2] as Boolean,
                    initialTemplatesPanelExpanded = saved[3] as Boolean
                )
            }
        )
    }
}

@Composable
fun rememberStructureEditorState(
    initialFocusedPageId: String,
    initialSidePanelExpanded: Boolean = true,
    initialTemplatesPanelExpanded: Boolean = false
): StructureEditorState {
    return rememberSaveable(saver = StructureEditorState.Saver) {
        StructureEditorState(
            initialFocusedPageId = initialFocusedPageId,
            initialSidePanelExpanded = initialSidePanelExpanded,
            initialTemplatesPanelExpanded = initialTemplatesPanelExpanded
        )
    }
}

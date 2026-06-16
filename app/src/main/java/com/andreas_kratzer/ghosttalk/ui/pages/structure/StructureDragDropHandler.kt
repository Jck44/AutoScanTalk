package com.andreas_kratzer.ghosttalk.ui.pages.structure

import com.andreas_kratzer.ghosttalk.core.model.ButtonTemplate
import com.andreas_kratzer.ghosttalk.ui.components.StructureButtonDrag
import com.andreas_kratzer.ghosttalk.ui.components.StructureDeleteTarget
import com.andreas_kratzer.ghosttalk.ui.components.StructureNodeTarget
import com.andreas_kratzer.ghosttalk.ui.components.StructureSlotTarget

object StructureDragDropHandler {
    fun handleDrop(
        draggedItem: Any,
        target: Any,
        onMoveButton: (fromPageId: String, fromIndex: Int, targetPageId: String) -> Unit,
        onMoveButtonToSlot: (fromPageId: String, fromIndex: Int, targetPageId: String, targetIndex: Int) -> Unit,
        onDeleteButton: (pageId: String, index: Int) -> Unit,
        onInsertTemplate: (pageId: String, index: Int, template: ButtonTemplate) -> Unit,
        showSnackbar: (String) -> Unit
    ) {
        when (draggedItem) {
            is StructureButtonDrag -> {
                when (target) {
                    is StructureNodeTarget -> {
                        if (draggedItem.pageId != target.pageId) {
                            onMoveButton(draggedItem.pageId, draggedItem.index, target.pageId)
                        }
                    }
                    is StructureSlotTarget -> {
                        onMoveButtonToSlot(draggedItem.pageId, draggedItem.index, target.pageId, target.index)
                    }
                    is StructureDeleteTarget -> {
                        onDeleteButton(draggedItem.pageId, draggedItem.index)
                    }
                }
            }
            is ButtonTemplate -> {
                when (target) {
                    is StructureNodeTarget -> {
                        onInsertTemplate(target.pageId, -1, draggedItem)
                    }
                    is StructureSlotTarget -> {
                        onInsertTemplate(target.pageId, target.index, draggedItem)
                    }
                }
            }
        }
    }
}

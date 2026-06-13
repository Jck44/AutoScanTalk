package com.andreas_kratzer.ghosttalk.ui.components

import com.andreas_kratzer.ghosttalk.core.model.ActionCategoryRegistry
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ButtonTemplate
import com.andreas_kratzer.ghosttalk.ui.util.GridEditorActions

object GridDragDropHandler {
    fun handleDrop(
        draggedItem: Any,
        target: Any,
        itemId: String,
        actions: GridEditorActions,
        showUndoSnackbar: (String) -> Unit,
        showSnackbar: (String) -> Unit,
        onSaveAsTemplate: (ButtonConfig) -> Unit
    ) {
        when (draggedItem) {
            is ButtonTemplate -> {
                when (target) {
                    is GridCellTarget -> {
                        val config = draggedItem.buttonConfig.copy(
                            id = java.util.UUID.randomUUID().toString()
                        )
                        actions.insertButtonConfig(itemId, target.index, config, false) { success ->
                            if (success) {
                                showUndoSnackbar("Vorlage platziert")
                            } else {
                                showSnackbar("Zielseite ist voll")
                            }
                        }
                    }
                    is InsertTarget -> {
                        val config = draggedItem.buttonConfig.copy(
                            id = java.util.UUID.randomUUID().toString()
                        )
                        actions.insertButtonConfig(itemId, target.index, config, true) { success ->
                            if (success) {
                                showUndoSnackbar("Vorlage eingefügt")
                            } else {
                                showSnackbar("Zielseite ist voll")
                            }
                        }
                    }
                    is TemplateDropTarget -> {
                        val allTemplates = actions.buttonTemplates.value
                        val fromItem = draggedItem
                        val toItem = target.template
                        if (fromItem.id != toItem.id) {
                            val fromCategory = ActionCategoryRegistry.getGroupForAction(fromItem.buttonConfig.buttonAction)
                            val toCategory = ActionCategoryRegistry.getGroupForAction(toItem.buttonConfig.buttonAction)
                            
                            if (fromCategory == toCategory) {
                                val categoryTemplates = allTemplates
                                    .filter { ActionCategoryRegistry.getGroupForAction(it.buttonConfig.buttonAction) == fromCategory }
                                    .sortedBy { it.orderIndex }
                                    .toMutableList()
                                    
                                val fromIdxInCat = categoryTemplates.indexOfFirst { it.id == fromItem.id }
                                val toIdxInCat = categoryTemplates.indexOfFirst { it.id == toItem.id }
                                
                                if (fromIdxInCat != -1 && toIdxInCat != -1) {
                                    categoryTemplates.removeAt(fromIdxInCat)
                                    categoryTemplates.add(toIdxInCat, fromItem)
                                    
                                    val grouped = allTemplates.groupBy {
                                        ActionCategoryRegistry.getGroupForAction(it.buttonConfig.buttonAction)
                                    }
                                    
                                    val newGlobalList = mutableListOf<ButtonTemplate>()
                                    ActionCategoryRegistry.ALL_GROUPS.forEach { cat ->
                                        val itemsInCat = if (cat == fromCategory) {
                                            categoryTemplates
                                        } else {
                                            grouped[cat]?.sortedBy { it.orderIndex } ?: emptyList()
                                        }
                                        newGlobalList.addAll(itemsInCat)
                                    }
                                    
                                    actions.updateButtonTemplatesOrder(newGlobalList)
                                }
                            }
                        }
                    }
                    is CategoryHeaderDropTarget -> {
                        val allTemplates = actions.buttonTemplates.value
                        val fromItem = draggedItem
                        val fromCategory = ActionCategoryRegistry.getGroupForAction(fromItem.buttonConfig.buttonAction)
                        val toCategory = target.groupName
                        
                        if (fromCategory == toCategory) {
                            val categoryTemplates = allTemplates
                                .filter { ActionCategoryRegistry.getGroupForAction(it.buttonConfig.buttonAction) == fromCategory }
                                .sortedBy { it.orderIndex }
                                .toMutableList()
                                
                            val fromIdxInCat = categoryTemplates.indexOfFirst { it.id == fromItem.id }
                            if (fromIdxInCat != -1) {
                                categoryTemplates.removeAt(fromIdxInCat)
                                categoryTemplates.add(0, fromItem)
                                
                                val grouped = allTemplates.groupBy {
                                    ActionCategoryRegistry.getGroupForAction(it.buttonConfig.buttonAction)
                                }
                                
                                val newGlobalList = mutableListOf<ButtonTemplate>()
                                ActionCategoryRegistry.ALL_GROUPS.forEach { cat ->
                                    val itemsInCat = if (cat == fromCategory) {
                                        categoryTemplates
                                    } else {
                                        grouped[cat]?.sortedBy { it.orderIndex } ?: emptyList()
                                    }
                                    newGlobalList.addAll(itemsInCat)
                                }
                                
                                actions.updateButtonTemplatesOrder(newGlobalList)
                            }
                        }
                    }
                }
            }
            is DraggedGridCell -> {
                if (target is DeleteTarget) {
                    actions.updateButtonConfig(itemId, draggedItem.index, null)
                    showUndoSnackbar("Button gelöscht")
                } else if (target is GridCellTarget) {
                    if (draggedItem.index != target.index) {
                        actions.moveButton(itemId, draggedItem.index, target.index)
                        showUndoSnackbar("Button verschoben")
                    }
                } else if (target is InsertTarget) {
                    actions.moveButtonWithInsert(itemId, draggedItem.index, target.index)
                    showUndoSnackbar("Button verschoben")
                } else if (target is TemplatesPanelTarget || target is TemplateDropTarget || target is CategoryHeaderDropTarget) {
                    onSaveAsTemplate(draggedItem.config)
                }
            }
        }
    }
}

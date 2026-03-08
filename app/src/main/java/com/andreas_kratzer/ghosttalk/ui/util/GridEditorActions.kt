package com.andreas_kratzer.ghosttalk.ui.util

import com.andreas_kratzer.ghosttalk.model.ButtonConfig

interface GridEditorActions {
    fun updateGridSettings(
        itemId: String,
        newName: String,
        newScanPattern: String?,
        newRowNames: List<String>,
        newRows: Int? = null,
        newColumns: Int? = null
    )
    fun updateButtonConfig(itemId: String, index: Int, newConfig: ButtonConfig?)
    fun updateRowName(itemId: String, rowIndex: Int, newName: String)
    fun moveRow(itemId: String, fromRow: Int, toRow: Int)
    fun moveButton(itemId: String, fromIndex: Int, toIndex: Int)
    
    // For ButtonConfigDialog
    fun createNewPage(name: String, rows: Int, columns: Int, bookId: String, templateId: String? = null, onCreated: (String) -> Unit)
    fun executeButtonAction(config: ButtonConfig)
}

package com.andreas_kratzer.ghosttalk.ui.util

import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import kotlinx.coroutines.flow.StateFlow

import com.andreas_kratzer.ghosttalk.core.model.GridSettingsUpdate

interface GridEditorActions {
    fun updateGridSettings(
        itemId: String,
        update: GridSettingsUpdate
    )
    fun updateButtonConfig(itemId: String, index: Int, newConfig: ButtonConfig?)
    fun updateRowName(itemId: String, rowIndex: Int, newName: String)
    fun moveRow(itemId: String, fromRow: Int, toRow: Int)
    fun moveButton(itemId: String, fromIndex: Int, toIndex: Int)
    
    // For ButtonConfigDialog
    val isExecuting: StateFlow<Boolean>
    fun createNewPage(name: String, rows: Int, columns: Int, bookId: String, templateId: String? = null, onCreated: (String) -> Unit)
    fun executeButtonAction(config: ButtonConfig)
    fun moveButtonToPage(
        fromPageId: String,
        fromIndex: Int,
        toPageId: String,
        forceMove: Boolean = false,
        onResult: (com.andreas_kratzer.ghosttalk.domain.pages.MoveButtonToPageUseCase.MoveResult) -> Unit
    )
    fun duplicateButtonToPage(
        fromPageId: String,
        fromIndex: Int,
        toPageId: String,
        forceMove: Boolean = false,
        onResult: (com.andreas_kratzer.ghosttalk.domain.pages.MoveButtonToPageUseCase.MoveResult) -> Unit
    )
    val availableGeminiTools: List<com.andreas_kratzer.ghosttalk.core.ai.domain.AiTool>
    
    fun isTextCached(text: String): Boolean = false
    fun prefetchText(text: String, onComplete: () -> Unit = {}) {}
    fun speakTtsPreview(text: String, onDone: () -> Unit = {}) {}
    fun stopTtsPreview() {}
    fun isTtsElevenLabs(): Boolean = false
}

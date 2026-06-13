package com.andreas_kratzer.ghosttalk.ui.pages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andreas_kratzer.ghosttalk.core.actions.ActionExecutor
import com.andreas_kratzer.ghosttalk.core.ai.domain.GeminiUseCase
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ButtonTemplate
import com.andreas_kratzer.ghosttalk.core.model.GridSettingsUpdate
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.ButtonTemplateDelegate
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.PageManagementDelegate
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.SuggestionsDelegate
import com.andreas_kratzer.ghosttalk.ui.pages.delegates.TtsPreviewDelegate
import com.andreas_kratzer.ghosttalk.ui.util.GridEditorActions

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class GridEditorViewModel @Inject constructor(
    private val pageManagementDelegate: PageManagementDelegate,
    private val buttonTemplateDelegate: ButtonTemplateDelegate,
    private val suggestionsDelegate: SuggestionsDelegate,
    private val ttsPreviewDelegate: TtsPreviewDelegate,
    private val actionExecutor: ActionExecutor,
    override val settingsRepository: SettingsRepository,
    private val geminiUseCase: GeminiUseCase,
    private val analyticsDelegate: com.andreas_kratzer.ghosttalk.ui.pages.delegates.AnalyticsDelegate,
    private val pageResolutionDelegate: com.andreas_kratzer.ghosttalk.ui.pages.delegates.PageResolutionDelegate
) : ViewModel(), GridEditorActions {

    override val isAnalyticsOverlayEnabled: StateFlow<Boolean>
        get() = analyticsDelegate.isAnalyticsOverlayEnabled

    override val pageMetrics: StateFlow<Map<String, com.andreas_kratzer.ghosttalk.core.model.ButtonEffortMetrics>>
        get() = analyticsDelegate.pageMetrics

    override val isEditPreviewActive: StateFlow<Boolean>
        get() = pageResolutionDelegate.isEditPreviewActive

    private val _resolvedPage = kotlinx.coroutines.flow.MutableStateFlow<com.andreas_kratzer.ghosttalk.core.model.Page?>(null)
    override val resolvedPage: StateFlow<com.andreas_kratzer.ghosttalk.core.model.Page?> = _resolvedPage

    fun setResolvedPage(page: com.andreas_kratzer.ghosttalk.core.model.Page?) {
        _resolvedPage.value = page
    }

    init {
        buttonTemplateDelegate.init(viewModelScope)
    }

    override val buttonTemplates: StateFlow<List<ButtonTemplate>>
        get() = buttonTemplateDelegate.buttonTemplates

    override fun saveButtonAsTemplate(name: String, config: ButtonConfig) {
        buttonTemplateDelegate.saveButtonAsTemplate(name, config)
    }

    override fun deleteButtonTemplate(template: ButtonTemplate) {
        buttonTemplateDelegate.deleteButtonTemplate(template)
    }

    override fun updateButtonTemplate(template: ButtonTemplate) {
        buttonTemplateDelegate.updateButtonTemplate(template)
    }

    override fun updateButtonTemplatesOrder(templates: List<ButtonTemplate>) {
        buttonTemplateDelegate.updateButtonTemplatesOrder(templates)
    }

    override fun updateButtonConfig(itemId: String, index: Int, newConfig: ButtonConfig?) {
        pageManagementDelegate.updateButtonConfig(itemId, index, newConfig)
    }

    override fun insertButtonConfig(itemId: String, index: Int, newConfig: ButtonConfig, forceShift: Boolean, onResult: (Boolean) -> Unit) {
        pageManagementDelegate.insertButtonConfig(itemId, index, newConfig, forceShift, onResult)
    }

    override fun moveButtonWithInsert(itemId: String, fromIndex: Int, toIndex: Int) {
        pageManagementDelegate.moveButtonWithInsert(itemId, fromIndex, toIndex)
    }

    override fun undo(onSuccess: (String) -> Unit) {
        pageManagementDelegate.undo(onSuccess)
    }

    override val canUndo: StateFlow<Boolean> = pageManagementDelegate.canUndo

    override fun updateGridSettings(
        itemId: String,
        update: GridSettingsUpdate
    ) {
        pageManagementDelegate.updatePageSettings(itemId, update)
    }

    override val isExecuting: StateFlow<Boolean> = actionExecutor.isExecuting

    override fun executeButtonAction(config: ButtonConfig) {
        actionExecutor.executeButtonAction(config)
    }

    override fun isTextCached(text: String): Boolean {
        return ttsPreviewDelegate.isTextCached(text)
    }

    override fun prefetchText(text: String, onComplete: () -> Unit) {
        ttsPreviewDelegate.prefetchText(text, onComplete)
    }

    override fun isTtsElevenLabs(): Boolean {
        return ttsPreviewDelegate.isTtsElevenLabs()
    }

    override fun createNewPage(
        name: String,
        rows: Int,
        columns: Int,
        bookId: String,
        templateId: String?,
        onCreated: (String) -> Unit
    ) {
        pageManagementDelegate.createNewPage(name, rows, columns, bookId, templateId, onCreated)
    }

    override fun updateRowName(itemId: String, rowIndex: Int, newName: String) {
        pageManagementDelegate.updateRowName(itemId, rowIndex, newName)
    }

    override val isGeminiEnabled: Boolean
        get() = settingsRepository.isGeminiEnabled

    override fun suggestButtonLabel(config: ButtonConfig, onResult: (String) -> Unit) {
        suggestionsDelegate.suggestButtonLabel(config, onResult)
    }

    override fun suggestRowName(itemId: String, rowIndex: Int, onResult: (String) -> Unit) {
        suggestionsDelegate.suggestRowName(itemId, rowIndex, onResult)
    }

    override fun moveRow(itemId: String, fromRow: Int, toRow: Int) {
        pageManagementDelegate.moveRow(itemId, fromRow, toRow)
    }

    override fun moveButton(itemId: String, fromIndex: Int, toIndex: Int) {
        pageManagementDelegate.moveButton(itemId, fromIndex, toIndex)
    }

    override fun moveButtonToPage(
        fromPageId: String,
        fromIndex: Int,
        toPageId: String,
        forceMove: Boolean,
        onResult: (com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonToPageUseCase.MoveResult) -> Unit
    ) {
        pageManagementDelegate.moveButtonToPage(fromPageId, fromIndex, toPageId, forceMove, onResult)
    }

    override fun duplicateButtonToPage(
        fromPageId: String,
        fromIndex: Int,
        toPageId: String,
        forceMove: Boolean,
        onResult: (com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonToPageUseCase.MoveResult) -> Unit
    ) {
        pageManagementDelegate.duplicateButtonToPage(fromPageId, fromIndex, toPageId, forceMove, onResult)
    }

    override val availableGeminiTools = geminiUseCase.getAvailableTools()

    override fun speakTtsPreview(text: String, onDone: () -> Unit) {
        ttsPreviewDelegate.speakTtsPreview(text, onDone)
    }

    override fun stopTtsPreview() {
        ttsPreviewDelegate.stopTtsPreview()
    }
}


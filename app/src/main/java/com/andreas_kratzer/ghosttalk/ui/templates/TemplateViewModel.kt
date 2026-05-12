package com.andreas_kratzer.ghosttalk.ui.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.TemplateRepository
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.GridSettingsUpdate
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import com.andreas_kratzer.ghosttalk.core.model.SortOrder
import com.andreas_kratzer.ghosttalk.domain.templates.CreateTemplateUseCase
import com.andreas_kratzer.ghosttalk.domain.templates.DeleteTemplateUseCase
import com.andreas_kratzer.ghosttalk.domain.templates.GetTemplateUsagesUseCase
import com.andreas_kratzer.ghosttalk.domain.templates.UpdateButtonConfigInTemplateUseCase
import com.andreas_kratzer.ghosttalk.ui.util.filterAndSort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TemplateViewModel @Inject constructor(
    private val templateRepository: TemplateRepository,
    internal val pageRepository: com.andreas_kratzer.ghosttalk.core.data.PageRepository,
    val settingsRepository: SettingsRepository,
    private val createTemplateUseCase: CreateTemplateUseCase,
    private val deleteTemplateUseCase: DeleteTemplateUseCase,
    private val updateButtonConfigInTemplateUseCase: UpdateButtonConfigInTemplateUseCase,
    private val getTemplateUsagesUseCase: GetTemplateUsagesUseCase,
    private val geminiUseCase: com.andreas_kratzer.ghosttalk.core.ai.domain.GeminiUseCase
) : ViewModel(), com.andreas_kratzer.ghosttalk.ui.util.GridEditorActions {

    override val availableGeminiTools = geminiUseCase.getAvailableTools()


    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    val templates: StateFlow<List<PageTemplate>> = combine(
        templateRepository.getAllTemplates(),
        settingsRepository.templateSortOrderFlow,
        _searchQuery,
        pageRepository.getUsedTemplateIdsFlow()
    ) { templates: List<PageTemplate>, sortOrderStr: String, query: String, activeIds: Set<String> ->
        val sortOrder = try { SortOrder.valueOf(sortOrderStr) } catch (_: Exception) { SortOrder.MANUAL }
        templates.filterAndSort(query, sortOrder, activeIds)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    init {
        // Ensure default templates exist when VM starts
        viewModelScope.launch {
            templateRepository.ensureBuiltInTemplates()
        }
    }

    fun createTemplate(name: String, rows: Int, columns: Int, initialConfigs: List<ButtonConfig?>? = null) {
        viewModelScope.launch {
            createTemplateUseCase.execute(name, rows, columns, initialConfigs)
        }
    }

    fun updateButtonConfig(template: PageTemplate, index: Int, config: ButtonConfig?) {
        viewModelScope.launch {
            updateButtonConfigInTemplateUseCase.execute(template, index, config)
        }
    }

    fun updateTemplate(template: PageTemplate) {
        viewModelScope.launch {
            templateRepository.insert(template)
        }
    }

    override fun updateGridSettings(
        itemId: String,
        update: GridSettingsUpdate
    ) {
        val current = templates.value.find { it.id == itemId } ?: return
        updateTemplate(current.copy(
            name = update.name ?: current.name,
            scanPattern = update.scanPattern?.value ?: current.scanPattern,
            rowNames = update.rowNames ?: current.rowNames,
            rows = update.rows ?: current.rows,
            columns = update.columns ?: current.columns
        ))
    }

    override fun updateButtonConfig(itemId: String, index: Int, newConfig: ButtonConfig?) {
        val current = templates.value.find { it.id == itemId } ?: return
        updateButtonConfig(current, index, newConfig)
    }

    override fun updateRowName(itemId: String, rowIndex: Int, newName: String) {
        val current = templates.value.find { it.id == itemId } ?: return
        val updatedNames = current.rowNames.toMutableList()
        while (updatedNames.size <= rowIndex) updatedNames.add("Row ${updatedNames.size + 1}")
        updatedNames[rowIndex] = newName
        updateTemplate(current.copy(rowNames = updatedNames))
    }

    override fun moveRow(itemId: String, fromRow: Int, toRow: Int) {
        val current = templates.value.find { it.id == itemId } ?: return
        if (fromRow == toRow) return
        
        val maxCols = com.andreas_kratzer.ghosttalk.ui.util.GridUtils.MAX_GRID_SIZE
        val newButtonConfigs = current.buttonConfigs.toMutableList()
        
        // Ensure 49 slots
        while (newButtonConfigs.size < com.andreas_kratzer.ghosttalk.ui.util.GridUtils.TOTAL_SLOTS) {
            newButtonConfigs.add(null)
        }

        val fromStart = fromRow * maxCols
        val rowToMove = newButtonConfigs.subList(fromStart, fromStart + maxCols).toList()
        repeat(maxCols) { newButtonConfigs.removeAt(fromStart) }
        
        val toStart = toRow * maxCols
        newButtonConfigs.addAll(toStart, rowToMove)
        
        val newRowNames = current.rowNames.toMutableList()
        if (newRowNames.isNotEmpty()) {
            val name = if (fromRow < newRowNames.size) newRowNames.removeAt(fromRow) else "Zeile ${fromRow + 1}"
            if (toRow <= newRowNames.size) newRowNames.add(toRow, name) else newRowNames.add(name)
        }
        
        updateTemplate(current.copy(buttonConfigs = newButtonConfigs, rowNames = newRowNames))
    }

    override fun moveButton(itemId: String, fromIndex: Int, toIndex: Int) {
        val current = templates.value.find { it.id == itemId } ?: return
        if (fromIndex == toIndex) return
        
        val newButtonConfigs = current.buttonConfigs.toMutableList()
        // Ensure 49 slots
        while (newButtonConfigs.size < com.andreas_kratzer.ghosttalk.ui.util.GridUtils.TOTAL_SLOTS) {
            newButtonConfigs.add(null)
        }
        
        if (fromIndex !in newButtonConfigs.indices || toIndex !in newButtonConfigs.indices) return
        
        // Spatial Swap
        val fromConfig = newButtonConfigs[fromIndex]
        val toConfig = newButtonConfigs[toIndex]
        newButtonConfigs[toIndex] = fromConfig
        newButtonConfigs[fromIndex] = toConfig
        
        updateTemplate(current.copy(buttonConfigs = newButtonConfigs))
    }

    override fun moveButtonToPage(
        fromPageId: String,
        fromIndex: Int,
        toPageId: String,
        forceMove: Boolean,
        onResult: (com.andreas_kratzer.ghosttalk.domain.pages.MoveButtonToPageUseCase.MoveResult) -> Unit
    ) {
        // Not implemented for templates
    }

    override fun duplicateButtonToPage(
        fromPageId: String,
        fromIndex: Int,
        toPageId: String,
        forceMove: Boolean,
        onResult: (com.andreas_kratzer.ghosttalk.domain.pages.MoveButtonToPageUseCase.MoveResult) -> Unit
    ) {
        // Not implemented for templates
    }

    override fun createNewPage(
        name: String,
        rows: Int,
        columns: Int,
        bookId: String,
        templateId: String?,
        onCreated: (String) -> Unit
    ) {
        // Not directly supported by TemplateViewModel, but interface requires it.
        // In the UI, PageViewModel is passed to handle this.
    }

    override val isExecuting: StateFlow<Boolean> = MutableStateFlow(false)

    override fun executeButtonAction(config: ButtonConfig) {
        // Not directly supported by TemplateViewModel
    }

    fun deleteTemplate(template: PageTemplate, clearUsages: Boolean = false) {
        viewModelScope.launch {
            deleteTemplateUseCase.execute(template, clearUsages)
        }
    }

    suspend fun getTemplateUsages(templateId: String) = getTemplateUsagesUseCase.execute(templateId)

    fun duplicateTemplate(templateId: String, suffix: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val newId = templateRepository.duplicateTemplate(templateId, suffix)
            onResult(newId)
        }
    }

}

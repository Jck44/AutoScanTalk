package com.andreas_kratzer.ghosttalk.ui.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.TemplateRepository
import com.andreas_kratzer.ghosttalk.core.domain.templates.CreateTemplateUseCase
import com.andreas_kratzer.ghosttalk.core.domain.templates.DeleteTemplateUseCase
import com.andreas_kratzer.ghosttalk.core.domain.templates.GetTemplateUsagesUseCase
import com.andreas_kratzer.ghosttalk.core.domain.templates.UpdateButtonConfigInTemplateUseCase
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.GridSettingsUpdate
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import com.andreas_kratzer.ghosttalk.core.model.SortOrder
import com.andreas_kratzer.ghosttalk.core.util.filterAndSort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Suppress("UNUSED_PARAMETER")
@HiltViewModel
class TemplateViewModel @Inject constructor(
    private val templateRepository: TemplateRepository,
    internal val pageRepository: com.andreas_kratzer.ghosttalk.core.data.PageRepository,
    override val settingsRepository: SettingsRepository,
    private val createTemplateUseCase: CreateTemplateUseCase,
    private val deleteTemplateUseCase: DeleteTemplateUseCase,
    private val updateButtonConfigInTemplateUseCase: UpdateButtonConfigInTemplateUseCase,
    private val getTemplateUsagesUseCase: GetTemplateUsagesUseCase,
    private val geminiUseCase: com.andreas_kratzer.ghosttalk.core.ai.domain.GeminiUseCase
) : ViewModel(), com.andreas_kratzer.ghosttalk.ui.util.GridEditorActions {

    override val availableGeminiTools = geminiUseCase.getAvailableTools()

    private val undoStack = mutableListOf<PageTemplate>()
    private val _historyState = MutableStateFlow(com.andreas_kratzer.ghosttalk.ui.pages.history.HistoryState())
    override val historyState: StateFlow<com.andreas_kratzer.ghosttalk.ui.pages.history.HistoryState> = _historyState.asStateFlow()

    private fun updateHistoryState() {
        _historyState.value = com.andreas_kratzer.ghosttalk.ui.pages.history.HistoryState(
            canUndo = undoStack.isNotEmpty(),
            canRedo = false,
            entries = emptyList()
        )
    }

    private fun saveUndoState(templateId: String) {
        templates.value.find { it.id == templateId }?.let { current ->
            if (undoStack.size >= 10) {
                undoStack.removeAt(0)
            }
            undoStack.add(current.copy(buttonConfigs = current.buttonConfigs.toList()))
            updateHistoryState()
        }
    }

    override fun undo(onSuccess: (String) -> Unit) {
        if (undoStack.isNotEmpty()) {
            val previousState = undoStack.removeLast()
            updateHistoryState()
            updateTemplate(previousState)
            onSuccess("Aktion rückgängig gemacht")
        }
    }

    override fun redo(onSuccess: (String) -> Unit) {}
    override fun undoTo(index: Int) {}

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
        saveUndoState(itemId)
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
        saveUndoState(itemId)
        updateButtonConfig(current, index, newConfig)
    }

    override fun bulkDeleteButtons(itemId: String, indices: List<Int>) {
        val current = templates.value.find { it.id == itemId } ?: return
        saveUndoState(itemId)
        
        val newConfigs = current.buttonConfigs.toMutableList()
        indices.forEach { idx ->
            if (idx in newConfigs.indices) {
                newConfigs[idx] = null
            }
        }
        
        updateTemplate(current.copy(buttonConfigs = newConfigs.toList()))
    }

    override fun insertButtonConfig(itemId: String, index: Int, newConfig: ButtonConfig, forceShift: Boolean, onResult: (Boolean) -> Unit) {
        val current = templates.value.find { it.id == itemId } ?: return
        val newButtonConfigs = current.buttonConfigs.toMutableList()
        
        // Ensure 49 slots
        while (newButtonConfigs.size < com.andreas_kratzer.ghosttalk.core.util.GridUtils.TOTAL_SLOTS) {
            newButtonConfigs.add(null)
        }
        
        if (index in newButtonConfigs.indices) {
            // Check if the entire 49 slots are completely full
            if ((newButtonConfigs[index] != null || forceShift) && newButtonConfigs.none { it == null }) {
                onResult(false)
                return
            }
            
            saveUndoState(itemId)
            if (newButtonConfigs[index] == null && !forceShift) {
                // Target is empty, just replace
                newButtonConfigs[index] = newConfig
            } else {
                // Target is not empty or we force shift, shift items down following the visible layout flow
                val visibleIndices = mutableListOf<Int>()
                for (r in 0 until current.rows) {
                    for (c in 0 until current.columns) {
                        visibleIndices.add(r * com.andreas_kratzer.ghosttalk.core.util.GridUtils.MAX_GRID_SIZE + c)
                    }
                }
                
                val dropVisiblePos = visibleIndices.indexOf(index)
                if (dropVisiblePos != -1) {
                    val lastVisibleGlobal = visibleIndices.last()
                    val lastItem = newButtonConfigs[lastVisibleGlobal]
                    
                    // Shift visible items down by 1
                    for (i in visibleIndices.size - 1 downTo dropVisiblePos + 1) {
                        val currentGlobal = visibleIndices[i]
                        val prevGlobal = visibleIndices[i - 1]
                        newButtonConfigs[currentGlobal] = newButtonConfigs[prevGlobal]
                    }
                    
                    // Rescue the last item by placing it in the first available invisible slot
                    if (lastItem != null) {
                        for (i in 0 until com.andreas_kratzer.ghosttalk.core.util.GridUtils.TOTAL_SLOTS) {
                            if (i !in visibleIndices && newButtonConfigs[i] == null) {
                                newButtonConfigs[i] = lastItem
                                break
                            }
                        }
                    }
                }
                
                // Insert new config
                newButtonConfigs[index] = newConfig
            }
            
            updateTemplate(current.copy(buttonConfigs = newButtonConfigs))
            onResult(true)
        }
    }

    override val isGeminiEnabled: Boolean
        get() = settingsRepository.isGeminiEnabled

    override fun suggestButtonLabel(config: ButtonConfig, onResult: (String) -> Unit) {
        if (!settingsRepository.isGeminiEnabled) {
            onResult("")
            return
        }
        viewModelScope.launch {
            try {
                val prompt = com.andreas_kratzer.ghosttalk.ui.util.generateSuggestButtonLabelPrompt(config) { pageId ->
                    pageRepository.getPageById(pageId)?.name
                }
                val response = geminiUseCase.generateResponse(prompt)
                val cleaned = response.trim().removeSurrounding("\"").removeSurrounding("'").trim()
                onResult(cleaned)
            } catch (e: Exception) {
                onResult("")
            }
        }
    }

    override fun updateRowName(itemId: String, rowIndex: Int, newName: String) {
        val current = templates.value.find { it.id == itemId } ?: return
        saveUndoState(itemId)
        val updatedNames = current.rowNames.toMutableList()
        while (updatedNames.size <= rowIndex) updatedNames.add("Row ${updatedNames.size + 1}")
        updatedNames[rowIndex] = newName
        updateTemplate(current.copy(rowNames = updatedNames))
    }

    override fun suggestRowName(itemId: String, rowIndex: Int, onResult: (String) -> Unit) {
        val template = templates.value.find { it.id == itemId }
        if (template == null) {
            onResult("")
            return
        }

        if (!settingsRepository.isGeminiEnabled) {
            onResult("")
            return
        }

        val columns = template.columns
        val labels = (0 until columns).mapNotNull { c ->
            val globalIndex = rowIndex * com.andreas_kratzer.ghosttalk.core.util.GridUtils.MAX_GRID_SIZE + c
            val config = template.buttonConfigs.getOrNull(globalIndex)
            if (config != null && config.isActive && config.label.isNotBlank()) {
                config.label
            } else null
        }

        if (labels.isEmpty()) {
            onResult("")
            return
        }

        viewModelScope.launch {
            try {
                val prompt = "Analysiere diese Liste von Begriffen, die sich in einer Zeile auf einer Kommunikations-Tafel für Unterstützte Kommunikation befinden: ${labels.joinToString(", ")}. Schlage eine kurze, prägnante Bezeichnung (maximal 2 Wörter, z. B. \"Schnelle Worte\" oder \"Smart Home\") vor, die als Name für diese Zeile dienen kann. Antworte NUR mit dieser Bezeichnung, ohne Satzzeichen, Anführungszeichen oder zusätzliche Erklärungen."
                val response = geminiUseCase.generateResponse(prompt)
                val cleaned = response.trim().removeSurrounding("\"").removeSurrounding("'").trim()
                onResult(cleaned)
            } catch (e: Exception) {
                onResult("")
            }
        }
    }

    override fun moveRow(itemId: String, fromRow: Int, toRow: Int) {
        val current = templates.value.find { it.id == itemId } ?: return
        if (fromRow == toRow) return
        
        saveUndoState(itemId)
        val maxCols = com.andreas_kratzer.ghosttalk.core.util.GridUtils.MAX_GRID_SIZE
        val newButtonConfigs = current.buttonConfigs.toMutableList()
        
        // Ensure 49 slots
        while (newButtonConfigs.size < com.andreas_kratzer.ghosttalk.core.util.GridUtils.TOTAL_SLOTS) {
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
        
        saveUndoState(itemId)
        val newButtonConfigs = current.buttonConfigs.toMutableList()
        // Ensure 49 slots
        while (newButtonConfigs.size < com.andreas_kratzer.ghosttalk.core.util.GridUtils.TOTAL_SLOTS) {
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

    override fun moveButtonWithInsert(itemId: String, fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex || fromIndex == toIndex - 1) return
        val current = templates.value.find { it.id == itemId } ?: return
        val newButtonConfigs = current.buttonConfigs.toMutableList()
        
        while (newButtonConfigs.size < com.andreas_kratzer.ghosttalk.core.util.GridUtils.TOTAL_SLOTS) {
            newButtonConfigs.add(null)
        }
        
        val visibleIndices = mutableListOf<Int>()
        for (r in 0 until current.rows) {
            for (c in 0 until current.columns) {
                visibleIndices.add(r * com.andreas_kratzer.ghosttalk.core.util.GridUtils.MAX_GRID_SIZE + c)
            }
        }
        
        if (fromIndex in visibleIndices && toIndex <= visibleIndices.size) {
            val movedItem = newButtonConfigs[fromIndex] ?: return
            saveUndoState(itemId)
            newButtonConfigs[fromIndex] = null
            
            if (fromIndex < toIndex) {
                for (i in fromIndex until toIndex - 1) {
                    if (i < visibleIndices.size - 1) {
                        val currentGlobal = visibleIndices[i]
                        val nextGlobal = visibleIndices[i + 1]
                        newButtonConfigs[currentGlobal] = newButtonConfigs[nextGlobal]
                    }
                }
                val targetGlobal = visibleIndices[toIndex - 1]
                newButtonConfigs[targetGlobal] = movedItem
            } else {
                for (i in fromIndex downTo toIndex + 1) {
                    val currentGlobal = visibleIndices[i]
                    val prevGlobal = visibleIndices[i - 1]
                    newButtonConfigs[currentGlobal] = newButtonConfigs[prevGlobal]
                }
                val targetGlobal = visibleIndices[toIndex]
                newButtonConfigs[targetGlobal] = movedItem
            }
            
            updateTemplate(current.copy(buttonConfigs = newButtonConfigs))
        }
    }

    override fun moveButtonToPage(
        fromPageId: String,
        fromIndices: List<Int>,
        toPageId: String,
        forceMove: Boolean,
        onResult: (com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonToPageUseCase.MoveResult) -> Unit
    ) {
        // Not implemented for templates
    }

    override fun duplicateButtonToPage(
        fromPageId: String,
        fromIndices: List<Int>,
        toPageId: String,
        forceMove: Boolean,
        onResult: (com.andreas_kratzer.ghosttalk.core.domain.pages.MoveButtonToPageUseCase.MoveResult) -> Unit
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

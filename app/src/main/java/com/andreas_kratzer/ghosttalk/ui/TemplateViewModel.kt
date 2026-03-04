package com.andreas_kratzer.ghosttalk.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.data.TemplateRepository
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.PageTemplate
import com.andreas_kratzer.ghosttalk.model.SortOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TemplateViewModel @Inject constructor(
    private val templateRepository: TemplateRepository,
    val settingsRepository: SettingsRepository
) : ViewModel() {

    val experimentalManualSorting: StateFlow<Boolean> = settingsRepository.experimentalManualSortingFlow

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    val templates: StateFlow<List<PageTemplate>> = combine(
        templateRepository.getAllTemplates(),
        settingsRepository.templateSortOrderFlow,
        _searchQuery
    ) { templates, sortOrderStr, query ->
        val sortOrder = try { SortOrder.valueOf(sortOrderStr) } catch (_: Exception) { SortOrder.MANUAL }
        val filtered = if (query.isBlank()) {
            templates
        } else {
            templates.filter { it.name.contains(query, ignoreCase = true) }
        }
        when (sortOrder) {
            SortOrder.MANUAL -> filtered.sortedBy { it.orderIndex }
            SortOrder.NEWEST -> filtered.sortedByDescending { it.createdAt }
            SortOrder.OLDEST -> filtered.sortedBy { it.createdAt }
            SortOrder.A_Z -> filtered.sortedBy { it.name.lowercase() }
            SortOrder.Z_A -> filtered.sortedByDescending { it.name.lowercase() }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
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
            val totalSlots = rows * columns
            val buttonConfigs = initialConfigs?.toMutableList() ?: MutableList<ButtonConfig?>(totalSlots) { null }
            
            // Pad or truncate to match grid size exactly
            while (buttonConfigs.size < totalSlots) {
                buttonConfigs.add(null)
            }
            if (buttonConfigs.size > totalSlots) {
                buttonConfigs.subList(totalSlots, buttonConfigs.size).clear()
            }
            
            val maxOrderIndex = templates.value.maxOfOrNull { it.orderIndex } ?: -1
            
            val newTemplate = PageTemplate(
                id = UUID.randomUUID().toString(),
                name = name,
                rows = rows,
                columns = columns,
                buttonConfigs = buttonConfigs,
                isBuiltIn = false,
                orderIndex = maxOrderIndex + 1,
                createdAt = System.currentTimeMillis()
            )
            templateRepository.insert(newTemplate)
        }
    }

    fun updateButtonConfig(templateId: String, index: Int, config: ButtonConfig?) {
        val currentTemplate = templates.value.find { it.id == templateId } ?: return
        
        val newConfigs = currentTemplate.buttonConfigs.toMutableList()
        if (index in newConfigs.indices) {
            newConfigs[index] = config
            updateTemplate(currentTemplate.copy(buttonConfigs = newConfigs))
        }
    }

    fun updateTemplate(template: PageTemplate) {
        viewModelScope.launch {
            templateRepository.insert(template)
        }
    }

    fun deleteTemplate(template: PageTemplate) {
        viewModelScope.launch {
            templateRepository.delete(template)
        }
    }

    fun reorderTemplates(fromIndex: Int, toIndex: Int) {
        val currentList = templates.value.toMutableList()
        if (fromIndex !in currentList.indices || toIndex !in currentList.indices) return
        
        val item = currentList.removeAt(fromIndex)
        currentList.add(toIndex, item)
        
        viewModelScope.launch {
            currentList.forEachIndexed { index, template ->
                if (template.orderIndex != index) {
                    templateRepository.insert(template.copy(orderIndex = index))
                }
            }
            settingsRepository.templateSortOrder = SortOrder.MANUAL.name
        }
    }
}

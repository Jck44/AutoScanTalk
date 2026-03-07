package com.andreas_kratzer.ghosttalk.ui.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.data.TemplateRepository
import com.andreas_kratzer.ghosttalk.domain.templates.CreateTemplateUseCase
import com.andreas_kratzer.ghosttalk.domain.templates.DeleteTemplateUseCase
import com.andreas_kratzer.ghosttalk.domain.templates.GetTemplateUsagesUseCase
import com.andreas_kratzer.ghosttalk.domain.templates.ReorderTemplatesUseCase
import com.andreas_kratzer.ghosttalk.domain.templates.UpdateButtonConfigInTemplateUseCase
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.PageTemplate
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
    val settingsRepository: SettingsRepository,
    private val createTemplateUseCase: CreateTemplateUseCase,
    private val deleteTemplateUseCase: DeleteTemplateUseCase,
    private val reorderTemplatesUseCase: ReorderTemplatesUseCase,
    private val updateButtonConfigInTemplateUseCase: UpdateButtonConfigInTemplateUseCase,
    private val getTemplateUsagesUseCase: GetTemplateUsagesUseCase
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
        val sortOrder = try { com.andreas_kratzer.ghosttalk.model.SortOrder.valueOf(sortOrderStr) } catch (_: Exception) { com.andreas_kratzer.ghosttalk.model.SortOrder.MANUAL }
        templates.filterAndSort(query, sortOrder)
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

    fun deleteTemplate(template: PageTemplate, clearUsages: Boolean = false) {
        viewModelScope.launch {
            deleteTemplateUseCase.execute(template, clearUsages)
        }
    }

    suspend fun getTemplateUsages(templateId: String) = getTemplateUsagesUseCase.execute(templateId)

    fun reorderTemplates(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            reorderTemplatesUseCase.execute(templates.value, fromIndex, toIndex)
        }
    }
}

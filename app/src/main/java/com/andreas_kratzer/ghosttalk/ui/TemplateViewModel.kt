package com.andreas_kratzer.ghosttalk.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andreas_kratzer.ghosttalk.data.TemplateRepository
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.PageTemplate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TemplateViewModel @Inject constructor(
    private val templateRepository: TemplateRepository
) : ViewModel() {

    val templates: StateFlow<List<PageTemplate>> = templateRepository.getAllTemplates()
        .stateIn(
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
            
            val newTemplate = PageTemplate(
                id = UUID.randomUUID().toString(),
                name = name,
                rows = rows,
                columns = columns,
                buttonConfigs = buttonConfigs,
                isBuiltIn = false
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
}

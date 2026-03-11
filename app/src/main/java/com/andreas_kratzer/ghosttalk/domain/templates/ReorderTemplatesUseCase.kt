package com.andreas_kratzer.ghosttalk.domain.templates

import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.data.TemplateRepository
import com.andreas_kratzer.ghosttalk.model.PageTemplate
import com.andreas_kratzer.ghosttalk.model.SortOrder
import javax.inject.Inject

class ReorderTemplatesUseCase @Inject constructor(
    private val templateRepository: TemplateRepository,
    private val settingsRepository: SettingsRepository
) {
    suspend fun execute(templates: List<PageTemplate>, fromIndex: Int, toIndex: Int) {
        val currentList = templates.toMutableList()
        if (fromIndex !in currentList.indices || toIndex !in currentList.indices) return
        
        val item = currentList.removeAt(fromIndex)
        currentList.add(toIndex, item)
        
        currentList.forEachIndexed { index, template ->
            if (template.orderIndex != index) {
                templateRepository.insert(template.copy(orderIndex = index))
            }
        }
        settingsRepository.templateSortOrder = SortOrder.MANUAL.name
    }
}
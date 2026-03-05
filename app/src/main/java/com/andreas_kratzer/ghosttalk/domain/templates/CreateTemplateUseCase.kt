package com.andreas_kratzer.ghosttalk.domain.templates

import com.andreas_kratzer.ghosttalk.data.TemplateRepository
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.PageTemplate
import com.andreas_kratzer.ghosttalk.ui.util.GridUtils
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject

class CreateTemplateUseCase @Inject constructor(
    private val templateRepository: TemplateRepository
) {
    suspend fun execute(name: String, rows: Int, columns: Int, initialConfigs: List<ButtonConfig?>?) {
        val buttonConfigs = GridUtils.adjustButtonConfigs(initialConfigs ?: emptyList(), rows, columns)
        val maxOrderIndex = templateRepository.getAllTemplates().first().maxOfOrNull { it.orderIndex } ?: -1

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
package com.andreas_kratzer.ghosttalk.domain.templates

import com.andreas_kratzer.ghosttalk.data.TemplateRepository
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.PageTemplate
import javax.inject.Inject

class UpdateButtonConfigInTemplateUseCase @Inject constructor(
    private val templateRepository: TemplateRepository
) {
    suspend fun execute(template: PageTemplate, index: Int, config: ButtonConfig?) {
        val newConfigs = template.buttonConfigs.toMutableList()
        if (index in newConfigs.indices) {
            newConfigs[index] = config
            templateRepository.insert(template.copy(buttonConfigs = newConfigs))
        }
    }
}
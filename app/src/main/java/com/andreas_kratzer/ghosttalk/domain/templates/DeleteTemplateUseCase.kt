package com.andreas_kratzer.ghosttalk.domain.templates

import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.data.TemplateRepository
import javax.inject.Inject

class DeleteTemplateUseCase @Inject constructor(
    private val templateRepository: TemplateRepository,
    private val pageRepository: PageRepository
) {
    suspend fun execute(template: PageTemplate, clearUsages: Boolean = false) {
        if (clearUsages) {
            val pages = pageRepository.getAllPages().filter { it.templateId == template.id }
            pages.forEach { page ->
                pageRepository.updatePageSettingsOnly(page.copy(templateId = null))
            }
        }
        templateRepository.delete(template)
    }
}
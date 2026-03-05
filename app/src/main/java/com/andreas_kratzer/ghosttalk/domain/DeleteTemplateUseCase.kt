package com.andreas_kratzer.ghosttalk.domain

import com.andreas_kratzer.ghosttalk.data.TemplateRepository
import com.andreas_kratzer.ghosttalk.model.PageTemplate
import javax.inject.Inject

class DeleteTemplateUseCase @Inject constructor(
    private val templateRepository: TemplateRepository
) {
    suspend fun execute(template: PageTemplate) {
        templateRepository.delete(template)
    }
}
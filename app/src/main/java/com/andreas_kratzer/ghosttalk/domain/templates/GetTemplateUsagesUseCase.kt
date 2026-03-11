package com.andreas_kratzer.ghosttalk.domain.templates

import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.domain.pages.UsageLocation
import javax.inject.Inject

class GetTemplateUsagesUseCase @Inject constructor(
    private val pageRepository: PageRepository
) {
    suspend fun execute(templateId: String): List<UsageLocation> {
        val allPages = pageRepository.getAllPages()
        return allPages.filter { it.templateId == templateId }
            .map { UsageLocation.PageUsage(it.id, it.name) }
    }
}

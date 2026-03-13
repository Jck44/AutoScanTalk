package com.andreas_kratzer.ghosttalk.core.data

import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import kotlinx.coroutines.flow.Flow

interface TemplateRepository {
    fun getAllTemplates(): Flow<List<PageTemplate>>
    suspend fun getById(id: String): PageTemplate?
    suspend fun insert(template: PageTemplate)
    suspend fun delete(template: PageTemplate)
    suspend fun ensureBuiltInTemplates()
    suspend fun duplicateTemplate(templateId: String, duplicateSuffix: String): String?
}

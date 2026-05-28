package com.andreas_kratzer.ghosttalk.core.data

import com.andreas_kratzer.ghosttalk.core.model.ButtonTemplate
import kotlinx.coroutines.flow.Flow

interface ButtonTemplateRepository {
    fun getTemplates(): Flow<List<ButtonTemplate>>
    suspend fun saveTemplate(template: ButtonTemplate)
    suspend fun deleteTemplate(template: ButtonTemplate)
    suspend fun updateTemplateOrder(templates: List<ButtonTemplate>)
    suspend fun ensureBuiltInTemplates()
}

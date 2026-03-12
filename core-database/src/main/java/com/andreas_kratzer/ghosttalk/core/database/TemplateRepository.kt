package com.andreas_kratzer.ghosttalk.core.database

import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.FrequentActionButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.PageTemplate
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.core.settings.DatabaseSettings
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

class TemplateRepository @Inject constructor(
    private val templateDao: TemplateDao,
    private val settingsRepository: DatabaseSettings
) {

    fun getAllTemplates(): Flow<List<PageTemplate>> {
        return templateDao.getAllTemplatesFlow()
    }

    suspend fun getById(id: String): PageTemplate? {
        return templateDao.getTemplateById(id)
    }

    suspend fun insert(template: PageTemplate) {
        templateDao.insertTemplate(template)
    }

    suspend fun delete(template: PageTemplate) {
        if (template.isBuiltIn) return
        templateDao.deleteTemplate(template)
    }

    /**
     * Initializes the built-in templates if they don't exist yet.
     */
    suspend fun ensureBuiltInTemplates() {
        if (settingsRepository.initialTemplatesCreated) return

        // Built-in 1: "Häufigste Aktionen" (4x4)
        val frequentId = "builtin_frequent"
        if (templateDao.getTemplateById(frequentId) == null) {
            val frequentButtons = (1..16).map { rank ->
                ButtonConfig(
                    id = UUID.randomUUID().toString(),
                    label = "Aktion ${rank}",
                    buttonAction = FrequentActionButtonAction(rank),
                    auditoryCue = null
                )
            }
            templateDao.insertTemplate(
                PageTemplate(
                    id = frequentId,
                    name = "Vorlage: Häufigste Aktionen",
                    rows = 4,
                    columns = 4,
                    buttonConfigs = frequentButtons,
                    isBuiltIn = true
                )
            )
        }

        // Built-in 2: "Ja / Nein" (4x4)
        val yesNoId = "builtin_yesno"
        if (templateDao.getTemplateById(yesNoId) == null) {
            val yesNoButtons = MutableList<ButtonConfig?>(16) { null }
            yesNoButtons[0] = ButtonConfig(
                id = UUID.randomUUID().toString(),
                label = "Ja",
                buttonAction = SpeakTextButtonAction(),
                auditoryCue = null
            )
            yesNoButtons[1] = ButtonConfig(
                id = UUID.randomUUID().toString(),
                label = "Nein",
                buttonAction = SpeakTextButtonAction(),
                auditoryCue = null
            )
            yesNoButtons[15] = ButtonConfig(
                id = UUID.randomUUID().toString(),
                label = "Zurück zum Start",
                // This ID will be replaced by CreatePageUseCase dynamically
                buttonAction = NavigateToPageButtonAction(""),
                auditoryCue = null
            )

            templateDao.insertTemplate(
                PageTemplate(
                    id = yesNoId,
                    name = "Vorlage: Ja / Nein",
                    rows = 4,
                    columns = 4,
                    buttonConfigs = yesNoButtons,
                    isBuiltIn = true
                )
            )
        }
        
        settingsRepository.initialTemplatesCreated = true
    }

    suspend fun duplicateTemplate(templateId: String, duplicateSuffix: String): String? {
        val original = getById(templateId) ?: return null
        val newId = UUID.randomUUID().toString()
        
        val newButtonConfigs = original.buttonConfigs.map { config ->
            config?.copy(id = UUID.randomUUID().toString())
        }
        
        val newTemplate = original.copy(
            id = newId,
            name = "${original.name}${duplicateSuffix}",
            buttonConfigs = newButtonConfigs,
            isBuiltIn = false,
            createdAt = System.currentTimeMillis()
        )
        
        insert(newTemplate)
        return newId
    }
}

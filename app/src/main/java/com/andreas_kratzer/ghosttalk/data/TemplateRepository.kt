package com.andreas_kratzer.ghosttalk.data

import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.FrequentActionButtonAction
import com.andreas_kratzer.ghosttalk.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.model.PageTemplate
import com.andreas_kratzer.ghosttalk.model.SpeakTextButtonAction
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

class TemplateRepository @Inject constructor(
    private val templateDao: TemplateDao,
    private val settingsRepository: SettingsRepository
) {

    fun getAllTemplatesFlow(): Flow<List<PageTemplate>> {
        return templateDao.getAllTemplatesFlow()
    }

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
}

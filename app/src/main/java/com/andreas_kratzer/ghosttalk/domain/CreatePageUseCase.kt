package com.andreas_kratzer.ghosttalk.domain

import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.data.TemplateRepository
import com.andreas_kratzer.ghosttalk.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.model.Page
import java.util.UUID

/**
 * Use case for creating a new page with an optional template or a default home button.
 */
class CreatePageUseCase @javax.inject.Inject constructor(
    private val pageRepository: PageRepository,
    private val settingsRepository: SettingsRepository,
    private val templateRepository: TemplateRepository
) {
    suspend fun execute(
        name: String, 
        rows: Int, 
        columns: Int, 
        bookId: String, 
        currentPages: List<Page>,
        templateId: String? = null
    ): String {
        val newPageId = UUID.randomUUID().toString()
        val homePageId = settingsRepository.defaultStartPageId ?: currentPages.firstOrNull()?.id

        var finalRows = rows
        var finalColumns = columns
        var buttonConfigs: MutableList<ButtonConfig?>

        val template = templateId?.let { templateRepository.getById(it) }

        if (template != null) {
            finalRows = template.rows
            finalColumns = template.columns
            
            // Validate Max Grid Size 6x6
            if (finalRows * finalColumns > 36 || finalRows > 6 || finalColumns > 6) {
                throw IllegalArgumentException("Maximale Grid-Größe ist 6x6")
            }

            buttonConfigs = template.buttonConfigs.map { config ->
                if (config?.buttonAction is NavigateToPageButtonAction) {
                    val action = config.buttonAction as NavigateToPageButtonAction
                    if (action.pageId.isEmpty() && homePageId != null) {
                        // Dynamically fill the "Zurück zum Start" pageId
                        config.copy(buttonAction = action.copy(pageId = homePageId))
                    } else config
                } else config
            }.toMutableList()
            
        } else {
            // Validate Max Grid Size 6x6
            if (finalRows * finalColumns > 36 || finalRows > 6 || finalColumns > 6) {
                throw IllegalArgumentException("Maximale Grid-Größe ist 6x6")
            }

            val totalSlots = finalRows * finalColumns
            buttonConfigs = MutableList(totalSlots) { null }

            if (totalSlots > 0 && homePageId != null) {
                buttonConfigs[totalSlots - 1] = ButtonConfig(
                    id = UUID.randomUUID().toString(),
                    label = "zurück zum Start",
                    spokenText = "Zurück zur Startseite",
                    buttonAction = NavigateToPageButtonAction(
                        pageId = homePageId
                    ),
                    auditoryCue = AuditoryCue.TextToSpeechCue("Zurück zur Startseite")
                )
            }
        }

        val newPage = Page(
            id = newPageId,
            bookId = bookId,
            name = name,
            rows = finalRows,
            columns = finalColumns,
            buttonConfigs = buttonConfigs
        )
        
        pageRepository.insertPage(newPage)
        return newPageId
    }
}

package com.andreas_kratzer.ghosttalk.core.domain.pages

import com.andreas_kratzer.ghosttalk.core.data.BookRepository
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.TemplateRepository
import com.andreas_kratzer.ghosttalk.core.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.Page
import com.andreas_kratzer.ghosttalk.core.util.GridUtils
import java.util.UUID
import javax.inject.Inject

class CreatePageUseCase @Inject constructor(
    private val pageRepository: PageRepository,
    private val settingsRepository: SettingsRepository,
    private val templateRepository: TemplateRepository,
    private val bookRepository: BookRepository
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

        val (finalRows, finalColumns) = if (templateId != null) {
            val template = templateRepository.getById(templateId)
                ?: throw IllegalArgumentException("Template not found: $templateId")
            template.rows to template.columns
        } else {
            rows to columns
        }

        if (finalRows > 7 || finalColumns > 7) {
            throw IllegalArgumentException("Grid size cannot exceed 7x7")
        }

        var buttonConfigs: List<ButtonConfig?>

        val template = templateId?.let { templateRepository.getById(it) }

        if (template != null) {
            buttonConfigs = template.buttonConfigs.map { config ->
                if (config != null) {
                    val action = config.buttonAction
                    val newAction = if (action is NavigateToPageButtonAction && action.pageId.isEmpty() && homePageId != null) {
                        action.copy(pageId = homePageId)
                    } else {
                        action
                    }
                    config.copy(
                        id = UUID.randomUUID().toString(),
                        buttonAction = newAction
                    )
                } else {
                    null
                }
            }
        } else {
            val initialConfigs = MutableList<ButtonConfig?>(GridUtils.TOTAL_SLOTS) { null }

            if (homePageId != null) {
                // Place Home button at bottom-right of the requested grid (spatial)
                val persistentIndex = GridUtils.getGlobalIndex(finalRows - 1, finalColumns - 1)
                
                initialConfigs[persistentIndex] = ButtonConfig(
                    id = UUID.randomUUID().toString(),
                    label = "zurück zum Start",
                    spokenText = "Zurück zur Startseite",
                    buttonAction = NavigateToPageButtonAction(
                        pageId = homePageId
                    ),
                    auditoryCue = AuditoryCue.TextToSpeechCue("Zurück zur Startseite")
                )
            }
            buttonConfigs = initialConfigs
        }

        val maxOrderIndex = currentPages.maxOfOrNull { it.orderIndex } ?: -1
        
        val newPage = Page(
            id = newPageId,
            bookId = bookId,
            name = name,
            rows = finalRows,
            columns = finalColumns,
            buttonConfigs = GridUtils.adjustButtonConfigs(buttonConfigs, finalRows, finalColumns),
            orderIndex = maxOrderIndex + 1,
            createdAt = System.currentTimeMillis()
        )
        
        pageRepository.insertPage(newPage)
        bookRepository.updateLastModified(bookId) // Moved logic here
        return newPageId
    }
}
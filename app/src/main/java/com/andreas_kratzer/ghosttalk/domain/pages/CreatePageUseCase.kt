package com.andreas_kratzer.ghosttalk.domain.pages

import com.andreas_kratzer.ghosttalk.data.BookRepository
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.data.TemplateRepository
import com.andreas_kratzer.ghosttalk.model.AuditoryCue
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.model.Page
import com.andreas_kratzer.ghosttalk.ui.util.GridUtils
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

        if (finalRows > 6 || finalColumns > 6) {
            throw IllegalArgumentException("Grid size cannot exceed 6x6")
        }

        var buttonConfigs: List<ButtonConfig?>

        val template = templateId?.let { templateRepository.getById(it) }

        if (template != null) {
            buttonConfigs = template.buttonConfigs.map { config ->
                val action = config?.buttonAction
                if (action is NavigateToPageButtonAction) {
                    if (action.pageId.isEmpty() && homePageId != null) {
                        config.copy(buttonAction = action.copy(pageId = homePageId))
                    } else config
                } else config
            }
            
        } else {
            val initialConfigs = MutableList<ButtonConfig?>(GridUtils.TOTAL_SLOTS) { null }

            if (homePageId != null) {
                // Determine persistent index for "back to start"
                // Usually we anchor it to the bottom-right of the current grid (e.g. at index rows*columns - 1)
                // BUT to preserve it across resizes, we might want to put it at 35 or calculate based on rows/cols
                // Here we stick to bottom-right of the INITIAL grid size
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
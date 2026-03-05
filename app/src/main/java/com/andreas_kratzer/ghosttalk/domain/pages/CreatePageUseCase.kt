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

        var finalRows = rows
        var finalColumns = columns
        var buttonConfigs: List<ButtonConfig?>

        val template = templateId?.let { templateRepository.getById(it) }

        if (template != null) {
            finalRows = template.rows
            finalColumns = template.columns
            
            if (finalRows * finalColumns > 36 || finalRows > 6 || finalColumns > 6) {
                throw IllegalArgumentException("Maximale Grid-Größe ist 6x6")
            }

            buttonConfigs = template.buttonConfigs.map { config ->
                val action = config?.buttonAction
                if (action is NavigateToPageButtonAction) {
                    if (action.pageId.isEmpty() && homePageId != null) {
                        config.copy(buttonAction = action.copy(pageId = homePageId))
                    } else config
                } else config
            }
            
        } else {
            if (finalRows * finalColumns > 36 || finalRows > 6 || finalColumns > 6) {
                throw IllegalArgumentException("Maximale Grid-Größe ist 6x6")
            }

            val totalSlots = finalRows * finalColumns
            val initialConfigs = MutableList<ButtonConfig?>(totalSlots) { null }

            if (totalSlots > 0 && homePageId != null) {
                initialConfigs[totalSlots - 1] = ButtonConfig(
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
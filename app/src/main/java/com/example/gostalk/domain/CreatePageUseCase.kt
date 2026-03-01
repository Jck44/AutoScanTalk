package com.example.gostalk.domain

import com.example.gostalk.data.PageRepository
import com.example.gostalk.data.SettingsRepository
import com.example.gostalk.model.AuditoryCue
import com.example.gostalk.model.ButtonConfig
import com.example.gostalk.model.NavigateToPageButtonAction
import com.example.gostalk.model.Page
import java.util.UUID

/**
 * Use case for creating a new page with a default home button.
 */
class CreatePageUseCase(
    private val pageRepository: PageRepository,
    private val settingsRepository: SettingsRepository
) {
    suspend fun execute(name: String, rows: Int, columns: Int, bookId: String, currentPages: List<Page>): String {
        val newPageId = UUID.randomUUID().toString()
        val totalSlots = rows * columns
        val buttonConfigs = MutableList<ButtonConfig?>(totalSlots) { null }

        if (totalSlots > 0) {
            val homePageId = settingsRepository.defaultStartPageId ?: currentPages.firstOrNull()?.id
            
            if (homePageId != null) {
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
            rows = rows,
            columns = columns,
            buttonConfigs = buttonConfigs
        )
        
        pageRepository.insertPage(newPage)
        return newPageId
    }
}

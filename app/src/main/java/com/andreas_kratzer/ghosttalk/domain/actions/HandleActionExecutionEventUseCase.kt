package com.andreas_kratzer.ghosttalk.domain.actions

import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.actions.ActionExecutor
import com.andreas_kratzer.ghosttalk.data.PageRepository
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.model.Page
import javax.inject.Inject

class HandleActionExecutionEventUseCase @Inject constructor(
    private val pageRepository: PageRepository,
    private val settingsRepository: SettingsRepository
) {
    sealed class Effect {
        data class LoadPage(val page: Page, val logMessage: String) : Effect()
        data class LogAction(val message: String) : Effect()
        data class SpeakError(val messageResId: Int, val logMessage: String) : Effect()
        data class EmitAuthIntent(val intent: android.content.Intent) : Effect()
    }

    suspend fun execute(event: ActionExecutor.ExecutionEvent): Effect? {
        return when (event) {
            is ActionExecutor.ExecutionEvent.NavigateToPage -> {
                val page = pageRepository.getPageById(event.pageId)
                if (page != null) {
                    val idSuffix = if (settingsRepository.showPageIdInLog) " (ID: ${event.pageId})" else ""
                    Effect.LoadPage(page, "Navigiert zu Seite: ${page.name}$idSuffix")
                } else {
                    val idSuffix = if (settingsRepository.showPageIdInLog) " mit ID '${event.pageId}'" else ""
                    Effect.SpeakError(R.string.error_page_not_found, "Fehler: Seite$idSuffix nicht gefunden.")
                }
            }
            is ActionExecutor.ExecutionEvent.Log -> Effect.LogAction(event.message)
            is ActionExecutor.ExecutionEvent.Error -> Effect.LogAction("Fehler: ${event.message}")
            is ActionExecutor.ExecutionEvent.RecoverableAuthError -> Effect.EmitAuthIntent(event.intent)
        }
    }
}

package com.andreas_kratzer.ghosttalk.domain.actions

import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.actions.ActionExecutionEvent
import com.andreas_kratzer.ghosttalk.core.data.PageRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.model.Page
import javax.inject.Inject

class HandleActionExecutionEventUseCase @Inject constructor(
    private val pageRepository: PageRepository,
    private val settingsRepository: SettingsRepository
) {
    sealed class Effect {
        data class LoadPage(val page: Page, val logMessage: String, val action: com.andreas_kratzer.ghosttalk.core.model.ButtonAction? = null, val label: String? = null) : Effect()
        data class GoBack(val logMessage: String, val action: com.andreas_kratzer.ghosttalk.core.model.ButtonAction? = null, val label: String? = null) : Effect()
        data class LogAction(val message: String, val action: com.andreas_kratzer.ghosttalk.core.model.ButtonAction? = null, val label: String? = null) : Effect()
        data class SpeakError(val messageResId: Int, val logMessage: String, val action: com.andreas_kratzer.ghosttalk.core.model.ButtonAction? = null, val label: String? = null) : Effect()
        data class EmitAuthIntent(val intent: android.content.Intent) : Effect()
        data class RequestPermissions(val permissions: Array<String>) : Effect()
    }

    suspend fun execute(event: ActionExecutionEvent): Effect? {
        return when (event) {
            is ActionExecutionEvent.NavigateToPage -> {
                val page = pageRepository.getPageById(event.pageId)
                if (page != null) {
                    val idSuffix = if (settingsRepository.showPageIdInLog) " (ID: ${event.pageId})" else ""
                    Effect.LoadPage(page, "Navigiert zu Seite: ${page.name}$idSuffix", event.action, event.label)
                } else {
                    val idSuffix = if (settingsRepository.showPageIdInLog) " mit ID '${event.pageId}'" else ""
                    Effect.SpeakError(R.string.error_page_not_found, "Fehler: Seite$idSuffix nicht gefunden.", event.action, event.label)
                }
            }
            is ActionExecutionEvent.NavigateBack -> {
                Effect.GoBack("Zur vorherigen Seite zurückgekehrt", event.action, event.label)
            }
            is ActionExecutionEvent.Log -> Effect.LogAction(event.message, event.action, event.label)
            is ActionExecutionEvent.Error -> Effect.LogAction("Fehler: ${event.message}", event.action, event.label)
            is ActionExecutionEvent.RecoverableAuthError -> Effect.EmitAuthIntent(event.intent)
            is ActionExecutionEvent.RequestPermissions -> Effect.RequestPermissions(event.permissions)
            // VocalSwitchTriggered is handled directly in PageViewModel; no effect needed here.
            is ActionExecutionEvent.VocalSwitchTriggered -> null
        }
    }
}

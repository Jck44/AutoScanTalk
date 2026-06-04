package com.andreas_kratzer.ghosttalk.core.actions

import com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.MarkAccidentalButtonAction
import kotlinx.coroutines.launch
import javax.inject.Inject


class MarkAccidentalActionHandler @Inject constructor(
    private val buttonUsageRepository: ButtonUsageRepository,
    private val settingsRepository: SettingsRepository,
    private val ttsHelper: ActionTtsProxy
) : ActionHandler {

    override fun canHandle(action: ButtonAction): Boolean = action is MarkAccidentalButtonAction

    override fun handle(
        buttonConfig: ButtonConfig,
        action: ButtonAction,
        executionId: Int,
        onFinish: (Int) -> Unit
    ) {
        if (action !is MarkAccidentalButtonAction) {
            onFinish(executionId)
            return
        }

        // We need bookId. Since handle interface lacks bookId, get activeBookId from settings.
        // Let's launch a coroutine:
        kotlinx.coroutines.MainScope().launch {
            try {
                val bookId = settingsRepository.activeBookId
                val marked = buttonUsageRepository.markLastUsageAsAccidental(bookId)
                if (marked) {
                    ttsHelper.speakRouted("Als unabsichtlich markiert", null, 0, false) {
                        onFinish(executionId)
                    }
                } else {
                    ttsHelper.speakRouted("Keine Aktion zum Markieren vorhanden", null, 0, false) {
                        onFinish(executionId)
                    }
                }
            } catch (e: Exception) {
                onFinish(executionId)
            }
        }
    }
}



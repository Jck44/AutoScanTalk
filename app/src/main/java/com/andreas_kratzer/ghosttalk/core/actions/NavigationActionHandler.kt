package com.andreas_kratzer.ghosttalk.core.actions

import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.model.ButtonAction
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class NavigationActionHandler(
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val ttsHelper: TextToSpeechHelper?,
    private val emitEvent: suspend (ActionExecutor.ExecutionEvent) -> Unit,
    private val log: (String) -> Unit
) : ActionHandler<NavigateToPageButtonAction> {

    override fun canHandle(action: ButtonAction): Boolean = action is NavigateToPageButtonAction

    override fun handle(
        buttonConfig: ButtonConfig,
        action: NavigateToPageButtonAction,
        executionId: Int,
        onFinish: (Int) -> Unit
    ) {
        val feedback = buttonConfig.spokenText?.takeIf { it.isNotBlank() }
        
        val performNavigation = {
            scope.launch {
                emitEvent(ActionExecutor.ExecutionEvent.NavigateToPage(action.pageId))
                onFinish(executionId)
            }
        }

        if (feedback != null && ttsHelper?.isReady == true) {
            ttsHelper.speakRouted(
                text = feedback,
                deviceAddress = settingsRepository.cuesAudioDeviceAddress,
                ttsMode = action.ttsMode,
                queueMode = android.speech.tts.TextToSpeech.QUEUE_FLUSH,
                isForCues = true,
                onDone = { performNavigation() }
            )
            log("Navigations-Feedback: \"$feedback\"")
        } else {
            if (feedback != null) log("Nav-Feedback (TTS nicht bereit): \"$feedback\"")
            performNavigation()
        }
    }
}

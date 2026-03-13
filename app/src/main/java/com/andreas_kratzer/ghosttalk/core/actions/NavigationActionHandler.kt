package com.andreas_kratzer.ghosttalk.core.actions

import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class NavigationActionHandler(
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val ttsHelperLazy: dagger.Lazy<TextToSpeechHelper>,
    private val emitEvent: suspend (ActionExecutor.ExecutionEvent) -> Unit,
    private val log: (String) -> Unit
) : ActionHandler {

    override fun canHandle(action: ButtonAction): Boolean = action is NavigateToPageButtonAction

    override fun handle(
        buttonConfig: ButtonConfig,
        action: ButtonAction,
        executionId: Int,
        onFinish: (Int) -> Unit
    ) {
        val navAction = action as NavigateToPageButtonAction
        val feedback = buttonConfig.spokenText?.takeIf { it.isNotBlank() }
        
        val performNavigation = {
            scope.launch {
                emitEvent(ActionExecutor.ExecutionEvent.NavigateToPage(navAction.pageId))
                onFinish(executionId)
            }
        }

        if (feedback != null) {
            val ttsHelper = ttsHelperLazy.get()
            if (ttsHelper.isReady) {
                ttsHelper.speakRouted(
                    text = feedback,
                    deviceAddress = settingsRepository.cuesAudioDeviceAddress,
                    queueMode = 0, // QUEUE_FLUSH
                    isForCues = true,
                    onDone = { performNavigation() }
                )
                log("Navigations-Feedback: \"$feedback\"")
            } else {
                log("Nav-Feedback (TTS nicht bereit): \"$feedback\"")
                performNavigation()
            }
        } else {
            performNavigation()
        }
    }
}

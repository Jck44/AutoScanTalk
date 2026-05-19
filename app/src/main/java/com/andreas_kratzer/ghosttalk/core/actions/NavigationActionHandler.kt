package com.andreas_kratzer.ghosttalk.core.actions

import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.di.ApplicationScope
import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

class NavigationActionHandler @Inject constructor(
    @param:ApplicationScope private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val ttsProxyLazy: dagger.Lazy<ActionTtsProxy>,
    private val actionEventEmitter: ActionEventEmitter,
    private val actionLogger: ActionLogger
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
                actionEventEmitter.emitEvent(
                    ActionEvent.NavigateToPage(navAction.pageId, action, buttonConfig.label)
                )
                onFinish(executionId)
            }
        }

        if (feedback != null) {
            val ttsHelper = ttsProxyLazy.get()
            if (ttsHelper.isReady) {
                ttsHelper.speakRouted(
                    text = feedback,
                    deviceAddress = settingsRepository.cuesAudioDeviceAddress,
                    queueMode = 0, // QUEUE_FLUSH
                    isForCues = true,
                    onDone = { performNavigation() }
                )
                actionLogger.log("Navigations-Feedback: \"$feedback\"", action, buttonConfig.label)
            } else {
                actionLogger.log("Nav-Feedback (TTS nicht bereit): \"$feedback\"", action, buttonConfig.label)
                performNavigation()
            }
        } else {
            performNavigation()
        }
    }
}

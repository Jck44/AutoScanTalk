package com.andreas_kratzer.ghosttalk.core.actions

import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.di.ApplicationScope
import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.NavigateToPageButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateBackButtonAction
import com.andreas_kratzer.ghosttalk.core.model.NavigateToStartPageButtonAction
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

    override fun canHandle(action: ButtonAction): Boolean =
        action is NavigateToPageButtonAction || action is NavigateBackButtonAction || action is NavigateToStartPageButtonAction

    override fun handle(
        buttonConfig: ButtonConfig,
        action: ButtonAction,
        executionId: Int,
        onFinish: (Int) -> Unit
    ) {
        val feedback = buttonConfig.spokenText?.takeIf { it.isNotBlank() }
        
        val performNavigation = {
            scope.launch {
                when (action) {
                    is NavigateToPageButtonAction -> {
                        val targetPageId = if (action.pageId.isEmpty()) {
                            settingsRepository.defaultStartPageId ?: ""
                        } else {
                            action.pageId
                        }
                        actionEventEmitter.emitEvent(
                            ActionEvent.NavigateToPage(targetPageId, action, buttonConfig.label)
                        )
                    }
                    is NavigateToStartPageButtonAction -> {
                        val targetPageId = settingsRepository.defaultStartPageId ?: ""
                        actionEventEmitter.emitEvent(
                            ActionEvent.NavigateToPage(targetPageId, action, buttonConfig.label)
                        )
                    }
                    is NavigateBackButtonAction -> {
                        actionEventEmitter.emitEvent(
                            ActionEvent.NavigateBack(action, buttonConfig.label)
                        )
                    }
                    else -> {}
                }
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

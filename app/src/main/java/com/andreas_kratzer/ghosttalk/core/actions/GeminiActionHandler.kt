package com.andreas_kratzer.ghosttalk.core.actions

import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.GeminiButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiNanoButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiSearchButtonAction
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.ai.LocalIntentRouter
import com.andreas_kratzer.ghosttalk.core.ai.domain.GeminiUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

import javax.inject.Inject
import com.andreas_kratzer.ghosttalk.core.di.ApplicationScope
import com.andreas_kratzer.ghosttalk.core.actions.ActionEvent
import com.andreas_kratzer.ghosttalk.core.actions.ActionEventEmitter
import android.content.Context

class GeminiActionHandler @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val geminiUseCaseLazy: dagger.Lazy<GeminiUseCase>,
    private val localIntentRouter: LocalIntentRouter,
    private val ttsProxyLazy: dagger.Lazy<ActionTtsProxy>,
    private val actionLogger: ActionLogger,
    private val actionEventEmitter: ActionEventEmitter
) : ActionHandler {

    private val getString: (Int, Array<out Any>) -> String = { id, args ->
        try { context.getString(id, *args) } catch (_: Exception) { "" }
    }

    override fun canHandle(action: ButtonAction): Boolean = 
        action is GeminiButtonAction || action is GeminiSearchButtonAction || action is GeminiNanoButtonAction

    override fun handle(
        buttonConfig: ButtonConfig,
        action: ButtonAction,
        executionId: Int,
        onFinish: (Int) -> Unit
    ) {
        val targetDeviceAddress = if (buttonConfig.playActionAsAuditoryCue) {
            settingsRepository.cuesAudioDeviceAddress
        } else {
            settingsRepository.ttsAudioDeviceAddress
        }

        scope.launch {
            try {
                if (action is GeminiNanoButtonAction) {
                    if (!settingsRepository.useLocalGenerativeAi) {
                        speakError("Lokale KI ist in den Einstellungen deaktiviert.", targetDeviceAddress, executionId, onFinish)
                        return@launch
                    }
                    actionLogger.log("Lokale Intent-Ausführung: ${action.intent}")
                    localIntentRouter.executeIntent(action.intent) { response ->
                        speakResponse(response, targetDeviceAddress, executionId, onFinish)
                    }
                    return@launch
                }

                if (!settingsRepository.isGeminiEnabled) {
                    speakError("Gemini ist in den Einstellungen deaktiviert.", targetDeviceAddress, executionId, onFinish)
                    onFinish(executionId)
                    return@launch
                }

                actionLogger.log("Gemini wird angefragt...")
                val prompt = when(action) {
                    is GeminiButtonAction -> action.prompt
                    is GeminiSearchButtonAction -> action.prompt
                    else -> ""
                }
                
                val response = try {
                    geminiUseCaseLazy.get().generateResponse(
                        prompt = prompt,
                        useGoogleSearch = action is GeminiSearchButtonAction
                    )
                } catch (e: Exception) {
                    val msg = e.message ?: ""
                    if (msg.contains("429")) {
                        val seconds = msg.substringAfter("429").filter { it.isDigit() }.toIntOrNull() ?: 60
                        val localizedError = getString(com.andreas_kratzer.ghosttalk.R.string.error_gemini_quota_reached, arrayOf(seconds))
                        actionLogger.log(localizedError)
                        speakError(localizedError, targetDeviceAddress, executionId, onFinish)
                    } else {
                        actionLogger.error("Gemini Fehler: $msg", e)
                        speakError("Gemini Fehler: $msg", targetDeviceAddress, executionId, onFinish)
                    }
                    return@launch
                }

                speakResponse(response, targetDeviceAddress, executionId, onFinish)
            } catch (e: Exception) {
                actionLogger.error("Unerwarteter Gemini Fehler", e)
                onFinish(executionId)
            }
        }
    }

    private fun speakResponse(text: String, deviceAddress: String?, executionId: Int, onFinish: (Int) -> Unit) {
        actionLogger.log(text)
        val tts = ttsProxyLazy.get()
        if (tts.isReady) {
            tts.speakRouted(text, deviceAddress) {
                onFinish(executionId)
            }
        } else {
            onFinish(executionId)
        }
    }

    private fun speakError(text: String, deviceAddress: String?, executionId: Int, onFinish: (Int) -> Unit) {
        actionLogger.log(text)
        val tts = ttsProxyLazy.get()
        if (tts.isReady) {
            tts.speakRouted(text, deviceAddress) {
                onFinish(executionId)
            }
        } else {
            onFinish(executionId)
        }
    }
}

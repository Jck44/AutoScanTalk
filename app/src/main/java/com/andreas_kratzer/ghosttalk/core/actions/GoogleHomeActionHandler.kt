package com.andreas_kratzer.ghosttalk.core.actions

import com.andreas_kratzer.ghosttalk.core.cloud.GoogleHomeManager
import com.andreas_kratzer.ghosttalk.core.di.ApplicationScope
import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.GoogleHomeButtonAction
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handler for Google Home device control actions.
 */
class GoogleHomeActionHandler @Inject constructor(
    @param:ApplicationScope private val scope: CoroutineScope,
    private val googleHomeManagerLazy: Lazy<GoogleHomeManager>,
    private val actionLogger: ActionLogger,
    private val ttsProxyLazy: Lazy<ActionTtsProxy>,
    private val settingsRepository: SettingsRepository
) : ActionHandler {

    override fun canHandle(action: ButtonAction): Boolean = action is GoogleHomeButtonAction

    override fun handle(
        buttonConfig: ButtonConfig,
        action: ButtonAction,
        executionId: Int,
        onFinish: (Int) -> Unit
    ) {
        val googleHomeAction = action as GoogleHomeButtonAction
        
        scope.launch {
            try {
                actionLogger.log("Steuere Google Home Gerät: ${googleHomeAction.deviceName} (${googleHomeAction.command})")
                
                val params = mutableMapOf<String, Any>()
                googleHomeAction.value?.let { params["value"] = it }
                
                
                val success = googleHomeManagerLazy.get().executeCommand(
                    projectId = settingsRepository.googleHomeProjectId,
                    deviceId = googleHomeAction.deviceId,
                    trait = googleHomeAction.trait,
                    command = googleHomeAction.command,
                    params = params
                )
                
                if (success) {
                    val message = "${googleHomeAction.deviceName} wurde auf '${googleHomeAction.command}' gesetzt."
                    speakResponse(message, executionId, onFinish)
                } else {
                    speakResponse("Fehler beim Steuern von ${googleHomeAction.deviceName}.", executionId, onFinish)
                }
            } catch (e: Exception) {
                actionLogger.error("Fehler in GoogleHomeActionHandler", e)
                speakResponse("Ein unerwarteter Fehler ist aufgetreten.", executionId, onFinish)
            }
        }
    }

    private fun speakResponse(text: String, executionId: Int, onFinish: (Int) -> Unit) {
        val tts = ttsProxyLazy.get()
        if (tts.isReady) {
            tts.speakRouted(text, null) {
                onFinish(executionId)
            }
        } else {
            onFinish(executionId)
        }
    }
}

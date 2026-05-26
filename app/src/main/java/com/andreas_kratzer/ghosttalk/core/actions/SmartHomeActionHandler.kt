package com.andreas_kratzer.ghosttalk.core.actions

import com.andreas_kratzer.ghosttalk.core.cloud.PhilipsHueManager
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.di.ApplicationScope
import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.SmartHomeButtonAction
import com.andreas_kratzer.ghosttalk.core.model.SmartHomeProvider
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handler for unified Smart Home device control actions.
 */
class SmartHomeActionHandler @Inject constructor(
    @param:ApplicationScope private val scope: CoroutineScope,
    private val hueManagerLazy: Lazy<PhilipsHueManager>,
    private val actionLogger: ActionLogger,
    private val ttsProxyLazy: Lazy<ActionTtsProxy>,
    private val settingsRepository: SettingsRepository
) : ActionHandler {

    override fun canHandle(action: ButtonAction): Boolean = action is SmartHomeButtonAction

    override fun handle(
        buttonConfig: ButtonConfig,
        action: ButtonAction,
        executionId: Int,
        onFinish: (Int) -> Unit
    ) {
        val smartHomeAction = action as SmartHomeButtonAction
        
        scope.launch {
            try {
                when (smartHomeAction.provider) {
                    SmartHomeProvider.GOOGLE_HOME -> {
                        actionLogger.log("Google Home wird nicht mehr unterstützt.", smartHomeAction, buttonConfig.label)
                        speakResponse("Google Home wird nicht mehr unterstützt.", executionId, buttonConfig.label, smartHomeAction, onFinish)
                    }
                    SmartHomeProvider.PHILIPS_HUE -> handlePhilipsHue(smartHomeAction, buttonConfig.label, executionId, onFinish)
                }
            } catch (e: Exception) {
                actionLogger.error("Fehler in SmartHomeActionHandler", e, smartHomeAction, buttonConfig.label)
                speakResponse("Ein unerwarteter Fehler ist aufgetreten.", executionId, buttonConfig.label, smartHomeAction, onFinish)
            }
        }
    }

    private suspend fun handlePhilipsHue(action: SmartHomeButtonAction, label: String, executionId: Int, onFinish: (Int) -> Unit) {
        actionLogger.log("Steuere Philips Hue: ${action.deviceName} (${action.intent})", action, label)
        
        val success = hueManagerLazy.get().executeLocalCommand(
            bridgeIp = settingsRepository.hueBridgeIp,
            username = settingsRepository.hueUsername,
            lightId = action.deviceId,
            intent = action.intent,
            value = action.value
        )
        
        if (success) {
            val actionText = when (action.intent) {
                "action.on" -> "eingeschaltet"
                "action.off" -> "ausgeschaltet"
                "action.brightness" -> {
                    val percent = action.value?.toIntOrNull() ?: 100
                    "auf $percent Prozent Helligkeit gesetzt"
                }
                "action.color" -> {
                    val colorName = action.value ?: "Standard"
                    "auf Farbe $colorName gesetzt"
                }
                else -> "auf '${action.intent.substringAfterLast(".")}' gesetzt"
            }
            val message = "${action.deviceName} wurde $actionText."
            speakResponse(message, executionId, label, action, onFinish)
        } else {
            speakResponse("Fehler beim Steuern von ${action.deviceName}.", executionId, label, action, onFinish)
        }
    }
    


    private fun speakResponse(text: String, executionId: Int, label: String, action: ButtonAction, onFinish: (Int) -> Unit) {
        actionLogger.log(text, action, label)
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


package com.andreas_kratzer.ghosttalk.core.actions

import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.domain.genai.GeminiUseCase
import com.andreas_kratzer.ghosttalk.model.ButtonAction
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.GeminiButtonAction
import com.andreas_kratzer.ghosttalk.model.GeminiNanoButtonAction
import com.andreas_kratzer.ghosttalk.model.GeminiSearchButtonAction
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class GeminiActionHandler(
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val geminiUseCase: GeminiUseCase?,
    private val localIntentRouter: com.andreas_kratzer.ghosttalk.domain.executors.LocalIntentRouter,
    private val ttsHelper: TextToSpeechHelper?,
    private val emitEvent: suspend (ActionExecutor.ExecutionEvent) -> Unit,
    private val log: (String) -> Unit
) : ActionHandler { // Handles both GeminiButtonAction and GeminiSearchButtonAction

    override fun canHandle(action: ButtonAction): Boolean = 
        action is GeminiButtonAction || action is GeminiSearchButtonAction || action is GeminiNanoButtonAction

    override fun handle(
        buttonConfig: ButtonConfig,
        action: ButtonAction,
        executionId: Int,
        onFinish: (Int) -> Unit
    ) {
        val isNanoAction = action is GeminiNanoButtonAction
        val nanoIntent = (action as? GeminiNanoButtonAction)?.intent ?: ""
        
        val prompt = when (action) {
            is GeminiButtonAction -> action.prompt
            is GeminiSearchButtonAction -> action.prompt
            is GeminiNanoButtonAction -> nanoIntent
            else -> return
        }
        val useGoogleSearch = action is GeminiSearchButtonAction
        val ttsMode = when (action) {
            is GeminiButtonAction -> action.ttsMode
            is GeminiSearchButtonAction -> action.ttsMode
            is GeminiNanoButtonAction -> action.ttsMode
        }

        log("Gemini ${if (useGoogleSearch) "Suche " else if (isNanoAction) "Nano ($nanoIntent) " else ""}aufgerufen.")
        
        val targetDeviceAddress = if (buttonConfig.playActionAsAuditoryCue) {
            settingsRepository.cuesAudioDeviceAddress
        } else {
            settingsRepository.ttsAudioDeviceAddress
        }

        scope.launch {
            val tts = ttsHelper
            try {
                if (isNanoAction) {
                    if (!settingsRepository.useLocalGenerativeAi) {
                        val errorMsg = tts?.context?.getString(R.string.error_gemini_disabled) 
                            ?: "Gemini Nano in Einstellungen aktivieren"
                        if (tts != null) {
                            tts.speakRouted(errorMsg, targetDeviceAddress, ttsMode) {
                                onFinish(executionId)
                            }
                        } else { onFinish(executionId) }
                        return@launch
                    }

                    localIntentRouter.executeIntent(nanoIntent) { response ->
                        val displayResponse = if (response.length > 50) response.take(50) + "..." else response
                        log("Gemini Nano [${nanoIntent}] Antwort: '$displayResponse'")

                        if (tts?.isReady == true) {
                            tts.speakRouted(response, targetDeviceAddress, ttsMode) {
                                onFinish(executionId)
                            }
                        } else {
                            onFinish(executionId)
                        }
                    }
                    return@launch
                }

                // Cloud actions path
                if (!settingsRepository.isGeminiEnabled) {
                    val errorMsg = tts?.context?.getString(R.string.error_gemini_disabled) 
                        ?: "Gemini in Einstellungen prüfen"
                    if (tts != null) {
                        tts.speakRouted(errorMsg, targetDeviceAddress, ttsMode) {
                            onFinish(executionId)
                        }
                    } else { onFinish(executionId) }
                    return@launch
                }

                val response = geminiUseCase?.generateResponse(prompt, useGoogleSearch = useGoogleSearch) 
                    ?: "Fehler: Gemini Integration nicht verfügbar."
                
                if (tts?.isReady == true) {
                    tts.speakRouted(response, targetDeviceAddress, ttsMode) {
                        onFinish(executionId)
                    }
                } else {
                    log("Gemini Ergebnis: \"$response\"")
                    onFinish(executionId)
                }
            } catch (e: UserRecoverableAuthIOException) {
                log("Gemini: Berechtigung erforderlich.")
                e.intent?.let { emitEvent(ActionExecutor.ExecutionEvent.RecoverableAuthError(it)) }
                onFinish(executionId)
            } catch (e: UserRecoverableAuthException) {
                log("Gemini: Berechtigung erforderlich.")
                e.intent?.let { emitEvent(ActionExecutor.ExecutionEvent.RecoverableAuthError(it)) }
                onFinish(executionId)
            } catch (e: Exception) {
                val message = e.message ?: ""
                if (message.contains("429")) {
                    val remainingMatch = Regex("wait (\\d+) seconds", RegexOption.IGNORE_CASE).find(message)
                    val seconds = remainingMatch?.groupValues?.get(1)?.toIntOrNull() ?: 60

                    val quotaMsg = tts?.context?.getString(
                        R.string.error_gemini_quota_reached, seconds
                    ) ?: "Gemini-Limit erreicht. Bitte $seconds Sekunden warten."
                    
                    log(quotaMsg)
                    if (tts?.isReady == true) {
                        tts.speakRouted(quotaMsg, settingsRepository.ttsAudioDeviceAddress, ttsMode) {
                            onFinish(executionId)
                        }
                    } else {
                        onFinish(executionId)
                    }
                } else {
                    log("Gemini Fehler: $message")
                    onFinish(executionId)
                }
            }
        }
    }
}

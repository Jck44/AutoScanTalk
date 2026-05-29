package com.andreas_kratzer.ghosttalk.core.actions

import android.content.Context
import androidx.core.graphics.createBitmap
import com.andreas_kratzer.ghosttalk.core.ai.LocalIntentRouter
import com.andreas_kratzer.ghosttalk.core.ai.domain.GeminiUseCase
import com.andreas_kratzer.ghosttalk.core.ai.domain.VisionUseCase
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.di.ApplicationScope
import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.GeminiButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiNanoButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiSearchButtonAction
import com.andreas_kratzer.ghosttalk.core.model.GeminiVisionButtonAction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

class GeminiActionHandler @Inject constructor(
    @param:ApplicationScope private val scope: CoroutineScope,
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val geminiUseCaseLazy: dagger.Lazy<GeminiUseCase>,
    private val visionUseCase: VisionUseCase,
    private val localIntentRouter: LocalIntentRouter,
    private val ttsProxyLazy: dagger.Lazy<ActionTtsProxy>,
    private val actionLogger: ActionLogger,
    private val actionEventEmitter: ActionEventEmitter,
    private val buttonUsageRepository: com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository,
    private val cameraProvider: CameraProvider
) : ActionHandler {

    private val mediaActionSound = android.media.MediaActionSound().apply {
        load(android.media.MediaActionSound.SHUTTER_CLICK)
    }

    private val getString: (Int, Array<out Any>) -> String = { id, args ->
        try { context.getString(id, *args) } catch (_: Exception) { "" }
    }

    override fun canHandle(action: ButtonAction): Boolean = 
        action is GeminiButtonAction || action is GeminiSearchButtonAction || 
                action is GeminiNanoButtonAction || action is GeminiVisionButtonAction

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
                        speakError("Lokale KI ist in den Einstellungen deaktiviert.", targetDeviceAddress, executionId, buttonConfig.label, action, onFinish)
                        return@launch
                    }
                    actionLogger.log("Lokale Intent-Ausführung: ${action.intent}", action, buttonConfig.label)
                    localIntentRouter.executeIntent(action.intent) { response ->
                        speakResponse(response, targetDeviceAddress, executionId, buttonConfig.label, action, onFinish)
                    }
                    return@launch
                }

                if (action is GeminiVisionButtonAction) {
                    actionLogger.log("Gemini Vision (KI Auge) wird gestartet...", action, buttonConfig.label)
                    
                    if (action.playShutterSound) {
                        try {
                            mediaActionSound.play(android.media.MediaActionSound.SHUTTER_CLICK)
                        } catch (e: Exception) {
                            actionLogger.error("Failed to play shutter sound", e)
                        }
                    }
                    
                    if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        speakError(getString(com.andreas_kratzer.ghosttalk.R.string.error_camera_permission_missing, emptyArray()), targetDeviceAddress, executionId, buttonConfig.label, action, onFinish)
                        return@launch
                    }

                    // Capture real image or fall back to mock
                    val capturedBitmap = cameraProvider.captureImage()
                    
                    val finalBitmap = if (capturedBitmap != null) {
                        actionLogger.log("Echtes Kamerabild erfasst.", action, buttonConfig.label)
                        capturedBitmap
                    } else {
                        actionLogger.log("Kamerazugriff fehlgeschlagen. Simuliere Bild...", action, buttonConfig.label)
                        createBitmap(1024, 1024).also {
                            val canvas = android.graphics.Canvas(it)
                            canvas.drawColor(android.graphics.Color.LTGRAY)
                            val paint = android.graphics.Paint().apply {
                                color = android.graphics.Color.BLACK
                                textSize = 40f
                            }
                            canvas.drawText("Simuliertes Kamerabild (Fallback)", 100f, 500f, paint)
                        }
                    }
                    
                    // Save image to temporary file for the history
                    try {
                        val historyDir = java.io.File(context.cacheDir, "action_history_images").apply { mkdirs() }
                        val imageFile = java.io.File(historyDir, "vision_${System.currentTimeMillis()}.jpg")
                        java.io.FileOutputStream(imageFile).use { out ->
                            finalBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                        }
                        buttonUsageRepository.updateLastEventImage(imageFile.absolutePath)
                    } catch (e: Exception) {
                        actionLogger.error("Failed to save vision image for history", e)
                    }

                    val response = visionUseCase.describeImage(
                        bitmap = finalBitmap,
                        prompt = action.prompt,
                        useCloud = action.useCloud
                    )
                    speakResponse(response, targetDeviceAddress, executionId, buttonConfig.label, action, onFinish)
                    return@launch
                }

                if (!settingsRepository.isGeminiEnabled) {
                    speakError("Gemini ist in den Einstellungen deaktiviert.", targetDeviceAddress, executionId, buttonConfig.label, action, onFinish)
                    onFinish(executionId)
                    return@launch
                }

                actionLogger.log("Gemini wird angefragt...", action, buttonConfig.label)
                val prompt = when(action) {
                    is GeminiButtonAction -> action.prompt
                    is GeminiSearchButtonAction -> action.prompt
                    else -> ""
                }
                
                val response = try {
                    geminiUseCaseLazy.get().generateResponse(
                        prompt = prompt,
                        useGoogleSearch = false // Google Search is no longer supported in free tier
                    )
                } catch (e: Exception) {
                    val msg = e.message ?: ""
                    if (msg.contains("429")) {
                        // Extract wait time only if "retry in X.Xs" pattern is present, otherwise default to 60
                        val regex = Regex("retry in (\\d+\\.?\\d*)s", RegexOption.IGNORE_CASE)
                        val match = regex.find(msg)
                        val seconds = match?.groupValues?.get(1)?.toDoubleOrNull()?.toInt() ?: 60

                        val localizedError = getString(com.andreas_kratzer.ghosttalk.R.string.error_gemini_quota_reached, arrayOf(seconds))
                        actionLogger.log(localizedError, action, buttonConfig.label)
                        speakError(localizedError, targetDeviceAddress, executionId, buttonConfig.label, action, onFinish)
                    } else {
                        actionLogger.error("Gemini Fehler: $msg", e)
                        speakError("Gemini Fehler: $msg", targetDeviceAddress, executionId, buttonConfig.label, action, onFinish)
                    }
                    return@launch
                }

                speakResponse(response, targetDeviceAddress, executionId, buttonConfig.label, action, onFinish)
            } catch (e: Exception) {
                actionLogger.error("Unerwarteter Gemini Fehler", e)
                onFinish(executionId)
            }
        }
    }

    private fun speakResponse(text: String, deviceAddress: String?, executionId: Int, label: String, action: ButtonAction, onFinish: (Int) -> Unit) {
        // Clean up common Markdown formatting characters that look/sound bad in TTS/Logs
        val cleanedText = text.replace("**", "").replace("*", "").trim()
        actionLogger.log(cleanedText, action, label)
        val tts = ttsProxyLazy.get()
        scope.launch {
            buttonUsageRepository.updateLastEventDetails(cleanedText)
        }
        if (tts.isReady) {
            tts.speakRouted(cleanedText, deviceAddress) {
                onFinish(executionId)
            }
        } else {
            onFinish(executionId)
        }
    }

    private fun speakError(text: String, deviceAddress: String?, executionId: Int, label: String, action: ButtonAction, onFinish: (Int) -> Unit) {
        actionLogger.log(text, action, label)
        val tts = ttsProxyLazy.get()
        scope.launch {
            buttonUsageRepository.updateLastEventDetails(text)
        }
        if (tts.isReady) {
            tts.speakRouted(text, deviceAddress) {
                onFinish(executionId)
            }
        } else {
            onFinish(executionId)
        }
    }
}

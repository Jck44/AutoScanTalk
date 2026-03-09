package com.andreas_kratzer.ghosttalk.core.actions

import android.content.Context
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.domain.executors.WeatherExecutor
import com.andreas_kratzer.ghosttalk.model.ButtonAction
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.WeatherButtonAction
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class WeatherActionHandler(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val ttsHelper: TextToSpeechHelper?,
    private val weatherExecutor: WeatherExecutor,
    private val scope: CoroutineScope,
    private val log: (String) -> Unit
) : ActionHandler {

    override fun canHandle(action: ButtonAction): Boolean {
        return action is WeatherButtonAction
    }

    override fun handle(
        buttonConfig: ButtonConfig,
        action: ButtonAction,
        executionId: Int,
        onFinish: (Int) -> Unit
    ) {
        if (action !is WeatherButtonAction) return

        log(context.getString(R.string.action_weather_fetching))

        scope.launch {
            try {
                val weatherResult = weatherExecutor.getWeatherInfo()
                
                // Format: "Regen, 15.0 °C" or "wttr.in format"
                // User wants: "Das aktuelle Wetter: Regen bei 15 Grad"
                
                val formattedWeather = if (weatherResult.contains(",")) {
                    val parts = weatherResult.split(",")
                    val condition = parts[0].trim()
                    val tempPart = parts.getOrNull(1)?.trim() ?: ""
                    val tempValue = tempPart.replace("°C", "").trim().toDoubleOrNull() ?: 0.0
                    val roundedTemp = kotlin.math.round(tempValue).toInt()
                    context.getString(R.string.action_weather_format, condition, roundedTemp.toString())
                } else {
                    weatherResult
                }

                speakRoutedWithLogging(formattedWeather, buttonConfig, action, executionId, onFinish)
            } catch (e: Exception) {
                log(context.getString(R.string.action_weather_error, e.message ?: "Unknown error"))
                onFinish(executionId)
            }
        }
    }

    private fun speakRoutedWithLogging(
        text: String,
        config: ButtonConfig,
        action: WeatherButtonAction,
        executionId: Int,
        onFinish: (Int) -> Unit
    ) {
        val ssml = "<speak>$text</speak>"
        log(text)
        
        val targetDeviceAddress = if (config.playActionAsAuditoryCue) {
            settingsRepository.cuesAudioDeviceAddress
        } else {
            settingsRepository.ttsAudioDeviceAddress
        }
        
        if (ttsHelper?.isReady == true) {
            ttsHelper.speakRouted(ssml, targetDeviceAddress, action.ttsMode) {
                onFinish(executionId)
            }
        } else {
            onFinish(executionId)
        }
    }
}

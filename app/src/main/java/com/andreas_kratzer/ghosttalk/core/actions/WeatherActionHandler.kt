package com.andreas_kratzer.ghosttalk.core.actions

import android.content.Context
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.di.ApplicationScope
import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.WeatherButtonAction
import com.andreas_kratzer.ghosttalk.domain.executors.WeatherExecutor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

class WeatherActionHandler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val ttsProxyLazy: dagger.Lazy<ActionTtsProxy>,
    private val weatherExecutor: WeatherExecutor,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val actionLogger: ActionLogger,
    private val actionEventEmitter: ActionEventEmitter
) : ActionHandler {

    override fun canHandle(action: ButtonAction): Boolean = action is WeatherButtonAction

    override fun handle(
        buttonConfig: ButtonConfig,
        action: ButtonAction,
        executionId: Int,
        onFinish: (Int) -> Unit
    ) {
        actionLogger.log(context.getString(com.andreas_kratzer.ghosttalk.R.string.action_weather_fetching), action, buttonConfig.label)
        
        val targetDeviceAddress = if (buttonConfig.playActionAsAuditoryCue) {
            settingsRepository.cuesAudioDeviceAddress
        } else {
            settingsRepository.ttsAudioDeviceAddress
        }

        scope.launch {
            try {
                when (val result = weatherExecutor.getWeatherInfo()) {
                    is WeatherExecutor.WeatherResult.Success -> {
                        val report = context.getString(
                            com.andreas_kratzer.ghosttalk.R.string.action_weather_format,
                            result.condition,
                            result.temperature.toString()
                        )
                        actionLogger.log(report, action, buttonConfig.label)
                        val tts = ttsProxyLazy.get()
                        if (tts.isReady) {
                            tts.speakRouted(report, targetDeviceAddress) {
                                onFinish(executionId)
                            }
                        } else onFinish(executionId)
                    }
                    is WeatherExecutor.WeatherResult.Error -> {
                        val errorMessage = context.getString(com.andreas_kratzer.ghosttalk.R.string.action_weather_error, result.message)
                        actionLogger.log(errorMessage, action, buttonConfig.label)
                        val tts = ttsProxyLazy.get()
                        if (tts.isReady) {
                            tts.speakRouted(errorMessage, targetDeviceAddress) {
                                onFinish(executionId)
                            }
                        } else {
                            onFinish(executionId)
                        }
                    }
                }
            } catch (e: Exception) {
                val errorMessage = context.getString(com.andreas_kratzer.ghosttalk.R.string.action_weather_error, e.message ?: "Unknown error")
                actionLogger.log(errorMessage, action, buttonConfig.label)
                val tts = ttsProxyLazy.get()
                if (tts.isReady) {
                    tts.speakRouted(errorMessage, targetDeviceAddress) {
                        onFinish(executionId)
                    }
                } else {
                    onFinish(executionId)
                }
            }
        }
    }
}

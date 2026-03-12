package com.andreas_kratzer.ghosttalk.core.actions

import android.content.Context
import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.WeatherButtonAction
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.domain.executors.WeatherExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class WeatherActionHandler(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val ttsProxyLazy: dagger.Lazy<ActionTtsProxy>,
    private val weatherExecutor: WeatherExecutor,
    private val scope: CoroutineScope,
    private val log: (String) -> Unit
) : ActionHandler {

    override fun canHandle(action: ButtonAction): Boolean = action is WeatherButtonAction

    override fun handle(
        buttonConfig: ButtonConfig,
        action: ButtonAction,
        executionId: Int,
        onFinish: (Int) -> Unit
    ) {
        log(context.getString(com.andreas_kratzer.ghosttalk.R.string.action_weather_fetching))
        
        val targetDeviceAddress = if (buttonConfig.playActionAsAuditoryCue) {
            settingsRepository.cuesAudioDeviceAddress
        } else {
            settingsRepository.ttsAudioDeviceAddress
        }

        scope.launch {
            try {
                val weather = weatherExecutor.getWeatherInfo()
                val report = context.getString(com.andreas_kratzer.ghosttalk.R.string.action_weather_format, weather)
                log(report)
                val tts = ttsProxyLazy.get()
                if (tts.isReady) {
                    tts.speakRouted(report, targetDeviceAddress) {
                        onFinish(executionId)
                    }
                } else onFinish(executionId)
            } catch (e: Exception) {
                log(context.getString(com.andreas_kratzer.ghosttalk.R.string.action_weather_error))
                onFinish(executionId)
            }
        }
    }
}

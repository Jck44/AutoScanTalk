package com.andreas_kratzer.ghosttalk.core.actions

import android.app.Application
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.WeatherButtonAction
import com.andreas_kratzer.ghosttalk.domain.executors.WeatherExecutor
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherActionHandlerTest {

    private lateinit var application: Application
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var ttsProxy: ActionTtsProxy
    private lateinit var weatherExecutor: WeatherExecutor
    private lateinit var actionLogger: ActionLogger
    private lateinit var actionEventEmitter: ActionEventEmitter
    private lateinit var handler: WeatherActionHandler
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        application = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        ttsProxy = mockk(relaxed = true)
        weatherExecutor = mockk(relaxed = true)
        actionLogger = mockk(relaxed = true)
        actionEventEmitter = mockk(relaxed = true)
        
        every { application.getString(R.string.action_weather_fetching) } returns "Wetterdaten werden abgerufen..."
        every { application.getString(any(), *anyVararg()) } answers {
            val resId = it.invocation.args[0] as Int
            if (resId == R.string.action_weather_format) {
                "Das aktuelle Wetter: Regen bei 15 Grad"
            } else {
                "Mocked String"
            }
        }
        
        handler = WeatherActionHandler(
            context = application,
            settingsRepository = settingsRepository,
            ttsProxyLazy = object : dagger.Lazy<ActionTtsProxy> {
                override fun get() = ttsProxy
            },
            weatherExecutor = weatherExecutor,
            scope = testScope,
            actionLogger = actionLogger,
            actionEventEmitter = actionEventEmitter
        )
    }

    @Test
    fun `canHandle returns true for WeatherButtonAction`() {
        assert(handler.canHandle(WeatherButtonAction()))
    }

    @Test
    fun `handle fetches weather and calls tts with formatted output`() = runTest(testDispatcher) {
        val action = WeatherButtonAction()
        val config = ButtonConfig(id = "b1", label = "Weather", buttonAction = action, auditoryCue = null)
        
        coEvery { weatherExecutor.getWeatherInfo() } returns WeatherExecutor.WeatherResult.Success("Regen", 15.0)
        val onDoneSlot = slot<() -> Unit>()
        every { ttsProxy.speakRouted(any(), any(), any(), any(), capture(onDoneSlot)) } answers {
            onDoneSlot.captured.invoke()
        }
        every { ttsProxy.isReady } returns true
        
        val onFinish = mockk<(Int) -> Unit>(relaxed = true)
        
        handler.handle(config, action, 1, onFinish)
        
        verify { actionLogger.log("Wetterdaten werden abgerufen...", action, config.label) }
        verify { ttsProxy.speakRouted("Das aktuelle Wetter: Regen bei 15 Grad", any(), any(), any(), any()) }
        verify { onFinish(1) }
    }

    @Test
    fun `handle fetches weather and calls tts with formatted output including location when available`() = runTest(testDispatcher) {
        val action = WeatherButtonAction()
        val config = ButtonConfig(id = "b1", label = "Weather", buttonAction = action, auditoryCue = null)
        
        coEvery { weatherExecutor.getWeatherInfo() } returns WeatherExecutor.WeatherResult.Success("Sonnig", 22.0, "München")
        
        every { application.getString(R.string.action_weather_format_with_location, "München", "Sonnig", "22.0") } returns "Das aktuelle Wetter in München: Sonnig bei 22.0 Grad"
        
        val onDoneSlot = slot<() -> Unit>()
        every { ttsProxy.speakRouted(any(), any(), any(), any(), capture(onDoneSlot)) } answers {
            onDoneSlot.captured.invoke()
        }
        every { ttsProxy.isReady } returns true
        
        val onFinish = mockk<(Int) -> Unit>(relaxed = true)
        
        handler.handle(config, action, 1, onFinish)
        
        verify { actionLogger.log("Wetterdaten werden abgerufen...", action, config.label) }
        verify { ttsProxy.speakRouted("Das aktuelle Wetter in München: Sonnig bei 22.0 Grad", any(), any(), any(), any()) }
        verify { onFinish(1) }
    }

    @Test
    fun `handle logs error when weather fetch fails`() = runTest(testDispatcher) {
        val action = WeatherButtonAction()
        val config = ButtonConfig(id = "b1", label = "Weather", buttonAction = action, auditoryCue = null)
        
        coEvery { weatherExecutor.getWeatherInfo() } returns WeatherExecutor.WeatherResult.Error("Timeout")
        every { application.getString(R.string.action_weather_error, "Timeout") } returns "Fehler: Timeout"
        
        val onFinish = mockk<(Int) -> Unit>(relaxed = true)
        
        handler.handle(config, action, 1, onFinish)
        
        verify { actionLogger.log("Fehler: Timeout", action, config.label) }
        verify { onFinish(1) }
    }
}

package com.andreas_kratzer.ghosttalk.core.actions

import android.content.Context
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.domain.executors.WeatherExecutor
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.WeatherButtonAction
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class WeatherActionHandlerTest {

    private lateinit var context: Context
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var ttsHelper: TextToSpeechHelper
    private lateinit var weatherExecutor: WeatherExecutor
    private lateinit var log: (String) -> Unit
    private lateinit var handler: WeatherActionHandler
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        ttsHelper = mockk(relaxed = true)
        weatherExecutor = mockk(relaxed = true)
        log = mockk(relaxed = true)
        
        every { context.getString(R.string.action_weather_fetching) } returns "Wetterdaten werden abgerufen..."
        every { context.getString(any(), *anyVararg()) } answers {
            val resId = it.invocation.args[0] as Int
            if (resId == R.string.action_weather_format) {
                "Das aktuelle Wetter: Regen bei 15 Grad"
            } else {
                "Mocked String"
            }
        }
        
        handler = WeatherActionHandler(context, settingsRepository, ttsHelper, weatherExecutor, testScope, log)
    }

    @Test
    fun `canHandle returns true for WeatherButtonAction`() {
        assert(handler.canHandle(WeatherButtonAction()))
    }

    @Test
    fun `handle fetches weather and calls tts with formatted output`() = runTest(testDispatcher) {
        val action = WeatherButtonAction()
        val config = ButtonConfig(id = "b1", label = "Weather", buttonAction = action, auditoryCue = null)
        
        coEvery { weatherExecutor.getWeatherInfo() } returns "Regen, 15.0 °C"
        val onDoneSlot = slot<() -> Unit>()
        every { ttsHelper.speakRouted(any(), any(), any(), any(), any(), capture(onDoneSlot)) } answers {
            onDoneSlot.captured.invoke()
        }
        every { ttsHelper.isReady } returns true
        
        val onFinish = mockk<(Int) -> Unit>(relaxed = true)
        
        handler.handle(config, action, 1, onFinish)
        
        verify { log("Wetterdaten werden abgerufen...") }
        verify { ttsHelper.speakRouted(match { it.contains("Das aktuelle Wetter: Regen bei 15 Grad") }, any(), any(), any(), any(), any()) }
        verify { onFinish(1) }
    }
}

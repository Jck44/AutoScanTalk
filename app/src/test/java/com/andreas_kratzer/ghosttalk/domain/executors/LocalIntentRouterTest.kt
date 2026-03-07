package com.andreas_kratzer.ghosttalk.domain.executors

import com.andreas_kratzer.ghosttalk.core.util.Logger
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

class LocalIntentRouterTest {

    private lateinit var systemTimeExecutor: SystemTimeExecutor
    private lateinit var androidClockExecutor: AndroidClockExecutor
    private lateinit var batteryExecutor: BatteryExecutor
    private lateinit var weatherExecutor: WeatherExecutor
    private lateinit var logger: Logger
    private lateinit var router: LocalIntentRouter

    @Before
    fun setup() {
        systemTimeExecutor = mockk(relaxed = true)
        androidClockExecutor = mockk(relaxed = true)
        batteryExecutor = mockk(relaxed = true)
        weatherExecutor = mockk(relaxed = true)
        logger = mockk(relaxed = true)
        
        router = spyk(LocalIntentRouter(systemTimeExecutor, androidClockExecutor, batteryExecutor, weatherExecutor, logger))
    }

    @Test
    fun `executeIntent calls speak with natural response for time`() = runBlocking {
        var spokenText = ""
        val onSpeak: (String) -> Unit = { spokenText = it }
        
        every { systemTimeExecutor.getRawTimestampContext() } returns "14:00"
        coEvery { router.generateRawResponse(any(), any()) } returns "Es ist jetzt zwei Uhr."
        
        router.executeIntent("time", onSpeak)
        
        assert(spokenText == "Es ist jetzt zwei Uhr.")
    }

    @Test
    fun `executeIntent falls back to system executor if AI response is empty`() = runBlocking {
        var spokenText = ""
        val onSpeak: (String) -> Unit = { spokenText = it }
        
        every { systemTimeExecutor.getCurrentTimeOutput() } returns "14:05 Uhr"
        coEvery { router.generateRawResponse(any(), any()) } returns ""
        
        router.executeIntent("time", onSpeak)
        
        assert(spokenText == "14:05 Uhr")
    }

    @Test
    fun `executeIntent provides battery context correctly`() = runBlocking {
        var spokenText = ""
        val onSpeak: (String) -> Unit = { spokenText = it }
        
        every { batteryExecutor.getBatteryStatus() } returns "85 Prozent"
        coEvery { router.generateRawResponse(any(), any()) } returns "Dein Akku ist bei 85 Prozent."
        
        router.executeIntent("battery", onSpeak)
        
        verify { batteryExecutor.getBatteryStatus() }
        assert(spokenText == "Dein Akku ist bei 85 Prozent.")
    }

    @Test
    fun `executeIntent provides weather context correctly`() = runBlocking {
        var spokenText = ""
        val onSpeak: (String) -> Unit = { spokenText = it }
        
        coEvery { weatherExecutor.getWeatherInfo() } returns "Sonnig, 20 Grad"
        coEvery { router.generateRawResponse(any(), any()) } returns "In Berlin ist es sonnig bei 20 Grad."
        
        router.executeIntent("weather", onSpeak)
        
        coVerify { weatherExecutor.getWeatherInfo() }
        assert(spokenText == "In Berlin ist es sonnig bei 20 Grad.")
    }

    @Test
    fun `executeIntent handles weather with timestamp correctly`() = runBlocking {
        var spokenText = ""
        val onSpeak: (String) -> Unit = { spokenText = it }
        
        coEvery { weatherExecutor.getWeatherInfo() } returns "Bewölkt, 15 Grad (Stand vom 07.03. um 12:00 Uhr)"
        coEvery { router.generateRawResponse(any(), any()) } returns "Laut Cache vom Mittag ist es bewölkt bei 15 Grad."
        
        router.executeIntent("weather", onSpeak)
        
        assert(spokenText == "Laut Cache vom Mittag ist es bewölkt bei 15 Grad.")
    }
}

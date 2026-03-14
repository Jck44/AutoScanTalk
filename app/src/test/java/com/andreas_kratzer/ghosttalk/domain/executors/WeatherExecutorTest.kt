package com.andreas_kratzer.ghosttalk.domain.executors

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.WeatherRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WeatherExecutorTest {

    private lateinit var context: Context
    private lateinit var repository: WeatherRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var locationExecutor: LocationExecutor
    private lateinit var logger: Logger
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var weatherExecutor: WeatherExecutor

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        repository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        locationExecutor = mockk(relaxed = true)
        logger = mockk(relaxed = true)
        connectivityManager = mockk(relaxed = true)

        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        
        weatherExecutor = WeatherExecutor(
            context,
            repository,
            settingsRepository,
            locationExecutor,
            logger
        )
    }

    @Test
    fun `getWeatherInfo returns cached weather when offline`() = runTest {
        // Arrange
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns false
        
        val timestamp = System.currentTimeMillis()
        val timeStr = SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(timestamp))
        
        every { repository.getLastWeather() } returns "Sonnig, 20 °C"
        every { repository.getLastTimestamp() } returns timestamp

        // Act
        val result = weatherExecutor.getWeatherInfo()

        // Assert
        assert(result is WeatherExecutor.WeatherResult.Success)
        val success = result as WeatherExecutor.WeatherResult.Success
        assertEquals("Sonnig (Stand $timeStr)", success.condition)
        assertEquals(20.0, success.temperature, 0.1)
    }

    @Test
    fun `getWeatherInfo returns cached weather when cache not expired`() = runTest {
        // Arrange
        val now = System.currentTimeMillis()
        val timestamp = now - (10 * 60 * 1000) // 10 mins ago (not expired)
        val timeStr = SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(timestamp))
        
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        
        every { settingsRepository.weatherCacheTimeout } returns 30
        every { repository.getLastTimestamp() } returns timestamp
        every { repository.getLastWeather() } returns "Bewölkt, 18 °C"

        // Act
        val result = weatherExecutor.getWeatherInfo()

        // Assert
        assert(result is WeatherExecutor.WeatherResult.Success)
        val success = result as WeatherExecutor.WeatherResult.Success
        assertEquals("Bewölkt (Stand $timeStr)", success.condition)
        assertEquals(18.0, success.temperature, 0.1)
        coVerify(exactly = 0) { locationExecutor.getCurrentLocation(any()) }
    }

    @Test
    fun `getWeatherInfo returns offline message when no cache and offline`() = runTest {
        // Arrange
        every { connectivityManager.activeNetwork } returns null
        every { repository.getLastWeather() } returns null

        // Act
        val result = weatherExecutor.getWeatherInfo()

        // Assert
        assert(result is WeatherExecutor.WeatherResult.Error)
        assertEquals("Keine Wetterdaten verfügbar (offline).", (result as WeatherExecutor.WeatherResult.Error).message)
    }

    // Helper to test private mapWeatherCode via reflection if needed, 
    // but better to just test it via a mocked fetch if we can.
    // Or we could make mapWeatherCode internal.
    
    @Test
    fun `mapWeatherCode returns correct strings`() {
        // Using reflection to test the private method 'mapWeatherCode'
        val method = WeatherExecutor::class.java.getDeclaredMethod("mapWeatherCode", Int::class.javaPrimitiveType)
        method.isAccessible = true
        
        assertEquals("Klarer Himmel", method.invoke(weatherExecutor, 0))
        assertEquals("Leicht bewölkt", method.invoke(weatherExecutor, 1))
        assertEquals("Leicht bewölkt", method.invoke(weatherExecutor, 2))
        assertEquals("Leicht bewölkt", method.invoke(weatherExecutor, 3))
        assertEquals("Nebel", method.invoke(weatherExecutor, 45))
        assertEquals("Gewitter", method.invoke(weatherExecutor, 95))
        assertEquals("Unbekannte Wetterlage", method.invoke(weatherExecutor, 999))
    }
}

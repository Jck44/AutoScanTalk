package com.andreas_kratzer.ghosttalk.domain.executors

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.andreas_kratzer.ghosttalk.core.util.Logger
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class WeatherExecutorTest {

    private lateinit var context: Context
    private lateinit var weatherUseCase: com.andreas_kratzer.ghosttalk.core.domain.WeatherUseCase
    private lateinit var locationExecutor: LocationExecutor
    private lateinit var logger: Logger
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var weatherExecutor: WeatherExecutor

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        weatherUseCase = mockk(relaxed = true)
        locationExecutor = mockk(relaxed = true)
        logger = mockk(relaxed = true)
        connectivityManager = mockk(relaxed = true)

        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        
        weatherExecutor = WeatherExecutor(
            context,
            weatherUseCase,
            locationExecutor,
            logger
        )
    }

    @Test
    fun `getWeatherInfo returns Success from useCase when online`() = runTest {
        // Arrange
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        
        val location = mockk<android.location.Location>()
        every { location.latitude } returns 52.52
        every { location.longitude } returns 13.40
        io.mockk.coEvery { locationExecutor.getCurrentLocation() } returns location

        io.mockk.coEvery { weatherUseCase.getWeatherInfo(52.52, 13.40) } returns 
            com.andreas_kratzer.ghosttalk.core.domain.WeatherUseCase.WeatherResult.Success("Sonnig", 20.0)

        // Act
        val result = weatherExecutor.getWeatherInfo()

        // Assert
        assert(result is WeatherExecutor.WeatherResult.Success)
        val success = result as WeatherExecutor.WeatherResult.Success
        assertEquals("Sonnig", success.condition)
        assertEquals(20.0, success.temperature, 0.1)
    }

    @Test
    fun `getWeatherInfo returns Error from useCase when online`() = runTest {
        // Arrange
        val network = mockk<Network>()
        val capabilities = mockk<NetworkCapabilities>()
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        
        val location = mockk<android.location.Location>()
        every { location.latitude } returns 52.52
        every { location.longitude } returns 13.40
        io.mockk.coEvery { locationExecutor.getCurrentLocation() } returns location

        io.mockk.coEvery { weatherUseCase.getWeatherInfo(52.52, 13.40) } returns 
            com.andreas_kratzer.ghosttalk.core.domain.WeatherUseCase.WeatherResult.Error("API failed")

        // Act
        val result = weatherExecutor.getWeatherInfo()

        // Assert
        assert(result is WeatherExecutor.WeatherResult.Error)
        assertEquals("API failed", (result as WeatherExecutor.WeatherResult.Error).message)
    }

    @Test
    fun `getWeatherInfo returns offline message when offline`() = runTest {
        // Arrange
        every { connectivityManager.activeNetwork } returns null

        // Act
        val result = weatherExecutor.getWeatherInfo()

        // Assert
        assert(result is WeatherExecutor.WeatherResult.Error)
        assertEquals("Keine Wetterdaten verfügbar (offline).", (result as WeatherExecutor.WeatherResult.Error).message)
    }
}

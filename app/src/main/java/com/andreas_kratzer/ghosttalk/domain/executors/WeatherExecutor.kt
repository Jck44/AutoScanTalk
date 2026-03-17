package com.andreas_kratzer.ghosttalk.domain.executors

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.andreas_kratzer.ghosttalk.core.domain.WeatherUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class WeatherExecutor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val weatherUseCase: WeatherUseCase,
    private val locationExecutor: LocationExecutor,
    private val logger: Logger
) {
    private val TAG = "WeatherExecutor"

    sealed class WeatherResult {
        data class Success(val condition: String, val temperature: Double) : WeatherResult()
        data class Error(val message: String) : WeatherResult()
    }

    suspend fun getWeatherInfo(): WeatherResult = withContext(Dispatchers.IO) {
        val online = isOnline()
        
        if (online) {
            val location = locationExecutor.getCurrentLocation()
            if (location != null) {
                val result = weatherUseCase.getWeatherInfo(location.latitude, location.longitude)
                return@withContext when (result) {
                    is WeatherUseCase.WeatherResult.Success -> WeatherResult.Success(result.condition, result.temperature)
                    is WeatherUseCase.WeatherResult.Error -> WeatherResult.Error(result.message)
                }
            }
        }

        return@withContext if (online) {
            WeatherResult.Error("Standort konnte nicht ermittelt werden.")
        } else {
            WeatherResult.Error("Keine Wetterdaten verfügbar (offline).")
        }
    }

    private fun isOnline(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

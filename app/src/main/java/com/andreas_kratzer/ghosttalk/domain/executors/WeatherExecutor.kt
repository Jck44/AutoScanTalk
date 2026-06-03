package com.andreas_kratzer.ghosttalk.domain.executors

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.domain.WeatherUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class WeatherExecutor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val weatherUseCase: WeatherUseCase,
    private val locationExecutor: LocationExecutor
) {

    sealed class WeatherResult {
        data class Success(
            val condition: String,
            val temperature: Double,
            val locationName: String? = null
        ) : WeatherResult()
        data class Error(val message: String) : WeatherResult()
    }

    suspend fun getWeatherInfo(): WeatherResult = withContext(Dispatchers.IO) {
        val online = isOnline()
        val locationName = locationExecutor.getPersistedLocationName()
        
        if (online) {
            val location = locationExecutor.getCurrentLocation()
            if (location != null) {
                val result = weatherUseCase.getWeatherInfo(location.latitude, location.longitude)
                when (result) {
                    is WeatherUseCase.WeatherResult.Success -> {
                        return@withContext WeatherResult.Success(result.condition, result.temperature, locationName)
                    }
                    is WeatherUseCase.WeatherResult.Error -> {
                        // Fallback to cache if request fails
                        val cached = weatherUseCase.getCachedWeather()
                        if (cached is WeatherUseCase.WeatherResult.Success) {
                            return@withContext WeatherResult.Success(cached.condition, cached.temperature, locationName)
                        } else {
                            return@withContext WeatherResult.Error(result.message)
                        }
                    }
                }
            } else {
                // Location is null but online. Try cache fallback
                val cached = weatherUseCase.getCachedWeather()
                if (cached is WeatherUseCase.WeatherResult.Success) {
                    return@withContext WeatherResult.Success(cached.condition, cached.temperature, locationName)
                } else {
                    val locError = context.getString(R.string.error_location_unavailable_weather)
                    return@withContext WeatherResult.Error(locError)
                }
            }
        } else {
            // Offline. Try cache fallback
            val cached = weatherUseCase.getCachedWeather()
            if (cached is WeatherUseCase.WeatherResult.Success) {
                return@withContext WeatherResult.Success(cached.condition, cached.temperature, locationName)
            } else {
                val netError = context.getString(R.string.error_no_internet_weather)
                return@withContext WeatherResult.Error(netError)
            }
        }
    }

    private fun isOnline(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

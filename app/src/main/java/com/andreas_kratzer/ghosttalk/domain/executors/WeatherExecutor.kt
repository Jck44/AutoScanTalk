package com.andreas_kratzer.ghosttalk.domain.executors

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.andreas_kratzer.ghosttalk.core.data.WeatherRepository
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.net.ssl.HttpsURLConnection

class WeatherExecutor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: WeatherRepository,
    private val settingsRepository: com.andreas_kratzer.ghosttalk.core.data.SettingsRepository,
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
        val cacheTimeoutMinutes = settingsRepository.weatherCacheTimeout
        val lastTimestamp = repository.getLastTimestamp()
        val isCacheExpired = System.currentTimeMillis() - lastTimestamp > (cacheTimeoutMinutes * 60 * 1000)
        
        logger.d(TAG, "getWeatherInfo: isOnline=$online, isCacheExpired=$isCacheExpired")
        
        if (online && isCacheExpired) {
            val location = locationExecutor.getCurrentLocation()
            if (location != null) {
                // Prioritize Open-Meteo as requested
                val openMeteoData = fetchOpenMeteoWeather(location.latitude, location.longitude)
                
                if (openMeteoData != null) {
                    logger.d(TAG, "getWeatherInfo: Open-Meteo success")
                    val result = WeatherResult.Success(openMeteoData.first, openMeteoData.second)
                    repository.saveWeather("${result.condition}, ${result.temperature} °C", System.currentTimeMillis())
                    return@withContext result
                }

                logger.w(TAG, "getWeatherInfo: Open-Meteo failed, trying wttr.in backup")
                val query = "${location.latitude},${location.longitude}"
                val liveWeather = fetchLiveWeather(query)

                if (liveWeather != null) {
                    logger.d(TAG, "getWeatherInfo: wttr.in success")
                    repository.saveWeather(liveWeather, System.currentTimeMillis())
                    return@withContext parseWttrIn(liveWeather)
                }
            }
        }

        // Return cache if it exists
        val cached = repository.getLastWeather()
        val timestamp = repository.getLastTimestamp()
        
        if (cached != null) {
            // Parse cached string back to structured data if possible, or return as special success
            // For now, let's keep it simple and just parse the cached string if we can
            return@withContext parseCachedWeather(cached, timestamp)
        } else {
            return@withContext if (online) WeatherResult.Error("Wetter-Dienst aktuell nicht erreichbar.") else WeatherResult.Error("Keine Wetterdaten verfügbar (offline).")
        }
    }

    private fun parseWttrIn(weather: String): WeatherResult {
        // wttr.in format=3 is usually "Condition: +Temp°C" or similar
        // Let's try to extract temperature
        return try {
            val parts = weather.split(",")
            if (parts.size >= 2) {
                val cond = parts[0].trim()
                val tempStr = parts[1].replace("°C", "").trim()
                WeatherResult.Success(cond, tempStr.toDouble())
            } else {
                WeatherResult.Success(weather, 0.0)
            }
        } catch (e: Exception) {
            WeatherResult.Success(weather, 0.0)
        }
    }

    private fun parseCachedWeather(cached: String, timestamp: Long): WeatherResult {
        val dateStr = SimpleDateFormat("dd.MM.", Locale.GERMANY).format(Date(timestamp))
        val timeStr = SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(timestamp))
        val displayStr = "$cached (Stand vom $dateStr um $timeStr Uhr)"
        
        // Try to extract condition and temp for formatting, otherwise return as error/fallback
        return try {
            val parts = cached.split(",")
            if (parts.size >= 2) {
                val cond = parts[0].trim()
                val tempStr = parts[1].replace("°C", "").trim()
                WeatherResult.Success("$cond (Stand $timeStr)", tempStr.toDouble())
            } else {
                WeatherResult.Success(displayStr, 0.0)
            }
        } catch (e: Exception) {
            WeatherResult.Success(displayStr, 0.0)
        }
    }

    private fun isOnline(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private suspend fun fetchLiveWeather(query: String): String? {
        return try {
            // For coordinates (contains comma), wttr.in prefers the literal comma.
            // For names (e.g. "New York"), we need encoding.
            val finalQuery = if (query.contains(",")) query else withContext(
                Dispatchers.IO
            ) {
                URLEncoder.encode(query, "UTF-8")
            }
            val url = URL("https://wttr.in/$finalQuery?format=3")
            val connection = withContext(Dispatchers.IO) {
                url.openConnection()
            } as HttpsURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000 // Increased to 15s
            connection.readTimeout = 15000 // Increased to 15s
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) curl/7.64.1") 
            
            if (connection.responseCode == 200) {
                connection.inputStream.bufferedReader().use { it.readText().trim() }
            } else {
                logger.w(TAG, "fetchLiveWeather: HTTP ${connection.responseCode} for $finalQuery")
                null
            }
        } catch (e: Exception) {
            logger.e(TAG, "Weather fetch failed", e)
            null
        }
    }

    private suspend fun fetchOpenMeteoWeather(lat: Double, lon: Double): Pair<String, Double>? {
        return try {
            val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true")
            val connection = withContext(Dispatchers.IO) {
                url.openConnection()
            } as HttpsURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JsonParser.parseString(response).asJsonObject
                val currentWeather = json.getAsJsonObject("current_weather")
                val temp = currentWeather.get("temperature").asDouble
                val code = currentWeather.get("weathercode").asInt
                
                val condition = mapWeatherCode(code)
                Pair(condition, temp)
            } else {
                null
            }
        } catch (e: Exception) {
            logger.e(TAG, "Open-Meteo fetch failed", e)
            null
        }
    }

    private fun mapWeatherCode(code: Int): String {
        return when (code) {
            0 -> "Klarer Himmel"
            1, 2, 3 -> "Leicht bewölkt"
            45, 48 -> "Nebel"
            51, 53, 55 -> "Nieselregen"
            61, 63, 65 -> "Regen"
            71, 73, 75 -> "Schneefall"
            80, 81, 82 -> "Regenschauer"
            95, 96, 99 -> "Gewitter"
            else -> "Unbekannte Wetterlage"
        }
    }
}

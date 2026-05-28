package com.andreas_kratzer.ghosttalk.core.domain

import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.data.WeatherRepository
import com.andreas_kratzer.ghosttalk.core.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection

@Singleton
class WeatherUseCase @Inject constructor(
    private val repository: WeatherRepository,
    private val settingsRepository: SettingsRepository,
    private val logger: Logger
) {
    private val TAG = "WeatherUseCase"

    sealed class WeatherResult {
        data class Success(val condition: String, val temperature: Double, val isCached: Boolean = false) : WeatherResult()
        data class Error(val message: String) : WeatherResult()
    }

    suspend fun getWeatherInfo(lat: Double, lon: Double): WeatherResult = withContext(Dispatchers.IO) {
        val cacheTimeoutMinutes = settingsRepository.weatherCacheTimeout
        val lastTimestamp = repository.getLastTimestamp()
        val isCacheExpired = System.currentTimeMillis() - lastTimestamp > (cacheTimeoutMinutes * 60 * 1000)
        
        logger.d(TAG, "getWeatherInfo: lat=$lat, lon=$lon, isCacheExpired=$isCacheExpired")
        
        if (isCacheExpired) {
            // Prioritize Open-Meteo
            val openMeteoData = fetchOpenMeteoWeather(lat, lon)
            
            if (openMeteoData != null) {
                logger.d(TAG, "getWeatherInfo: Open-Meteo success")
                val result = WeatherResult.Success(openMeteoData.first, openMeteoData.second)
                repository.saveWeather("${result.condition}, ${result.temperature} °C", System.currentTimeMillis())
                return@withContext result
            }

            logger.w(TAG, "getWeatherInfo: Open-Meteo failed, trying wttr.in backup")
            val query = "$lat,$lon"
            val liveWeather = fetchLiveWeather(query)

            if (liveWeather != null) {
                logger.d(TAG, "getWeatherInfo: wttr.in success")
                repository.saveWeather(liveWeather, System.currentTimeMillis())
                return@withContext parseWttrIn(liveWeather)
            }
        }

        // Return cache if it exists
        val cached = repository.getLastWeather()
        val timestamp = repository.getLastTimestamp()
        
        if (cached != null) {
            return@withContext parseCachedWeather(cached, timestamp)
        } else {
            return@withContext WeatherResult.Error("Wetter-Dienst aktuell nicht erreichbar.")
        }
    }

    private fun parseWttrIn(weather: String): WeatherResult {
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
        val timeStr = SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(timestamp))
        return try {
            val parts = cached.split(",")
            if (parts.size >= 2) {
                val cond = parts[0].trim()
                val tempStr = parts[1].replace("°C", "").trim()
                WeatherResult.Success("$cond (Stand $timeStr)", tempStr.toDouble(), isCached = true)
            } else {
                WeatherResult.Success(cached, 0.0, isCached = true)
            }
        } catch (e: Exception) {
            WeatherResult.Success(cached, 0.0, isCached = true)
        }
    }

    private suspend fun fetchLiveWeather(query: String): String? {
        return try {
            val finalQuery = if (query.contains(",")) query else withContext(Dispatchers.IO) {
                URLEncoder.encode(query, "UTF-8")
            }
            val url = URL("https://wttr.in/$finalQuery?format=3")
            val connection = withContext(Dispatchers.IO) {
                url.openConnection()
            } as HttpsURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) curl/7.64.1") 
            
            if (connection.responseCode == 200) {
                connection.inputStream.bufferedReader().use { it.readText().trim() }
            } else {
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
                val json = org.json.JSONObject(response)
                val currentWeather = json.getJSONObject("current_weather")
                val temp = currentWeather.getDouble("temperature")
                val code = currentWeather.getInt("weathercode")
                
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

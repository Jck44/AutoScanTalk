package com.andreas_kratzer.ghosttalk.domain.executors

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.andreas_kratzer.ghosttalk.core.util.Logger
import com.andreas_kratzer.ghosttalk.data.WeatherRepository
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
    private val settingsRepository: com.andreas_kratzer.ghosttalk.data.SettingsRepository,
    private val locationExecutor: LocationExecutor,
    private val logger: Logger
) {
    private val TAG = "WeatherExecutor"

    suspend fun getWeatherInfo(): String = withContext(Dispatchers.IO) {
        val online = isOnline()
        val cacheTimeoutMinutes = settingsRepository.weatherCacheTimeout
        val lastTimestamp = repository.getLastTimestamp()
        val isCacheExpired = System.currentTimeMillis() - lastTimestamp > (cacheTimeoutMinutes * 60 * 1000)
        
        logger.d(TAG, "getWeatherInfo: isOnline=$online, isCacheExpired=$isCacheExpired")
        
        if (online && isCacheExpired) {
            val location = locationExecutor.getCurrentLocation()
            if (location != null) {
                // Prioritize Open-Meteo as requested
                var freshWeather = fetchOpenMeteoWeather(location.latitude, location.longitude)
                
                if (freshWeather == null) {
                    logger.w(TAG, "getWeatherInfo: Open-Meteo failed, trying wttr.in backup")
                    val query = "${location.latitude},${location.longitude}"
                    freshWeather = fetchLiveWeather(query)
                }

                if (freshWeather != null) {
                    logger.d(TAG, "getWeatherInfo: Live fetch success")
                    repository.saveWeather(freshWeather, System.currentTimeMillis())
                    return@withContext freshWeather
                } else {
                    logger.w(TAG, "getWeatherInfo: All live fetches failed, trying cache")
                }
            }
        }

        // Return cache if it exists (even if expired if we couldn't fetch live)
        val cached = repository.getLastWeather()
        val timestamp = repository.getLastTimestamp()
        logger.d(TAG, "getWeatherInfo: cache null=${cached == null}, timestamp=$timestamp")
        
        if (cached != null) {
            val dateStr = SimpleDateFormat("dd.MM.", Locale.GERMANY).format(Date(timestamp))
            val timeStr = SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(timestamp))
            return@withContext "$cached (Stand vom $dateStr um $timeStr Uhr)"
        } else {
            return@withContext if (online) "Wetter-Dienst aktuell nicht erreichbar." else "Keine Wetterdaten verfügbar (offline)."
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

    private suspend fun fetchOpenMeteoWeather(lat: Double, lon: Double): String? {
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
                "$condition, $temp °C"
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

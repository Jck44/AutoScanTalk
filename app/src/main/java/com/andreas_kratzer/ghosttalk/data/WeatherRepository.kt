package com.andreas_kratzer.ghosttalk.data

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeatherRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("weather_cache", Context.MODE_PRIVATE)

    fun saveWeather(weather: String, timestamp: Long) {
        prefs.edit()
            .putString("last_weather", weather)
            .putLong("last_timestamp", timestamp)
            .apply()
    }

    fun getLastWeather(): String? {
        return prefs.getString("last_weather", null)
    }

    fun getLastTimestamp(): Long {
        return prefs.getLong("last_timestamp", 0)
    }
}

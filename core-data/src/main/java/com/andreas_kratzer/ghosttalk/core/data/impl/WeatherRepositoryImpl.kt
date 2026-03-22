package com.andreas_kratzer.ghosttalk.core.data.impl

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.andreas_kratzer.ghosttalk.core.data.WeatherRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeatherRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : WeatherRepository {
    private val prefs: SharedPreferences = context.getSharedPreferences("weather_cache", Context.MODE_PRIVATE)

    override fun saveWeather(weather: String, timestamp: Long) {
        prefs.edit {
            putString("last_weather", weather)
                .putLong("last_timestamp", timestamp)
        }
    }

    override fun getLastWeather(): String? {
        return prefs.getString("last_weather", null)
    }

    override fun getLastTimestamp(): Long {
        return prefs.getLong("last_timestamp", 0)
    }
}

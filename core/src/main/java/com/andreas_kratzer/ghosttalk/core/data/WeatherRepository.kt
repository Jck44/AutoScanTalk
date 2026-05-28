package com.andreas_kratzer.ghosttalk.core.data

interface WeatherRepository {
    fun saveWeather(weather: String, timestamp: Long)
    fun getLastWeather(): String?
    fun getLastTimestamp(): Long
}

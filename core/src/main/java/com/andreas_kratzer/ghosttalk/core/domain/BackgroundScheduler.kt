package com.andreas_kratzer.ghosttalk.core.domain

interface BackgroundScheduler {
    fun scheduleLocationUpdate()
    fun scheduleWeatherUpdate()
}

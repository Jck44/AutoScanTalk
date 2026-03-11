package com.andreas_kratzer.ghosttalk.domain.executors

import android.content.Context
import android.os.BatteryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class BatteryExecutor @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun getBatteryLevel(): Int {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    fun getBatteryStatus(): String {
        val level = getBatteryLevel()
        return "Der aktuelle Akkustand beträgt $level Prozent."
    }
}

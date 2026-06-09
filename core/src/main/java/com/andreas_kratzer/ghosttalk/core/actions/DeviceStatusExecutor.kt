package com.andreas_kratzer.ghosttalk.core.actions

import android.content.Context
import com.andreas_kratzer.ghosttalk.core.audio.SsmlFactory
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceStatusExecutor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val actionLogger: ActionLogger,
    private val settings: ControlDeviceSettings,
    private val ttsProxyLazy: dagger.Lazy<ControlDeviceTtsProxy>
) {
    private val getString: (Int, Array<out Any?>) -> String = { id, args ->
        when (id) {
            -1001 -> "Batteriestand ist bei ${args.getOrNull(0) ?: ""} Prozent"
            -1002 -> "Batteriestand ist bei ${args.getOrNull(0) ?: ""} Prozent"
            else -> try { context.getString(id, *args) } catch(_: Exception) { "" }
        }
    }

    fun handleReadBattery(config: ButtonConfig, action: ControlDeviceButtonAction, executionId: Int, onFinish: (Int) -> Unit) {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        val percentage = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        
        val ssml = getString(-1001, arrayOf(percentage))
        val plain = getString(-1002, arrayOf(percentage))
        
        speakRoutedWithLogging(ssml, plain, config, action, executionId, onFinish)
    }

    fun handleReadTime(config: ButtonConfig, action: ControlDeviceButtonAction, executionId: Int, onFinish: (Int) -> Unit) {
        val calendar = java.util.Calendar.getInstance()
        if (action.offsetValue != 0) {
            calendar.add(java.util.Calendar.MINUTE, action.offsetValue)
        }
        
        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        val timeString = sdf.format(calendar.time)
        
        val prefix = action.prefixText?.takeIf { it.isNotBlank() }?.let { if (it.endsWith(" ")) it else "$it " } ?: ""
        val suffix = action.suffixText?.takeIf { it.isNotBlank() }?.let { if (it.startsWith(" ")) it else " $it" } ?: ""
        
        val plain = "$prefix$timeString$suffix"
        val ssml = SsmlFactory.wrap(plain)
        
        speakRoutedWithLogging(ssml, plain, config, action, executionId, onFinish)
    }

    fun handleReadDate(config: ButtonConfig, action: ControlDeviceButtonAction, executionId: Int, onFinish: (Int) -> Unit) {
        val calendar = java.util.Calendar.getInstance()
        if (action.offsetValue != 0) {
            calendar.add(java.util.Calendar.DAY_OF_YEAR, action.offsetValue)
        }
        
        val mainPattern = "dd. MMMM yyyy"
        val fullPattern = if (action.includeWeekday) "EEEE, dd. MMMM yyyy" else mainPattern
        
        val sdfDisplay = java.text.SimpleDateFormat(fullPattern, java.util.Locale.getDefault())
        val dateString = sdfDisplay.format(calendar.time)
        
        val prefix = action.prefixText?.takeIf { it.isNotBlank() }?.let { if (it.endsWith(" ")) it else "$it " } ?: ""
        val suffix = action.suffixText?.takeIf { it.isNotBlank() }?.let { if (it.startsWith(" ")) it else " $it" } ?: ""
        
        val plain = "$prefix$dateString$suffix"
        val ssml = SsmlFactory.wrap(plain)
        
        speakRoutedWithLogging(ssml, plain, config, action, executionId, onFinish)
    }

    private fun speakRoutedWithLogging(
        ssml: String,
        plainText: String,
        config: ButtonConfig,
        action: ControlDeviceButtonAction,
        executionId: Int,
        onFinish: (Int) -> Unit
    ) {
        actionLogger.log(plainText, action, config.label)
        val targetDeviceAddress = if (config.playActionAsAuditoryCue) {
            settings.cuesAudioDeviceAddress
        } else {
            settings.ttsAudioDeviceAddress
        }
        
        val tts = ttsProxyLazy.get()
        tts.speakRouted(ssml, targetDeviceAddress) {
            onFinish(executionId)
        }
    }
}

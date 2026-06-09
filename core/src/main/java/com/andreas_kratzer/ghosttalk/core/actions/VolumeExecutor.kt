package com.andreas_kratzer.ghosttalk.core.actions

import android.content.Context
import android.media.AudioManager
import com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VolumeExecutor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val actionLogger: ActionLogger,
    private val settings: ControlDeviceSettings
) {
    fun handleVolume(streamType: Int, action: ControlDeviceButtonAction, label: String?, executionId: Int, onFinish: (Int) -> Unit) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val valueStr = action.volumeValue ?: "50"
        
        val maxVolume = audioManager.getStreamMaxVolume(streamType)
        val currentVolume = audioManager.getStreamVolume(streamType)
        
        val isRelative = valueStr.startsWith("+") || valueStr.startsWith("-")
        val percentage = valueStr.removePrefix("+").toIntOrNull() ?: 50
        
        val targetVolume = if (isRelative) {
            val change = (maxVolume * (percentage / 100.0)).toInt()
            (currentVolume + change).coerceIn(0, maxVolume)
        } else {
            (maxVolume * (percentage / 100.0)).toInt().coerceIn(0, maxVolume)
        }
        
        audioManager.setStreamVolume(streamType, targetVolume, AudioManager.FLAG_SHOW_UI)
        actionLogger.log("Lautstärke auf ${((targetVolume.toDouble() / maxVolume) * 100).toInt()}% gesetzt", action, label)
        onFinish(executionId)
    }

    fun handleInAppVolume(isTts: Boolean, action: ControlDeviceButtonAction, label: String?, executionId: Int, onFinish: (Int) -> Unit) {
        val valueStr = action.volumeValue ?: "50"
        
        val maxVolume = 100
        val currentVolume = if (isTts) settings.speakerVolume else settings.headphoneVolume
        
        val isRelative = valueStr.startsWith("+") || valueStr.startsWith("-")
        val percentage = valueStr.removePrefix("+").toIntOrNull() ?: 50
        
        val targetVolume = if (isRelative) {
            val change = percentage
            (currentVolume + change).coerceIn(0, maxVolume)
        } else {
            percentage.coerceIn(0, maxVolume)
        }
        
        if (isTts) {
            settings.speakerVolume = targetVolume
        } else {
            settings.headphoneVolume = targetVolume
        }
        
        val channelName = if (isTts) "Laut Sprechen" else "Audio-Hinweis"
        actionLogger.log("In-App Lautstärke ($channelName) auf $targetVolume% gesetzt", action, label)
        onFinish(executionId)
    }
}

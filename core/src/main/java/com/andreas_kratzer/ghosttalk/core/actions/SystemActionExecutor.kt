package com.andreas_kratzer.ghosttalk.core.actions

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemActionExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val actionLogger: ActionLogger,
    private val settings: ControlDeviceSettings,
    private val scanControllerLazy: dagger.Lazy<ScannerController>,
    private val callActionProxy: dagger.Lazy<CallActionProxy>,
    private val syncActionProxy: dagger.Lazy<SyncActionProxy>
) {
    fun handleMediaKey(keyCode: Int, description: String, action: ButtonAction, label: String?, executionId: Int, onFinish: (Int) -> Unit) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        actionLogger.log(description, action, label)
        onFinish(executionId)
    }

    fun handleStatus(ringerMode: Int, action: ButtonAction, label: String?, executionId: Int, onFinish: (Int) -> Unit) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (ringerMode == AudioManager.RINGER_MODE_SILENT && !notificationManager.isNotificationPolicyAccessGranted) {
            actionLogger.log("Berechtigung für 'Nicht stören' fehlt.", action, label)
        } else {
            audioManager.ringerMode = ringerMode
            val modeName = when(ringerMode) {
                AudioManager.RINGER_MODE_SILENT -> "Lautlos"
                AudioManager.RINGER_MODE_VIBRATE -> "Vibration"
                else -> "Laut"
            }
            actionLogger.log("Modus auf $modeName gesetzt", action, label)
        }
        onFinish(executionId)
    }

    fun handleToggleScanning(action: ButtonAction, label: String?, executionId: Int, onFinish: (Int) -> Unit) {
        val scannerController = scanControllerLazy.get()
        scannerController.togglePause()
        
        val isPaused = scannerController.isPausedManually.value
        val msg = if (isPaused) "Scannen pausiert" else "Scannen fortgesetzt"
        actionLogger.log(msg, action, label)
        
        onFinish(executionId)
    }

    fun handleStartCall(config: ButtonConfig, action: ControlDeviceButtonAction, executionId: Int, onFinish: (Int) -> Unit) {
        val phone = action.contactPhone ?: ""
        val name = action.contactName ?: ""
        if (settings.simulateCallsEnabled) {
            callActionProxy.get().simulateOutgoingCall(name, phone)
            actionLogger.log("Anruf simulieren an $name ($phone)", action, config.label)
        } else {
            callActionProxy.get().startCall(name, phone)
            actionLogger.log("Anruf starten an $name ($phone)", action, config.label)
        }
        onFinish(executionId)
    }

    fun handleStartSync(config: ButtonConfig, action: ControlDeviceButtonAction, executionId: Int, onFinish: (Int) -> Unit) {
        actionLogger.log("Synchronisation starten", action, config.label)
        syncActionProxy.get().triggerSync()
        onFinish(executionId)
    }
}

package com.andreas_kratzer.ghosttalk.core.actions

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import com.andreas_kratzer.ghosttalk.core.model.ButtonAction
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction
import com.andreas_kratzer.ghosttalk.core.model.DeviceActionType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface ControlDeviceTtsProxy {
    val isReady: Boolean
    var isReadingNotification: Boolean
    fun speakRouted(text: String, deviceAddress: String?, onDone: (() -> Unit)? = null)
}

class ControlDeviceActionHandler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val actionLogger: ActionLogger,
    private val smsExecutor: SmsExecutor,
    private val notificationExecutor: NotificationExecutor,
    private val deviceStatusExecutor: DeviceStatusExecutor,
    private val calendarExecutor: CalendarExecutor,
    private val volumeExecutor: VolumeExecutor,
    private val systemActionExecutor: SystemActionExecutor
) : ActionHandler {
    
    override fun canHandle(action: ButtonAction): Boolean = action is ControlDeviceButtonAction

    override fun handle(
        buttonConfig: ButtonConfig,
        action: ButtonAction,
        executionId: Int,
        onFinish: (Int) -> Unit
    ) {
        val deviceAction = action as ControlDeviceButtonAction
        when (deviceAction.actionType) {
            DeviceActionType.MEDIA_NEXT -> systemActionExecutor.handleMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT, "Nächstes Lied", action, buttonConfig.label, executionId, onFinish)
            DeviceActionType.MEDIA_PREVIOUS -> systemActionExecutor.handleMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Vorheriges Lied", action, buttonConfig.label, executionId, onFinish)
            DeviceActionType.MEDIA_PLAY_PAUSE -> systemActionExecutor.handleMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "Start / Stop", action, buttonConfig.label, executionId, onFinish)
            DeviceActionType.READ_NOTIFICATIONS -> notificationExecutor.handleReadNotifications(buttonConfig, deviceAction, executionId, onFinish)
            
            DeviceActionType.VOLUME_NOTIFICATION -> volumeExecutor.handleVolume(AudioManager.STREAM_NOTIFICATION, deviceAction, buttonConfig.label, executionId, onFinish)
            DeviceActionType.VOLUME_ALARM -> volumeExecutor.handleVolume(AudioManager.STREAM_ALARM, deviceAction, buttonConfig.label, executionId, onFinish)
            DeviceActionType.VOLUME_MEDIA -> volumeExecutor.handleVolume(AudioManager.STREAM_MUSIC, deviceAction, buttonConfig.label, executionId, onFinish)
            DeviceActionType.VOLUME_CALL -> volumeExecutor.handleVolume(AudioManager.STREAM_VOICE_CALL, deviceAction, buttonConfig.label, executionId, onFinish)
            DeviceActionType.VOLUME_IN_APP_TTS -> volumeExecutor.handleInAppVolume(true, deviceAction, buttonConfig.label, executionId, onFinish)
            DeviceActionType.VOLUME_IN_APP_CUES -> volumeExecutor.handleInAppVolume(false, deviceAction, buttonConfig.label, executionId, onFinish)
            
            DeviceActionType.STATUS_SILENT -> systemActionExecutor.handleStatus(AudioManager.RINGER_MODE_SILENT, action, buttonConfig.label, executionId, onFinish)
            DeviceActionType.STATUS_VIBRATE -> systemActionExecutor.handleStatus(AudioManager.RINGER_MODE_VIBRATE, action, buttonConfig.label, executionId, onFinish)
            DeviceActionType.STATUS_LOUD -> systemActionExecutor.handleStatus(AudioManager.RINGER_MODE_NORMAL, action, buttonConfig.label, executionId, onFinish)
            
            DeviceActionType.SEND_MESSAGE -> smsExecutor.handleSendMessage(deviceAction, buttonConfig.label, executionId, onFinish)
            DeviceActionType.SEND_LAST_SPOKEN_SMS -> smsExecutor.handleSendLastSpokenSms(buttonConfig, deviceAction, executionId, onFinish)
            
            DeviceActionType.READ_BATTERY -> deviceStatusExecutor.handleReadBattery(buttonConfig, deviceAction, executionId, onFinish)
            DeviceActionType.READ_TIME -> deviceStatusExecutor.handleReadTime(buttonConfig, deviceAction, executionId, onFinish)
            DeviceActionType.READ_DATE -> deviceStatusExecutor.handleReadDate(buttonConfig, deviceAction, executionId, onFinish)
            DeviceActionType.READ_CALENDAR_ENTRIES -> calendarExecutor.handleReadCalendarEntries(buttonConfig, deviceAction, executionId, onFinish)

            DeviceActionType.TOGGLE_SCANNING -> {
                systemActionExecutor.handleToggleScanning(action, buttonConfig.label, executionId, onFinish)
            }

            DeviceActionType.CLEAR_NOTIFICATIONS -> {
                notificationExecutor.handleClearNotifications(deviceAction, buttonConfig.label, executionId, onFinish)
            }

            DeviceActionType.START_CALL -> {
                systemActionExecutor.handleStartCall(buttonConfig, deviceAction, executionId, onFinish)
            }

            DeviceActionType.INSTALL_UPDATE -> {
                actionLogger.log("Aktion 'App aktualisieren' ist nicht mehr verfügbar", action, buttonConfig.label)
                onFinish(executionId)
            }

            DeviceActionType.START_SYNC -> {
                systemActionExecutor.handleStartSync(buttonConfig, deviceAction, executionId, onFinish)
            }

            DeviceActionType.TOGGLE_AUTO_READ_NOTIFICATIONS -> {
                notificationExecutor.handleToggleAutoRead(buttonConfig, deviceAction, executionId, onFinish)
            }
        }
    }
}

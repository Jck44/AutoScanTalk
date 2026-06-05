package com.andreas_kratzer.ghosttalk.core.services

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.andreas_kratzer.ghosttalk.core.di.NotificationServiceEntryPoint
import dagger.hilt.android.EntryPointAccessors

class NotificationReaderService : NotificationListenerService() {
    companion object {
        var instance: NotificationReaderService? = null
            private set
    }

    var passiveReader: PassiveNotificationReader? = null
        private set

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("NotificationReaderService", "Listener connected")
        instance = this

        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                NotificationServiceEntryPoint::class.java
            )
            passiveReader = PassiveNotificationReader(
                settings = entryPoint.settings(),
                ttsProxy = entryPoint.ttsProxy(),
                scannerController = entryPoint.scannerController(),
                appStateRepository = entryPoint.appStateRepository(),
                notificationService = this,
                context = applicationContext
            )
        } catch (e: Exception) {
            Log.e("NotificationReaderService", "Failed to initialize entry point or passive reader", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)
        passiveReader?.onNewNotification(sbn)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d("NotificationReaderService", "Listener disconnected")
        passiveReader?.destroy()
        passiveReader = null
        instance = null
    }
}

package com.andreas_kratzer.ghosttalk.core.services

import android.service.notification.NotificationListenerService
import android.util.Log

class NotificationReaderService : NotificationListenerService() {
    companion object {
        var instance: NotificationReaderService? = null
            private set
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("NotificationReaderService", "Listener connected")
        instance = this
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d("NotificationReaderService", "Listener disconnected")
        instance = null
    }
}

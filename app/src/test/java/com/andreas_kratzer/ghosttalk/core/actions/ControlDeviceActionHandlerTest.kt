package com.andreas_kratzer.ghosttalk.core.actions

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import com.andreas_kratzer.ghosttalk.core.services.NotificationReaderService
import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.ControlDeviceButtonAction
import com.andreas_kratzer.ghosttalk.model.DeviceActionType
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test

class ControlDeviceActionHandlerTest {

    private lateinit var context: Context
    private lateinit var audioManager: AudioManager
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var ttsHelper: TextToSpeechHelper
    private lateinit var log: (String) -> Unit
    private lateinit var handler: ControlDeviceActionHandler

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        audioManager = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        ttsHelper = mockk(relaxed = true)
        log = mockk(relaxed = true)

        every { context.getSystemService(Context.AUDIO_SERVICE) } returns audioManager
        
        handler = ControlDeviceActionHandler(context, settingsRepository, ttsHelper, log)
        
        every { ttsHelper.isReadingNotification = any() } just Runs
        
        mockkObject(NotificationReaderService)
    }

    @After
    fun teardown() {
        unmockkObject(NotificationReaderService)
    }

    @Test
    fun `canHandle returns true for ControlDeviceButtonAction`() {
        assert(handler.canHandle(ControlDeviceButtonAction(DeviceActionType.MEDIA_NEXT)))
    }

    @Test
    fun `handle MEDIA_NEXT dispatches correct key events`() {
        val action = ControlDeviceButtonAction(DeviceActionType.MEDIA_NEXT)
        val config = ButtonConfig(id = "b1", label = "Next", buttonAction = action, auditoryCue = null)

        handler.handle(config, action, 1) {}

        verify { 
            audioManager.dispatchMediaKeyEvent(any())
        }
        verify { log("Nächstes Lied") }
    }

    @Test
    fun `handle READ_NOTIFICATIONS calls tts with notification content`() {
        val action = ControlDeviceButtonAction(DeviceActionType.READ_NOTIFICATIONS)
        val config = ButtonConfig(id = "b1", label = "Read", buttonAction = action, auditoryCue = null)
        
        val service = mockk<NotificationReaderService>(relaxed = true)
        every { NotificationReaderService.instance } returns service
        every { settingsRepository.isNotificationReadingEnabled } returns true
        every { ttsHelper.isReady } returns true
        
        val sbn = mockk<android.service.notification.StatusBarNotification>(relaxed = true)
        val notification = mockk<android.app.Notification>(relaxed = true)
        val extras = mockk<android.os.Bundle>(relaxed = true)
        every { extras.getString(android.app.Notification.EXTRA_TITLE) } returns "Test Sender"
        every { extras.getCharSequence(android.app.Notification.EXTRA_TEXT) } returns "Hello World"
        
        every { sbn.packageName } returns "com.whatsapp"
        every { sbn.notification } returns notification
        notification.extras = extras
        
        every { service.activeNotifications } returns arrayOf(sbn)
        every { settingsRepository.monitoredNotificationApps } returns setOf("com.whatsapp")

        val onFinish = mockk<(Int) -> Unit>(relaxed = true)
        val onCompleteSlot = slot<() -> Unit>()
        every { ttsHelper.speakRouted("Von Test Sender: Hello World", any(), any(), any(), any(), capture(onCompleteSlot)) } returns Unit

        handler.handle(config, action, 1, onFinish)
        
        // Simuliere TTS Ende
        if (onCompleteSlot.isCaptured) {
            onCompleteSlot.captured.invoke()
        }

        verify { ttsHelper.isReadingNotification = true }
        verify { ttsHelper.speakRouted("Von Test Sender: Hello World", any(), any(), any(), any(), any()) }
        verify { ttsHelper.isReadingNotification = false }
        verify { onFinish(1) }
    }
}

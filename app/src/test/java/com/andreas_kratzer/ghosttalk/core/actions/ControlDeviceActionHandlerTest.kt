package com.andreas_kratzer.ghosttalk.core.actions

import android.content.Context
import android.media.AudioManager
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.ControlDeviceButtonAction
import com.andreas_kratzer.ghosttalk.core.model.DeviceActionType
import com.andreas_kratzer.ghosttalk.core.services.NotificationReaderService
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

class ControlDeviceActionHandlerTest {

    private lateinit var context: Context
    private lateinit var audioManager: AudioManager
    private lateinit var settings: ControlDeviceSettings
    private lateinit var ttsProxy: ControlDeviceTtsProxy
    private lateinit var actionLogger: ActionLogger
    private lateinit var actionEventEmitter: ActionEventEmitter
    private lateinit var handler: ControlDeviceActionHandler

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        audioManager = mockk(relaxed = true)
        settings = mockk(relaxed = true)
        ttsProxy = mockk(relaxed = true)
        actionLogger = mockk(relaxed = true)
        actionEventEmitter = mockk(relaxed = true)

        every { context.getSystemService(Context.AUDIO_SERVICE) } returns audioManager
        
        handler = ControlDeviceActionHandler(
            context = context,
            settings = settings,
            ttsProxyLazy = object : dagger.Lazy<ControlDeviceTtsProxy> {
                override fun get() = ttsProxy
            },
            actionLogger = actionLogger,
            actionEventEmitter = actionEventEmitter
        )
        
        every { ttsProxy.isReadingNotification = any() } just Runs
        
        mockkObject(NotificationReaderService)
        
        // Mock localized strings for Device Control
        every { context.getString(R.string.action_battery_ssml, any()) } answers { "Battery SSML ${it.invocation.args[1]}" }
        every { context.getString(R.string.action_battery_plain, any()) } answers { "Battery Plain ${it.invocation.args[1]}" }
        every { context.getString(R.string.time_format_pattern) } returns "HH:mm"
        every { context.getString(R.string.action_time_ssml, any()) } answers { "Time SSML ${it.invocation.args[1]}" }
        every { context.getString(R.string.action_time_plain, any()) } answers { "Time Plain ${it.invocation.args[1]}" }
        every { context.getString(R.string.date_format_pattern) } returns "dd.MM"
        every { context.getString(R.string.action_date_ssml, any()) } answers { "Date SSML ${it.invocation.args[1]}" }
        every { context.getString(R.string.action_date_plain, any()) } answers { "Date Plain ${it.invocation.args[1]}" }
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
        verify { actionLogger.log("Nächstes Lied") }
    }

    @Test
    fun `handle READ_NOTIFICATIONS calls tts with notification content`() {
        val action = ControlDeviceButtonAction(DeviceActionType.READ_NOTIFICATIONS)
        val config = ButtonConfig(id = "b1", label = "Read", buttonAction = action, auditoryCue = null)
        
        val service = mockk<NotificationReaderService>(relaxed = true)
        every { NotificationReaderService.instance } returns service
        every { settings.isNotificationReadingEnabled } returns true
        every { ttsProxy.isReady } returns true
        
        val sbn = mockk<android.service.notification.StatusBarNotification>(relaxed = true)
        val notification = mockk<android.app.Notification>(relaxed = true)
        val extras = mockk<android.os.Bundle>(relaxed = true)
        every { extras.getString(android.app.Notification.EXTRA_TITLE) } returns "Test Sender"
        every { extras.getCharSequence(android.app.Notification.EXTRA_TEXT) } returns "Hello World"
        
        every { sbn.packageName } returns "com.whatsapp"
        every { sbn.notification } returns notification
        notification.extras = extras
        
        every { service.activeNotifications } returns arrayOf(sbn)
        every { settings.monitoredNotificationApps } returns setOf("com.whatsapp")

        val onFinish = mockk<(Int) -> Unit>(relaxed = true)
        val onCompleteSlot = slot<() -> Unit>()
        every { ttsProxy.speakRouted("Von Test Sender: Hello World", any(), capture(onCompleteSlot)) } returns Unit

        handler.handle(config, action, 1, onFinish)
        
        // Simuliere TTS Ende
        if (onCompleteSlot.isCaptured) {
            onCompleteSlot.captured.invoke()
        }

        verify { ttsProxy.isReadingNotification = false }
        verify { onFinish(1) }
    }

    @Test
    fun `handle READ_BATTERY calls tts with battery level`() {
        val action = ControlDeviceButtonAction(DeviceActionType.READ_BATTERY)
        val config = ButtonConfig(id = "b1", label = "Battery", buttonAction = action, auditoryCue = null)
        val batteryManager = mockk<android.os.BatteryManager>(relaxed = true)
        
        every { context.getSystemService(Context.BATTERY_SERVICE) } returns batteryManager
        every { batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) } returns 85
        every { ttsProxy.isReady } returns true
        
        val onFinish = mockk<(Int) -> Unit>(relaxed = true)
        val onDoneSlot = slot<() -> Unit>()
        every { ttsProxy.speakRouted(any(), any(), capture(onDoneSlot)) } answers {
            onDoneSlot.captured.invoke()
        }
        
        handler.handle(config, action, 1, onFinish)
        
        verify { ttsProxy.speakRouted(any(), any(), any()) }
        verify { onFinish(1) }
    }

    @Test
    fun `handle READ_TIME calls tts with current time`() {
        val action = ControlDeviceButtonAction(DeviceActionType.READ_TIME)
        val config = ButtonConfig(id = "b1", label = "Time", buttonAction = action, auditoryCue = null)
        
        every { ttsProxy.isReady } returns true
        
        val onFinish = mockk<(Int) -> Unit>(relaxed = true)
        val onDoneSlot = slot<() -> Unit>()
        every { ttsProxy.speakRouted(any(), any(), capture(onDoneSlot)) } answers {
            onDoneSlot.captured.invoke()
        }
        
        handler.handle(config, action, 1, onFinish)
        
        verify { ttsProxy.speakRouted(any(), any(), any()) }
        verify { onFinish(1) }
    }

    @Test
    fun `handle READ_DATE calls tts with current date`() {
        val action = ControlDeviceButtonAction(DeviceActionType.READ_DATE)
        val config = ButtonConfig(id = "b1", label = "Date", buttonAction = action, auditoryCue = null)
        
        every { ttsProxy.isReady } returns true
        
        val onFinish = mockk<(Int) -> Unit>(relaxed = true)
        val onDoneSlot = slot<() -> Unit>()
        every { ttsProxy.speakRouted(any(), any(), capture(onDoneSlot)) } answers {
            onDoneSlot.captured.invoke()
        }
        
        handler.handle(config, action, 1, onFinish)
        
        verify { ttsProxy.speakRouted(any(), any(), any()) }
        verify { onFinish(1) }
    }
}

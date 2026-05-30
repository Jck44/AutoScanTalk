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

    private lateinit var scannerController: ScannerController
    private lateinit var callActionProxy: CallActionProxy
    private lateinit var handler: ControlDeviceActionHandler

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        audioManager = mockk(relaxed = true)
        settings = mockk(relaxed = true)
        ttsProxy = mockk(relaxed = true)
        actionLogger = mockk(relaxed = true)

        scannerController = mockk(relaxed = true)
        callActionProxy = mockk(relaxed = true)

        every { context.getSystemService(Context.AUDIO_SERVICE) } returns audioManager
        
        handler = ControlDeviceActionHandler(
            context = context,
            settings = settings,
            ttsProxyLazy = object : dagger.Lazy<ControlDeviceTtsProxy> {
                override fun get() = ttsProxy
            },
            scanControllerLazy = object : dagger.Lazy<ScannerController> {
                override fun get() = scannerController
            },
            callActionProxy = object : dagger.Lazy<CallActionProxy> {
                override fun get() = callActionProxy
            },
            actionLogger = actionLogger
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
        verify { actionLogger.log("Nächstes Lied", action, "Next") }
    }

    @Test
    fun `handle READ_NOTIFICATIONS calls tts with notification content`() {
        val action = ControlDeviceButtonAction(DeviceActionType.READ_NOTIFICATIONS)
        val config = ButtonConfig(id = "b1", label = "Read", buttonAction = action, auditoryCue = null)
        
        val service = mockk<NotificationReaderService>(relaxed = true)
        every { NotificationReaderService.instance } returns service
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
        every { ttsProxy.speakRouted("Test Sender: Hello World", any(), capture(onCompleteSlot)) } returns Unit

        handler.handle(config, action, 1, onFinish)
        
        // Simuliere TTS Ende
        if (onCompleteSlot.isCaptured) {
            onCompleteSlot.captured.invoke()
        }

        verify { ttsProxy.isReadingNotification = false }
        verify { onFinish(1) }
    }

    @Test
    fun `handle READ_NOTIFICATIONS with ignoreEmojis true filters emojis`() {
        val action = ControlDeviceButtonAction(DeviceActionType.READ_NOTIFICATIONS, ignoreEmojis = true)
        val config = ButtonConfig(id = "b1", label = "Read", buttonAction = action, auditoryCue = null)
        
        val service = mockk<NotificationReaderService>(relaxed = true)
        every { NotificationReaderService.instance } returns service
        every { ttsProxy.isReady } returns true
        
        val sbn = mockk<android.service.notification.StatusBarNotification>(relaxed = true)
        val notification = mockk<android.app.Notification>(relaxed = true)
        val extras = mockk<android.os.Bundle>(relaxed = true)
        every { extras.getString(android.app.Notification.EXTRA_TITLE) } returns "Test Sender"
        every { extras.getCharSequence(android.app.Notification.EXTRA_TEXT) } returns "Hello World 😊! 🚀 This is a test. ❤"
        
        every { sbn.packageName } returns "com.whatsapp"
        every { sbn.notification } returns notification
        notification.extras = extras
        
        every { service.activeNotifications } returns arrayOf(sbn)
        every { settings.monitoredNotificationApps } returns setOf("com.whatsapp")

        val onFinish = mockk<(Int) -> Unit>(relaxed = true)
        val onCompleteSlot = slot<() -> Unit>()
        // Emojis should be removed
        every { ttsProxy.speakRouted("Test Sender: Hello World ! This is a test.", any(), capture(onCompleteSlot)) } returns Unit

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
    fun `handle READ_TIME with offset and prefix-suffix builds correct plain and SSML strings`() {
        val action = ControlDeviceButtonAction(
            actionType = DeviceActionType.READ_TIME,
            prefixText = "Es ist jetzt",
            suffixText = "Uhr heute",
            offsetValue = 5
        )
        val config = ButtonConfig(id = "b1", label = "Time", buttonAction = action, auditoryCue = null)
        
        every { ttsProxy.isReady } returns true
        val ssmlSlot = slot<String>()
        val plainSlot = slot<String>()
        val onDoneSlot = slot<() -> Unit>()
        
        every { ttsProxy.speakRouted(capture(ssmlSlot), any(), capture(onDoneSlot)) } answers {
            onDoneSlot.captured.invoke()
        }
        mockkObject(actionLogger)
        every { actionLogger.log(capture(plainSlot), any(), any()) } just Runs

        handler.handle(config, action, 1) {}
        
        val ssml = ssmlSlot.captured
        val plain = plainSlot.captured
        
        assert(plain.startsWith("Es ist jetzt "))
        assert(plain.endsWith(" Uhr heute"))
        
        // Verify SSML is simply wrapped plain text
        assert(ssml == "<speak>$plain</speak>")
    }

    @Test
    fun `handle READ_DATE with offset, weekday and prefix-suffix builds correct plain and SSML strings`() {
        val action = ControlDeviceButtonAction(
            actionType = DeviceActionType.READ_DATE,
            prefixText = "Heute ist der",
            suffixText = "bald ist Ostern",
            includeWeekday = true,
            offsetValue = 2
        )
        val config = ButtonConfig(id = "b1", label = "Date", buttonAction = action, auditoryCue = null)
        
        every { ttsProxy.isReady } returns true
        val ssmlSlot = slot<String>()
        val plainSlot = slot<String>()
        val onDoneSlot = slot<() -> Unit>()
        
        every { ttsProxy.speakRouted(capture(ssmlSlot), any(), capture(onDoneSlot)) } answers {
            onDoneSlot.captured.invoke()
        }
        every { actionLogger.log(capture(plainSlot), any(), any()) } just Runs

        handler.handle(config, action, 1) {}
        
        val ssml = ssmlSlot.captured
        val plain = plainSlot.captured
        
        assert(plain.startsWith("Heute ist der "))
        assert(plain.contains(","))
        
        // Verify SSML is simply wrapped plain text
        assert(ssml == "<speak>$plain</speak>")
    }

    @Test
    fun `smart space logic adds spaces when missing`() {
        val action = ControlDeviceButtonAction(
            actionType = DeviceActionType.READ_TIME,
            prefixText = "Zeit:", // No space at end
            suffixText = "jetzt", // No space at start
            offsetValue = 0
        )
        val config = ButtonConfig(id = "b1", label = "Time", buttonAction = action, auditoryCue = null)
        
        every { ttsProxy.isReady } returns true
        val ssmlSlot = slot<String>()
        every { ttsProxy.speakRouted(capture(ssmlSlot), any(), any()) } just Runs
        
        handler.handle(config, action, 1) {}
        
        val ssml = ssmlSlot.captured
        // Verify SSML includes prefix with space and suffix with space
        assert(ssml.startsWith("<speak>Zeit: "))
        assert(ssml.endsWith(" jetzt</speak>"))
    }

    @Test
    fun `smart space logic does not add extra spaces if already present`() {
        val action = ControlDeviceButtonAction(
            actionType = DeviceActionType.READ_TIME,
            prefixText = "Zeit: ", // Has space
            suffixText = " jetzt", // Has space
            offsetValue = 0
        )
        val config = ButtonConfig(id = "b1", label = "Time", buttonAction = action, auditoryCue = null)
        
        every { ttsProxy.isReady } returns true
        val ssmlSlot = slot<String>()
        every { ttsProxy.speakRouted(capture(ssmlSlot), any(), any()) } just Runs
        
        handler.handle(config, action, 1) {}
        
        val ssml = ssmlSlot.captured
        // Verify no double spaces
        assert(!ssml.contains("Zeit:  "))
        assert(!ssml.contains("  jetzt"))
    }

    @Test
    fun `handle READ_CALENDAR_ENTRIES reads next events and speaks them`() {
        val action = ControlDeviceButtonAction(
            actionType = DeviceActionType.READ_CALENDAR_ENTRIES,
            offsetValue = 2
        )
        val config = ButtonConfig(id = "b1", label = "Calendar", buttonAction = action, auditoryCue = null)
        
        val contentResolver = mockk<android.content.ContentResolver>(relaxed = true)
        every { context.contentResolver } returns contentResolver
        
        val cursor = mockk<android.database.Cursor>(relaxed = true)
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns cursor
        
        // Mock 2 events
        every { cursor.moveToNext() } returnsMany listOf(true, true, false)
        
        // Mock column indices
        every { cursor.getColumnIndex(android.provider.CalendarContract.Instances.TITLE) } returns 0
        every { cursor.getColumnIndex(android.provider.CalendarContract.Instances.BEGIN) } returns 1
        every { cursor.getColumnIndex(android.provider.CalendarContract.Instances.END) } returns 2
        every { cursor.getColumnIndex(android.provider.CalendarContract.Instances.ALL_DAY) } returns 3
        
        // Use a list to return different values for different calls if needed, 
        // but here we just need to return title and times correctly for each row.
        // Mocking getString(0) to return different values on subsequent calls
        var callCount = 0
        every { cursor.getString(0) } answers { 
            if (callCount == 0) "Meeting 1" else "Meeting 2" 
        }
        every { cursor.getLong(1) } answers { 
            if (callCount == 0) 1712836800000L else 1712844000000L 
        }
        every { cursor.getLong(2) } answers { 
            if (callCount == 0) 1712840400000L else 1712847600000L 
        }
        every { cursor.getInt(3) } answers { 
            val res = 0
            callCount++
            res
        }

        every { ttsProxy.isReady } returns true
        val ssmlSlot = slot<String>()
        every { ttsProxy.speakRouted(capture(ssmlSlot), any(), any()) } just Runs
        
        handler.handle(config, action, 1) {}
        
        val ssml = ssmlSlot.captured
        assert(ssml.contains("Meeting 1"))
        assert(ssml.contains("Meeting 2"))
    }

    @Test
    fun `handle START_CALL with simulateCallsEnabled true calls simulateOutgoingCall`() {
        val action = ControlDeviceButtonAction(
            actionType = DeviceActionType.START_CALL,
            contactName = "Test Name",
            contactPhone = "123456"
        )
        val config = ButtonConfig(id = "b1", label = "Call", buttonAction = action, auditoryCue = null)

        every { settings.simulateCallsEnabled } returns true

        handler.handle(config, action, 1) {}

        verify { callActionProxy.simulateOutgoingCall("Test Name", "123456") }
        verify { actionLogger.log("Anruf simulieren an Test Name (123456)", action, "Call") }
    }

    @Test
    fun `handle START_CALL with simulateCallsEnabled false calls startCall`() {
        val action = ControlDeviceButtonAction(
            actionType = DeviceActionType.START_CALL,
            contactName = "Test Name",
            contactPhone = "123456"
        )
        val config = ButtonConfig(id = "b1", label = "Call", buttonAction = action, auditoryCue = null)

        every { settings.simulateCallsEnabled } returns false

        handler.handle(config, action, 1) {}

        verify { callActionProxy.startCall("Test Name", "123456") }
        verify { actionLogger.log("Anruf starten an Test Name (123456)", action, "Call") }
    }
}

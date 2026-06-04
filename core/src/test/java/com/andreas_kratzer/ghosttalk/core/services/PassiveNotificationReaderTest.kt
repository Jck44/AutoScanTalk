package com.andreas_kratzer.ghosttalk.core.services

import android.app.Notification
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.PowerManager
import android.service.notification.StatusBarNotification
import com.andreas_kratzer.ghosttalk.core.actions.ControlDeviceSettings
import com.andreas_kratzer.ghosttalk.core.actions.ControlDeviceTtsProxy
import com.andreas_kratzer.ghosttalk.core.actions.ScannerController
import com.andreas_kratzer.ghosttalk.core.data.AppStateRepository
import com.andreas_kratzer.ghosttalk.core.settings.AutoReadMode
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PassiveNotificationReaderTest {

    private val settings: ControlDeviceSettings = mockk(relaxed = true)
    private val ttsProxy: ControlDeviceTtsProxy = mockk(relaxed = true)
    private val scannerController: ScannerController = mockk(relaxed = true)
    private val appStateRepository: AppStateRepository = mockk(relaxed = true)
    private val notificationService: NotificationReaderService = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    private val powerManager: PowerManager = mockk(relaxed = true)
    private val packageManager: PackageManager = mockk(relaxed = true)

    private val isUserModeActiveFlow = MutableStateFlow(true)
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var reader: PassiveNotificationReader

    @Before
    fun setUp() {
        mockkStatic(Character::class)
        every { Character.getType(any<Int>()) } returns Character.OTHER_SYMBOL.toInt()
        every { Character.charCount(any()) } returns 1

        every { context.getSystemService(Context.POWER_SERVICE) } returns powerManager
        every { context.packageManager } returns packageManager
        every { appStateRepository.isUserModeActive } returns isUserModeActiveFlow
        every { powerManager.isInteractive } returns true

        // Default settings
        every { settings.isNotificationReadingEnabled } returns true
        every { settings.autoReadMode } returns AutoReadMode.IMMEDIATE
        every { settings.monitoredNotificationApps } returns setOf("com.test.app")
        every { settings.autoReadOnlyInUserMode } returns true
        every { settings.autoReadInStandby } returns false

        val appInfo = mockk<ApplicationInfo>()
        every { packageManager.getApplicationInfo("com.test.app", 0) } returns appInfo
        every { packageManager.getApplicationLabel(appInfo) } returns "TestApp"

        reader = PassiveNotificationReader(
            settings = settings,
            ttsProxy = ttsProxy,
            scannerController = scannerController,
            appStateRepository = appStateRepository,
            notificationService = notificationService,
            context = context
        )
    }

    @After
    fun tearDown() {
        reader.destroy()
        unmockkAll()
    }

    private fun createMockNotification(
        packageName: String = "com.test.app",
        key: String = "notification_key_1",
        title: String = "Hello",
        text: String = "World",
        isGroupSummary: Boolean = false
    ): StatusBarNotification {
        val sbn = mockk<StatusBarNotification>()
        val mockNotification = mockk<Notification>()
        val extras = mockk<Bundle>()

        every { sbn.packageName } returns packageName
        every { sbn.key } returns key
        every { sbn.notification } returns mockNotification
        every { mockNotification.flags } returns if (isGroupSummary) Notification.FLAG_GROUP_SUMMARY else 0
        every { mockNotification.extras } returns extras
        every { extras.getString(Notification.EXTRA_TITLE) } returns title
        every { extras.getCharSequence(Notification.EXTRA_TEXT) } returns text

        return sbn
    }

    @Test
    fun `onNewNotification ignores when reading disabled in settings`() = runTest {
        every { settings.isNotificationReadingEnabled } returns false

        val sbn = createMockNotification()
        reader.onNewNotification(sbn)

        // Verifies no tts actions were triggered
        verify(exactly = 0) { scannerController.pauseForNotification() }
    }

    @Test
    fun `onNewNotification ignores group summaries`() = runTest {
        val sbn = createMockNotification(isGroupSummary = true)
        reader.onNewNotification(sbn)

        verify(exactly = 0) { scannerController.pauseForNotification() }
    }

    @Test
    fun `onNewNotification filters package names not in monitored apps`() = runTest {
        val sbn = createMockNotification(packageName = "com.unmonitored.app")
        reader.onNewNotification(sbn)

        verify(exactly = 0) { scannerController.pauseForNotification() }
    }

    @Test
    fun `onNewNotification filters when only read in user mode and user mode inactive`() = runTest {
        isUserModeActiveFlow.value = false
        every { settings.autoReadOnlyInUserMode } returns true

        val sbn = createMockNotification()
        reader.onNewNotification(sbn)

        verify(exactly = 0) { scannerController.pauseForNotification() }
    }

    @Test
    fun `onNewNotification filters when in standby and autoReadInStandby is false`() = runTest {
        every { powerManager.isInteractive } returns false
        every { settings.autoReadInStandby } returns false

        val sbn = createMockNotification()
        reader.onNewNotification(sbn)

        verify(exactly = 0) { scannerController.pauseForNotification() }
    }

    @Test
    fun `immediate mode reads notification pauses scanning and cancels notification upon success`() = runTest(testDispatcher) {
        val sbn = createMockNotification(key = "key1", title = "Greeting", text = "How are you?")
        
        var speakCallback: (() -> Unit)? = null
        every { ttsProxy.speakRouted(any(), any(), any()) } answers {
            speakCallback = thirdArg()
        }

        reader.onNewNotification(sbn)
        testScheduler.advanceUntilIdle()

        // Verify paused scanning
        verify { scannerController.pauseForNotification() }
        verify { ttsProxy.isReadingNotification = true }
        verify { ttsProxy.speakRouted("TestApp. Greeting: How are you?", any(), any()) }

        // Complete speaking
        speakCallback?.invoke()
        testScheduler.advanceUntilIdle()

        // Verify resume and cancel notification called
        verify { ttsProxy.isReadingNotification = false }
        verify { notificationService.cancelNotification("key1") }
        verify { scannerController.resumeFromNotification() }
    }

    @Test
    fun `cancellation during reading stops speech and leaves notification in queue`() = runTest(testDispatcher) {
        val sbn = createMockNotification(key = "key2", title = "Greeting", text = "Wait for cancel")

        every { ttsProxy.speakRouted(any(), any(), any()) } just Runs

        reader.onNewNotification(sbn)
        testScheduler.advanceUntilIdle()

        // Cancel
        reader.cancelReading()
        testScheduler.advanceUntilIdle()

        // Verify cancelled and resumed scanner without removing from queue or calling cancelNotification
        verify(exactly = 0) { notificationService.cancelNotification("key2") }
        verify { scannerController.resumeFromNotification() }
    }
}

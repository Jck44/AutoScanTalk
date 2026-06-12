package com.andreas_kratzer.ghosttalk.core.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.andreas_kratzer.ghosttalk.core.audio.AudioDeviceManager
import com.andreas_kratzer.ghosttalk.core.audio.RoutedAudioPlayer
import com.andreas_kratzer.ghosttalk.core.settings.TtsSettings
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field

class AndroidTtsProviderTest {

    private val context = mockk<Context>(relaxed = true)
    private val ttsSettings = mockk<TtsSettings>(relaxed = true)
    private val routedAudioPlayer = mockk<RoutedAudioPlayer>(relaxed = true)
    private val ttsVoiceManager = mockk<TtsVoiceManager>(relaxed = true)
    private val audioDeviceManager = mockk<AudioDeviceManager>(relaxed = true)

    private lateinit var provider: AndroidTtsProvider

    @Before
    fun setup() {
        mockkStatic(Looper::class)
        val mockLooper = mockk<Looper>(relaxed = true)
        every { Looper.getMainLooper() } returns mockLooper

        mockkConstructor(Handler::class)
        every { anyConstructed<Handler>().post(any()) } answers {
            val runnable = firstArg<Runnable>()
            runnable.run()
            true
        }
        every { anyConstructed<Handler>().postDelayed(any(), any()) } answers {
            val runnable = firstArg<Runnable>()
            runnable.run()
            true
        }

        provider = AndroidTtsProvider(
            context = context,
            settingsRepository = ttsSettings,
            routedAudioPlayer = routedAudioPlayer,
            voiceManager = ttsVoiceManager,
            audioDeviceManager = audioDeviceManager
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testStopAllClearsPendingRequestsAndNotifiesCallbacks() {
        var doneFired = false
        var errorFired = false

        provider.speakRouted(
            text = "Test pending request",
            deviceAddress = null,
            queueMode = 0,
            isForCues = false,
            onDone = { doneFired = true },
            onError = { errorFired = true }
        )

        // Verify it was indeed added to pendingRequests
        val pendingRequestsField: Field = AndroidTtsProvider::class.java.getDeclaredField("pendingRequests")
        pendingRequestsField.isAccessible = true
        val pendingRequests = pendingRequestsField.get(provider) as List<*>
        assertEquals(1, pendingRequests.size)

        // Call stopAll
        provider.stopAll()

        // Verify the list is cleared
        assertTrue(pendingRequests.isEmpty())

        // Verify the callback was triggered (either onError or onDone fallback)
        assertTrue(errorFired || doneFired)
    }
}

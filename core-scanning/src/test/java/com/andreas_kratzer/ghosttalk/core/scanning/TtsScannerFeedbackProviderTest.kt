package com.andreas_kratzer.ghosttalk.core.scanning

import com.andreas_kratzer.ghosttalk.core.settings.ScanningSettings
import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TtsScannerFeedbackProviderTest {

    private val mockTtsHelper = mockk<TextToSpeechHelper>(relaxed = true)
    private val mockSettings = mockk<ScanningSettings>(relaxed = true)
    private val isReadyFlow = MutableStateFlow(false)
    private val onDoneSlot = io.mockk.slot<() -> Unit>()

    private lateinit var provider: TtsScannerFeedbackProvider

    @Before
    fun setup() {
        every { mockTtsHelper.isReadyFlow } returns isReadyFlow
        every { mockSettings.cuesAudioDeviceAddress } returns "mock_cues_device"
        
        // Mock speakRouted to immediately invoke the onDone callback
        every { 
            mockTtsHelper.speakRouted(any(), any(), any(), any(), capture(onDoneSlot), any()) 
        } answers {
            onDoneSlot.captured.invoke()
        }
        
        provider = TtsScannerFeedbackProvider(mockTtsHelper, mockSettings)
    }

    @Test
    fun testSpeakCue_whenTtsReady_speaksImmediately() = runTest {
        every { mockTtsHelper.isReady } returns true
        
        provider.speakCue("Hello")

        // Should speak immediately
        verify(exactly = 1) {
            mockTtsHelper.speakRouted("Hello", "mock_cues_device", any(), true, any(), any())
        }
        assertEquals(0L, currentTime)
    }

    @Test
    fun testSpeakCue_whenTtsNotReadyButBecomesReady_waitsAndSpeaks() = runTest {
        every { mockTtsHelper.isReady } returns false
        isReadyFlow.value = false

        // Launch a coroutine to change the ready status to true after 1000ms virtual delay
        launch {
            kotlinx.coroutines.delay(1000)
            every { mockTtsHelper.isReady } returns true
            isReadyFlow.value = true
        }

        provider.speakCue("Delayed Hello")

        verify(exactly = 1) {
            mockTtsHelper.speakRouted("Delayed Hello", "mock_cues_device", any(), true, any(), any())
        }
        assertEquals(1000L, currentTime)
    }

    @Test
    fun testSpeakCue_whenTtsNeverReady_skipsAfterTwoSeconds() = runTest {
        every { mockTtsHelper.isReady } returns false
        isReadyFlow.value = false

        provider.speakCue("Skip Hello")

        // Should NOT speak
        verify(exactly = 0) {
            mockTtsHelper.speakRouted("Skip Hello", any(), any(), any(), any(), any())
        }
        // Should wait exactly 2000ms (timeout)
        assertEquals(2000L, currentTime)
    }
}

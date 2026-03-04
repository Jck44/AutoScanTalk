package com.andreas_kratzer.ghosttalk.core.actions

import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SpeechActionHandlerTest {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var ttsHelper: TextToSpeechHelper
    private val logMessages = mutableListOf<String>()
    private val logFn: (String) -> Unit = { logMessages.add(it) }

    @Before
    fun setup() {
        settingsRepository = mockk(relaxed = true)
        ttsHelper = mockk(relaxed = true) {
            every { isReady } returns true
        }
        logMessages.clear()
    }

    @Test
    fun `uses spokenText over label when spokenText is present`() {
        val handler = SpeechActionHandler(settingsRepository, ttsHelper, logFn)
        val config = ButtonConfig(
            id = "b1",
            label = "Short Label",
            spokenText = "Full spoken sentence",
            auditoryCue = null,
            isActive = true,
            buttonAction = SpeakTextButtonAction()
        )

        handler.handle(config, config.buttonAction as SpeakTextButtonAction, 1) {}

        // speakRouted(text, deviceAddress, ttsMode, queueMode, isForCues, onDone)
        verify { ttsHelper.speakRouted(eq("Full spoken sentence"), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `falls back to label when spokenText is null`() {
        val handler = SpeechActionHandler(settingsRepository, ttsHelper, logFn)
        val config = ButtonConfig(
            id = "b1",
            label = "Fallback Label",
            spokenText = null,
            auditoryCue = null,
            isActive = true,
            buttonAction = SpeakTextButtonAction()
        )

        handler.handle(config, config.buttonAction as SpeakTextButtonAction, 1) {}

        verify { ttsHelper.speakRouted(eq("Fallback Label"), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `falls back to label when spokenText is blank`() {
        val handler = SpeechActionHandler(settingsRepository, ttsHelper, logFn)
        val config = ButtonConfig(
            id = "b1",
            label = "Fallback Label",
            spokenText = "   ",
            auditoryCue = null,
            isActive = true,
            buttonAction = SpeakTextButtonAction()
        )

        handler.handle(config, config.buttonAction as SpeakTextButtonAction, 1) {}

        verify { ttsHelper.speakRouted(eq("Fallback Label"), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `tts not ready still calls onFinish`() {
        every { ttsHelper.isReady } returns false
        val handler = SpeechActionHandler(settingsRepository, ttsHelper, logFn)
        val config = ButtonConfig(
            id = "b1",
            label = "Test",
            auditoryCue = null,
            isActive = true,
            buttonAction = SpeakTextButtonAction()
        )

        var finishedId = -1
        handler.handle(config, config.buttonAction as SpeakTextButtonAction, 42) { finishedId = it }

        assertEquals("onFinish must be called even when TTS is not ready", 42, finishedId)
        assertTrue("Should log TTS-not-ready message", logMessages.any { it.contains("nicht bereit") })
    }

    @Test
    fun `tts helper null still calls onFinish`() {
        val handler = SpeechActionHandler(settingsRepository, null, logFn)
        val config = ButtonConfig(
            id = "b1",
            label = "Test",
            auditoryCue = null,
            isActive = true,
            buttonAction = SpeakTextButtonAction()
        )

        var finishedId = -1
        handler.handle(config, config.buttonAction as SpeakTextButtonAction, 42) { finishedId = it }

        assertEquals("onFinish must be called even when ttsHelper is null", 42, finishedId)
    }

    @Test
    fun `routes to cues device when playActionAsAuditoryCue is true`() {
        every { settingsRepository.cuesAudioDeviceAddress } returns "cues-device-addr"
        every { settingsRepository.ttsAudioDeviceAddress } returns "tts-device-addr"

        val handler = SpeechActionHandler(settingsRepository, ttsHelper, logFn)
        val config = ButtonConfig(
            id = "b1",
            label = "Test",
            auditoryCue = null,
            isActive = true,
            buttonAction = SpeakTextButtonAction(),
            playActionAsAuditoryCue = true
        )

        handler.handle(config, config.buttonAction as SpeakTextButtonAction, 1) {}

        verify { ttsHelper.speakRouted(any(), eq("cues-device-addr"), any(), any(), any(), any()) }
    }

    @Test
    fun `routes to tts device when playActionAsAuditoryCue is false`() {
        every { settingsRepository.cuesAudioDeviceAddress } returns "cues-device-addr"
        every { settingsRepository.ttsAudioDeviceAddress } returns "tts-device-addr"

        val handler = SpeechActionHandler(settingsRepository, ttsHelper, logFn)
        val config = ButtonConfig(
            id = "b1",
            label = "Test",
            auditoryCue = null,
            isActive = true,
            buttonAction = SpeakTextButtonAction(),
            playActionAsAuditoryCue = false
        )

        handler.handle(config, config.buttonAction as SpeakTextButtonAction, 1) {}

        verify { ttsHelper.speakRouted(any(), eq("tts-device-addr"), any(), any(), any(), any()) }
    }

    @Test
    fun `passes correct ttsMode to speakRouted`() {
        val handler = SpeechActionHandler(settingsRepository, ttsHelper, logFn)
        val action = SpeakTextButtonAction(ttsMode = "WHISPER")
        val config = ButtonConfig(
            id = "b1",
            label = "Test",
            auditoryCue = null,
            isActive = true,
            buttonAction = action
        )

        handler.handle(config, action, 1) {}

        verify { ttsHelper.speakRouted(any(), any(), eq("WHISPER"), any(), any(), any()) }
    }
}

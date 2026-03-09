package com.andreas_kratzer.ghosttalk.core.actions

import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.model.SpeakTextButtonAction
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class SpeechActionHandlerTest {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var ttsHelper: TextToSpeechHelper
    private lateinit var log: (String) -> Unit
    private lateinit var handler: SpeechActionHandler

    @Before
    fun setup() {
        settingsRepository = mockk(relaxed = true)
        ttsHelper = mockk(relaxed = true)
        log = mockk(relaxed = true)
        handler = SpeechActionHandler(settingsRepository, ttsHelper, log)
    }

    @Test
    fun `canHandle returns true for SpeakTextButtonAction`() {
        assert(handler.canHandle(SpeakTextButtonAction()))
    }

    @Test
    fun `handle prefers spokenText over label`() {
        val action = SpeakTextButtonAction()
        val config = ButtonConfig(
            id = "b1",
            label = "Label",
            spokenText = "Spoken",
            buttonAction = action,
            auditoryCue = null
        )
        every { ttsHelper.isReady } returns true

        handler.handle(config, action, 1) {}

        verify { ttsHelper.speakRouted("Spoken", any(), any(), any(), any()) }
    }

    @Test
    fun `handle uses label if spokenText is blank`() {
        val action = SpeakTextButtonAction()
        val config = ButtonConfig(
            id = "b1",
            label = "Label",
            spokenText = "",
            buttonAction = action,
            auditoryCue = null
        )
        every { ttsHelper.isReady } returns true

        handler.handle(config, action, 1) {}

        verify { ttsHelper.speakRouted("Label", any(), any(), any(), any()) }
    }

    @Test
    fun `handle uses cuesAudioDeviceAddress when playActionAsAuditoryCue is true`() {
        val action = SpeakTextButtonAction()
        val config = ButtonConfig(
            id = "b1",
            label = "Test",
            buttonAction = action,
            playActionAsAuditoryCue = true,
            auditoryCue = null
        )
        every { ttsHelper.isReady } returns true
        every { settingsRepository.cuesAudioDeviceAddress } returns "cues-addr"

        handler.handle(config, action, 1) {}

        verify { ttsHelper.speakRouted(any(), "cues-addr", any(), any(), any()) }
    }

    @Test
    fun `handle uses ttsAudioDeviceAddress when playActionAsAuditoryCue is false`() {
        val action = SpeakTextButtonAction()
        val config = ButtonConfig(
            id = "b1",
            label = "Test",
            buttonAction = action,
            playActionAsAuditoryCue = false,
            auditoryCue = null
        )
        every { ttsHelper.isReady } returns true
        every { settingsRepository.ttsAudioDeviceAddress } returns "tts-addr"

        handler.handle(config, action, 1) {}

        verify { ttsHelper.speakRouted(any(), "tts-addr", any(), any(), any()) }
    }

    @Test
    fun `handle finishes immediately and logs if tts is not ready`() {
        val action = SpeakTextButtonAction()
        val config = ButtonConfig(id = "b1", label = "Test", buttonAction = action, auditoryCue = null)
        every { ttsHelper.isReady } returns false
        
        val finishCallback = mockk<(Int) -> Unit>(relaxed = true)

        handler.handle(config, action, 1, finishCallback)

        verify { log("Sprechen (TTS nicht bereit): \"Test\"") }
        verify { finishCallback(1) }
    }

    @Test
    fun `handle calls onFinish when tts speak completes`() {
        val action = SpeakTextButtonAction()
        val config = ButtonConfig(id = "b1", label = "Test", buttonAction = action, auditoryCue = null)
        every { ttsHelper.isReady } returns true
        
        val onCompleteSlot = slot<() -> Unit>()
        every { ttsHelper.speakRouted(any(), any(), any(), any(), capture(onCompleteSlot)) } returns Unit
        
        val finishCallback = mockk<(Int) -> Unit>(relaxed = true)

        handler.handle(config, action, 1, finishCallback)
        
        onCompleteSlot.captured.invoke()

        verify { finishCallback(1) }
        verify { log("Gesprochen: \"Test\"") }
    }
}

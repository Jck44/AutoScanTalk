package com.andreas_kratzer.ghosttalk.core.actions

import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class SpeechActionHandlerTest {

    private lateinit var settings: SpeechSettings
    private lateinit var ttsProxy: ActionTtsProxy
    private lateinit var actionLogger: ActionLogger
    private lateinit var handler: SpeechActionHandler

    @Before
    fun setup() {
        settings = mockk(relaxed = true)
        ttsProxy = mockk(relaxed = true)
        actionLogger = mockk(relaxed = true)
        handler = SpeechActionHandler(settings, object : dagger.Lazy<ActionTtsProxy> {
            override fun get() = ttsProxy
        }, actionLogger)
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
        every { ttsProxy.isReady } returns true

        handler.handle(config, action, 1) {}

        verify { ttsProxy.speakRouted("Spoken", any(), any(), any(), any()) }
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
        every { ttsProxy.isReady } returns true

        handler.handle(config, action, 1) {}

        verify { ttsProxy.speakRouted("Label", any(), any(), any(), any()) }
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
        every { ttsProxy.isReady } returns true
        every { settings.cuesAudioDeviceAddress } returns "cues-addr"

        handler.handle(config, action, 1) {}

        verify { ttsProxy.speakRouted(any(), "cues-addr", any(), any(), any()) }
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
        every { ttsProxy.isReady } returns true
        every { settings.ttsAudioDeviceAddress } returns "tts-addr"

        handler.handle(config, action, 1) {}

        verify { ttsProxy.speakRouted(any(), "tts-addr", any(), any(), any()) }
    }

    @Test
    fun `handle finishes immediately and logs if tts is not ready`() {
        val action = SpeakTextButtonAction()
        val config = ButtonConfig(id = "b1", label = "Test", buttonAction = action, auditoryCue = null)
        every { ttsProxy.isReady } returns false
        
        val finishCallback = mockk<(Int) -> Unit>(relaxed = true)

        handler.handle(config, action, 1, finishCallback)

        verify { actionLogger.log("Sprechen (TTS nicht bereit): \"Test\"") }
        verify { finishCallback(1) }
    }

    @Test
    fun `handle calls onFinish when tts speak completes`() {
        val action = SpeakTextButtonAction()
        val config = ButtonConfig(id = "b1", label = "Test", buttonAction = action, auditoryCue = null)
        every { ttsProxy.isReady } returns true
        
        val onCompleteSlot = slot<() -> Unit>()
        every { ttsProxy.speakRouted(any(), any(), any(), any(), capture(onCompleteSlot)) } returns Unit
        
        val finishCallback = mockk<(Int) -> Unit>(relaxed = true)

        handler.handle(config, action, 1, finishCallback)
        
        onCompleteSlot.captured.invoke()

        verify { finishCallback(1) }
        verify { actionLogger.log("Gesprochen: \"Test\"") }
    }
}

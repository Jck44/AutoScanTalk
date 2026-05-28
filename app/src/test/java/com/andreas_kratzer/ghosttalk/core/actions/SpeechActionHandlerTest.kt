package com.andreas_kratzer.ghosttalk.core.actions

import android.content.Context
import com.andreas_kratzer.ghosttalk.core.audio.RoutedAudioPlayer
import com.andreas_kratzer.ghosttalk.core.model.ButtonConfig
import com.andreas_kratzer.ghosttalk.core.model.SpeakTextButtonAction
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class SpeechActionHandlerTest {

    private lateinit var context: Context
    private lateinit var settings: SpeechSettings
    private lateinit var ttsProxy: ActionTtsProxy
    private lateinit var audioPlayer: RoutedAudioPlayer
    private lateinit var actionLogger: ActionLogger
    private lateinit var handler: SpeechActionHandler

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        settings = mockk(relaxed = true)
        ttsProxy = mockk(relaxed = true)
        audioPlayer = mockk(relaxed = true)
        actionLogger = mockk(relaxed = true)
        handler = SpeechActionHandler(
            context = context,
            settings = settings,
            ttsProxyLazy = object : dagger.Lazy<ActionTtsProxy> {
                override fun get() = ttsProxy
            },
            audioPlayerLazy = object : dagger.Lazy<RoutedAudioPlayer> {
                override fun get() = audioPlayer
            },
            actionLogger = actionLogger
        )
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

        verify { actionLogger.log("Sprechen (TTS nicht bereit): \"Test\"", action, "Test") }
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
        verify { actionLogger.log("Gesprochen: \"Test\"", action, "Test") }
    }

    @Test
    fun `handle plays recorded audio if mode is AUDIO and file exists`() {
        val action = SpeakTextButtonAction()
        val config = ButtonConfig(
            id = "b1",
            label = "Label",
            spokenTextMode = com.andreas_kratzer.ghosttalk.core.model.SpokenTextMode.AUDIO,
            audioFileName = "audio_b1.ogg",
            buttonAction = action,
            auditoryCue = null
        )
        
        val tempDir = java.nio.file.Files.createTempDirectory("ghosttalk_test").toFile()
        val audioFile = java.io.File(tempDir.resolve("audio_recordings"), "audio_b1.ogg")
        audioFile.parentFile?.mkdirs()
        audioFile.createNewFile()
        
        every { context.filesDir } returns tempDir
        every { settings.ttsAudioDeviceAddress } returns "tts-device"

        val finishCallback = mockk<(Int) -> Unit>(relaxed = true)
        val onCompleteSlot = slot<() -> Unit>()
        every { audioPlayer.playAudioFile(any(), any(), any(), capture(onCompleteSlot)) } answers {
            onCompleteSlot.captured.invoke()
        }

        handler.handle(config, action, 1, finishCallback)

        verify { audioPlayer.playAudioFile(audioFile, "tts-device", any(), any()) }
        verify { finishCallback(1) }
        verify { actionLogger.log("Sprachaufnahme abgespielt: \"audio_b1.ogg\"", action, "Label") }
        
        audioFile.delete()
        tempDir.delete()
    }

    @Test
    fun `handle falls back to TTS if mode is AUDIO but file does not exist`() {
        val action = SpeakTextButtonAction()
        val config = ButtonConfig(
            id = "b1",
            label = "Label",
            spokenText = "Fallback TTS",
            spokenTextMode = com.andreas_kratzer.ghosttalk.core.model.SpokenTextMode.AUDIO,
            audioFileName = "nonexistent.ogg",
            buttonAction = action,
            auditoryCue = null
        )
        
        val tempDir = java.nio.file.Files.createTempDirectory("ghosttalk_test").toFile()
        every { context.filesDir } returns tempDir
        every { ttsProxy.isReady } returns true
        every { settings.ttsAudioDeviceAddress } returns "tts-device"

        val finishCallback = mockk<(Int) -> Unit>(relaxed = true)
        val onCompleteSlot = slot<() -> Unit>()
        every { ttsProxy.speakRouted(any(), any(), any(), any(), capture(onCompleteSlot)) } answers {
            onCompleteSlot.captured.invoke()
        }

        handler.handle(config, action, 1, finishCallback)

        verify(exactly = 0) { audioPlayer.playAudioFile(any(), any(), any(), any()) }
        verify { ttsProxy.speakRouted("Fallback TTS", "tts-device", any(), any(), any()) }
        verify { finishCallback(1) }
        verify { actionLogger.log("Gesprochen: \"Fallback TTS\"", action, "Label") }
        
        tempDir.delete()
    }
}

package com.andreas_kratzer.ghosttalk.core.tts

import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class FallbackTtsProviderTest {

    private val scope = kotlinx.coroutines.CoroutineScope(kotlin.coroutines.EmptyCoroutineContext)

    @Test
    fun `onDone fires exactly once when primary fails and fallback succeeds`() {
        val primary = FakeTtsProvider()
        val fallback = FakeTtsProvider()
        var fallbackTriggeredCount = 0
        var onFallbackError: String? = null

        val provider = FallbackTtsProvider(primary, fallback, scope) { error ->
            fallbackTriggeredCount++
            onFallbackError = error
        }

        var callerDoneCount = 0
        var callerErrorCount = 0

        // Primary fails immediately on call, but also tries to call onDone (violating contract)
        primary.onSpeakRoutedCalled = { onDone, onError ->
            onError?.invoke("primary failed")
            onDone?.invoke()
        }

        // Fallback succeeds later manually
        var fallbackDoneCallback: (() -> Unit)? = null
        fallback.onSpeakRoutedCalled = { onDone, _ ->
            fallbackDoneCallback = onDone
        }

        provider.speakRouted("Hello", null, 0, false,
            onDone = { callerDoneCount++ },
            onError = { callerErrorCount++ }
        )

        // At this point, fallback should have been triggered
        assertEquals(1, fallbackTriggeredCount)
        assertEquals("primary failed", onFallbackError)
        assertEquals(1, fallback.speakRoutedCalledCount)

        // Caller should not have received onDone yet because fallback hasn't finished
        assertEquals(0, callerDoneCount)
        assertEquals(0, callerErrorCount)

        // Now trigger fallback success
        fallbackDoneCallback?.invoke()

        // Caller onDone should fire exactly once
        assertEquals(1, callerDoneCount)
        assertEquals(0, callerErrorCount)

        // Try to trigger primary's onDone late again, should be ignored
        primary.triggerOnDone?.invoke()
        assertEquals(1, callerDoneCount)
    }

    @Test
    fun `onDone is not fired before fallback completes`() {
        val primary = FakeTtsProvider()
        val fallback = FakeTtsProvider()
        val provider = FallbackTtsProvider(primary, fallback, scope) {}

        var callerDone = false
        primary.onSpeakRoutedCalled = { _, onError ->
            onError?.invoke("error")
        }

        provider.speakRouted("Hello", null, 0, false,
            onDone = { callerDone = true },
            onError = {}
        )

        assertFalse(callerDone)
        fallback.triggerOnDone?.invoke()
        assertTrue(callerDone)
    }

    @Test
    fun `onDone fires once when primary succeeds`() {
        val primary = FakeTtsProvider()
        val fallback = FakeTtsProvider()
        var fallbackTriggered = false
        val provider = FallbackTtsProvider(primary, fallback, scope) { fallbackTriggered = true }

        var callerDoneCount = 0
        primary.onSpeakRoutedCalled = { onDone, _ ->
            onDone?.invoke()
        }

        provider.speakRouted("Hello", null, 0, false,
            onDone = { callerDoneCount++ },
            onError = {}
        )

        assertEquals(1, callerDoneCount)
        assertFalse(fallbackTriggered)
        assertEquals(0, fallback.speakRoutedCalledCount)
    }

    @Test
    fun `onFallbackTriggered fires exactly once per utterance`() {
        val primary = FakeTtsProvider()
        val fallback = FakeTtsProvider()
        var fallbackTriggeredCount = 0
        val provider = FallbackTtsProvider(primary, fallback, scope) { fallbackTriggeredCount++ }

        primary.onSpeakRoutedCalled = { _, onError ->
            onError?.invoke("error1")
            onError?.invoke("error2")
        }

        provider.speakRouted("Hello", null, 0, false, onDone = {}, onError = {})

        assertEquals(1, fallbackTriggeredCount)
        assertEquals(1, fallback.speakRoutedCalledCount)
    }

    @Test
    fun `late onError after successful onDone does not start fallback`() {
        val primary = FakeTtsProvider()
        val fallback = FakeTtsProvider()
        var fallbackTriggered = false
        val provider = FallbackTtsProvider(primary, fallback, scope) { fallbackTriggered = true }

        var callerDoneCount = 0
        var callerErrorCount = 0

        provider.speakRouted("Hello", null, 0, false,
            onDone = { callerDoneCount++ },
            onError = { callerErrorCount++ }
        )

        // Primary succeeds
        primary.triggerOnDone?.invoke()
        assertEquals(1, callerDoneCount)

        // Primary fires a late error (violating contract)
        primary.triggerOnError?.invoke("late error")

        // Should not trigger fallback, and counts should remain unchanged
        assertFalse(fallbackTriggered)
        assertEquals(0, fallback.speakRoutedCalledCount)
        assertEquals(1, callerDoneCount)
        assertEquals(0, callerErrorCount)
    }

    @Test
    fun `caller onError fires once when both providers fail`() {
        val primary = FakeTtsProvider()
        val fallback = FakeTtsProvider()
        val provider = FallbackTtsProvider(primary, fallback, scope) {}

        var callerDoneCount = 0
        var callerErrorCount = 0

        primary.onSpeakRoutedCalled = { _, onError ->
            onError?.invoke("primary error")
        }
        fallback.onSpeakRoutedCalled = { _, onError ->
            onError?.invoke("fallback error")
        }

        provider.speakRouted("Hello", null, 0, false,
            onDone = { callerDoneCount++ },
            onError = { callerErrorCount++ }
        )

        assertEquals(0, callerDoneCount)
        assertEquals(1, callerErrorCount)
    }

    @Test
    fun `speak delegates with same guarantees`() {
        val primary = FakeTtsProvider()
        val fallback = FakeTtsProvider()
        var fallbackTriggeredCount = 0
        val provider = FallbackTtsProvider(primary, fallback, scope) { fallbackTriggeredCount++ }

        var callerDoneCount = 0
        var callerErrorCount = 0

        primary.onSpeakCalled = { _, onError ->
            onError?.invoke("error")
        }
        fallback.onSpeakCalled = { onDone, _ ->
            onDone?.invoke()
        }

        provider.speak("Hello", 0,
            onDone = { callerDoneCount++ },
            onError = { callerErrorCount++ }
        )

        assertEquals(1, fallbackTriggeredCount)
        assertEquals(1, fallback.speakCalledCount)
        assertEquals(1, callerDoneCount)
        assertEquals(0, callerErrorCount)
    }

    @Test
    fun `canceled call completes via onDone and does not trigger fallback`() {
        val primary = FakeTtsProvider()
        val fallback = FakeTtsProvider()
        var fallbackTriggered = false
        val provider = FallbackTtsProvider(primary, fallback, scope) { fallbackTriggered = true }

        var callerDoneCount = 0
        var callerErrorCount = 0

        // Primary is canceled, so it calls onDone
        primary.onSpeakRoutedCalled = { onDone, _ ->
            onDone?.invoke()
        }

        provider.speakRouted("Hello", null, 0, false,
            onDone = { callerDoneCount++ },
            onError = { callerErrorCount++ }
        )

        assertEquals(1, callerDoneCount)
        assertEquals(0, callerErrorCount)
        assertFalse(fallbackTriggered)
        assertEquals(0, fallback.speakRoutedCalledCount)
    }
}

class FakeTtsProvider : TtsProvider {
    override val isReady: Boolean = true
    override val isReadyFlow: StateFlow<Boolean> = kotlinx.coroutines.flow.MutableStateFlow(true)
    override val availableVoicesFlow: StateFlow<List<TtsVoice>> = kotlinx.coroutines.flow.MutableStateFlow(emptyList())

    var lastText: String? = null
    var speakCalledCount = 0
    var speakRoutedCalledCount = 0

    var triggerOnDone: (() -> Unit)? = null
    var triggerOnError: ((String) -> Unit)? = null

    var onSpeakCalled: ((onDone: (() -> Unit)?, onError: ((String) -> Unit)?) -> Unit)? = null
    var onSpeakRoutedCalled: ((onDone: (() -> Unit)?, onError: ((String) -> Unit)?) -> Unit)? = null

    override fun speak(text: String, queueMode: Int, onDone: (() -> Unit)?, onError: ((String) -> Unit)?) {
        lastText = text
        speakCalledCount++
        triggerOnDone = onDone
        triggerOnError = onError
        onSpeakCalled?.invoke(onDone, onError)
    }

    override fun speakRouted(
        text: String,
        deviceAddress: String?,
        queueMode: Int,
        isForCues: Boolean,
        onDone: (() -> Unit)?,
        onError: ((String) -> Unit)?
    ) {
        lastText = text
        speakRoutedCalledCount++
        triggerOnDone = onDone
        triggerOnError = onError
        onSpeakRoutedCalled?.invoke(onDone, onError)
    }

    override fun stopAll() {}
    override fun shutdown() {}
    override fun isSpeaking(): Boolean = false
    override fun setLanguageAndVoice(languageTag: String?, voiceName: String?) {}
    override fun setVoice(voiceName: String?) {}
    override fun getAvailableLanguages(): List<Locale> = emptyList()
    override fun getAvailableVoices(languageTag: String?): List<TtsVoice> = emptyList()
}

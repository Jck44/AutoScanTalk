package com.andreas_kratzer.ghosttalk.ui.pages.delegates

import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

class TtsPreviewDelegate @Inject constructor(
    private val ttsHelper: TextToSpeechHelper,
    private val settingsRepository: SettingsRepository
) {
    private lateinit var scope: CoroutineScope

    fun init(coroutineScope: CoroutineScope) {
        this.scope = coroutineScope
    }

    fun isTextCached(text: String): Boolean {
        return ttsHelper.isCached(text)
    }

    fun prefetchText(text: String, onComplete: () -> Unit) {
        scope.launch {
            ttsHelper.prefetch(text)
            onComplete()
        }
    }

    fun isTtsElevenLabs(): Boolean {
        return settingsRepository.ttsEngine == "elevenlabs"
    }

    fun speakTtsPreview(text: String, onDone: () -> Unit) {
        ttsHelper.speak(text, queueMode = 0, onDone = onDone, onError = { onDone() })
    }

    fun stopTtsPreview() {
        ttsHelper.stopAll()
    }
}

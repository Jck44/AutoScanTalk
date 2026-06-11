package com.andreas_kratzer.ghosttalk.core.scanning

import com.andreas_kratzer.ghosttalk.core.settings.ScanningSettings
import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import kotlin.coroutines.resume

class TtsScannerFeedbackProvider @Inject constructor(
    private val ttsHelper: TextToSpeechHelper,
    private val scanningSettings: ScanningSettings
) : ScannerFeedbackProvider {
    
    override suspend fun speakCue(text: String) {
        val ready = try {
            withTimeout(2000) {
                if (ttsHelper.isReady) true else ttsHelper.isReadyFlow.first { it }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            false
        }

        if (ready) {
            kotlinx.coroutines.withTimeoutOrNull(5000) {
                kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                    ttsHelper.speakRouted(
                        text = text,
                        deviceAddress = scanningSettings.cuesAudioDeviceAddress,
                        queueMode = android.speech.tts.TextToSpeech.QUEUE_FLUSH,
                        isForCues = true,
                        onDone = {
                            if (continuation.isActive) {
                                continuation.resume(Unit)
                            }
                        }
                    )
                }
            }
        }
    }

    override suspend fun prefetchCue(text: String) {
        ttsHelper.prefetch(text)
    }
}

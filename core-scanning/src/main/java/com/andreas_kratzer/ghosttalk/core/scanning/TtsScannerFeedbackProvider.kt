package com.andreas_kratzer.ghosttalk.core.scanning

import com.andreas_kratzer.ghosttalk.core.settings.ScanningSettings
import com.andreas_kratzer.ghosttalk.core.tts.TextToSpeechHelper
import kotlinx.coroutines.delay
import javax.inject.Inject
import kotlin.coroutines.resume

class TtsScannerFeedbackProvider @Inject constructor(
    private val ttsHelper: TextToSpeechHelper,
    private val scanningSettings: ScanningSettings
) : ScannerFeedbackProvider {
    
    override suspend fun speakCue(text: String) {
        var retries = 0
        while (!ttsHelper.isReady && retries < 20) {
            delay(100)
            retries++
        }

        if (ttsHelper.isReady) {
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

    override suspend fun prefetchCue(text: String) {
        ttsHelper.prefetch(text)
    }
}

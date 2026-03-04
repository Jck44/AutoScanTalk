package com.andreas_kratzer.ghosttalk.core.scanning

import com.andreas_kratzer.ghosttalk.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.tts.TextToSpeechHelper
import kotlinx.coroutines.delay
import javax.inject.Inject

class TtsScannerFeedbackProvider @Inject constructor(
    private val ttsHelper: TextToSpeechHelper,
    private val settingsRepository: SettingsRepository
) : ScannerFeedbackProvider {
    
    override suspend fun speakCue(text: String) {
        var retries = 0
        while (!ttsHelper.isReady && retries < 20) {
            delay(100)
            retries++
        }

        if (ttsHelper.isReady) {
            ttsHelper.speakRouted(
                text = text, 
                deviceAddress = settingsRepository.cuesAudioDeviceAddress,
                queueMode = android.speech.tts.TextToSpeech.QUEUE_FLUSH
            )
        }
    }
}

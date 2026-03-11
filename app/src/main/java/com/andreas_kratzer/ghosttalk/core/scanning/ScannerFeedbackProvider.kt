package com.andreas_kratzer.ghosttalk.core.scanning

interface ScannerFeedbackProvider {
    suspend fun speakCue(text: String)
}

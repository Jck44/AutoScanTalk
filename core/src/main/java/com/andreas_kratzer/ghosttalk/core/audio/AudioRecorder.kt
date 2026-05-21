package com.andreas_kratzer.ghosttalk.core.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Helper class to record audio via the microphone using native OPUS encoding in an OGG container.
 * Uses AudioSource.VOICE_RECOGNITION to utilize hardware-level noise cancellation and speech clarity tuning.
 */
class AudioRecorder(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false

    fun startRecording(outputFile: File) {
        if (isRecording) return

        try {
            // Ensure parent directories exist
            outputFile.parentFile?.mkdirs()

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                setOutputFormat(MediaRecorder.OutputFormat.OGG)
                setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                setOutputFile(outputFile.absolutePath)
                setAudioChannels(1) // Mono for voice recording
                setAudioSamplingRate(44100) // 44.1 kHz sampling rate
                setAudioEncodingBitRate(64000) // 64 kbps is highly optimized for Opus speech
                
                prepare()
                start()
            }
            mediaRecorder = recorder
            isRecording = true
            Log.d("AudioRecorder", "Recording started successfully to: ${outputFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Error starting voice recording", e)
            stopRecording()
            throw e
        }
    }

    fun stopRecording() {
        if (!isRecording) return

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Error stopping MediaRecorder", e)
        } finally {
            mediaRecorder = null
            isRecording = false
            Log.d("AudioRecorder", "Recording stopped.")
        }
    }
}

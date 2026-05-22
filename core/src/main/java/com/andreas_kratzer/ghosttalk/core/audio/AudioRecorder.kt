package com.andreas_kratzer.ghosttalk.core.audio

import android.content.Context
import android.media.MediaRecorder
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

            val recorder = MediaRecorder(context)

            val prefs = context.getSharedPreferences("ghosttalk_settings", Context.MODE_PRIVATE)
            val audioSource = prefs.getInt("recording_audio_source", MediaRecorder.AudioSource.VOICE_COMMUNICATION)

            recorder.apply {
                setAudioSource(audioSource)
                setOutputFormat(MediaRecorder.OutputFormat.OGG)
                setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                setOutputFile(outputFile.absolutePath)
                setAudioChannels(1) // Mono for voice recording
                setAudioSamplingRate(48000) // 48 kHz sampling rate (native for Opus)
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

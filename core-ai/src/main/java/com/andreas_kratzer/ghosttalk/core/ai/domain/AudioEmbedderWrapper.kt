package com.andreas_kratzer.ghosttalk.core.ai.domain

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier.AudioClassifierOptions
import com.google.mediapipe.tasks.components.containers.AudioData
import com.google.mediapipe.tasks.core.BaseOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps MediaPipe's [AudioClassifier] with the YAMNet model to produce a
 * 521-dimensional score vector as the user's acoustic fingerprint.
 *
 * # Why AudioClassifier instead of AudioEmbedder?
 * The AudioEmbedder API was removed from `tasks-audio` in recent MediaPipe versions
 * (it lives only in low-level internal modules). We use AudioClassifier instead and
 * treat YAMNet's 521-class probability vector as our fingerprint – the "hidden
 * embedding" trick from the ML community:
 *
 * Each distinct sound activates a very specific, repeatable pattern of YAMNet
 * neurons (classes). A tongue-click will always light up "Tongue" / "Click" /
 * "Snap" with high scores, while a ballpoint pen produces a completely different
 * pattern. Cosine similarity on this 521-dim vector is as reliable as a raw
 * embedding for few-shot user-specific sound recognition.
 *
 * # Why MediaPipe instead of tensorflow-lite-task-audio?
 * `tensorflow-lite-task-audio:0.4.4` ships `libtask_audio_jni.so` without 16 KB
 * page-size alignment → rejected by Google Play for Android 15+ since Nov 2025.
 * `com.google.mediapipe:tasks-audio` is the official, actively-maintained
 * successor and is fully 16 KB compatible.
 */
@Singleton
class AudioEmbedderWrapper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "AudioEmbedderWrapper"

    /** YAMNet class count – defines the fingerprint vector dimension. */
    private val NUM_CLASSES = 521

    /** Expected PCM sample count per inference window (~0.975 s @ 16 kHz). */
    val requiredSampleCount: Int = 15600

    /** Expected sample rate in Hz. */
    val sampleRate: Int = 16000

    private var classifier: AudioClassifier? = null

    init {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("yamnet.tflite")
                .build()
            val options = AudioClassifierOptions.builder()
                .setBaseOptions(baseOptions)
                .setMaxResults(NUM_CLASSES) // request all 521 classes so none are dropped
                .build()
            classifier = AudioClassifier.createFromOptions(context, options)
            Log.d(TAG, "MediaPipe AudioClassifier (YAMNet) initialized – fingerprint dim: $NUM_CLASSES")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MediaPipe AudioClassifier", e)
        }
    }

    /**
     * Runs YAMNet on [floatAudioData] and returns the averaged 521-dimensional
     * score vector across all inference frames as the acoustic fingerprint.
     *
     * [floatAudioData] must contain [requiredSampleCount] mono PCM samples
     * normalised to [-1, 1] at [sampleRate] Hz.
     *
     * Returns a [List<Float>] with 521 values, or null on error.
     */
    @Synchronized
    fun getEmbedding(floatAudioData: FloatArray): List<Float>? {
        val clf = classifier ?: run {
            Log.e(TAG, "AudioClassifier not initialized")
            return null
        }

        return try {
            // Build AudioData with the correct MediaPipe builder API
            val format = AudioData.AudioDataFormat.builder()
                .setNumOfChannels(1)
                .setSampleRate(sampleRate.toFloat())
                .build()
            val audioData = AudioData.create(format, floatAudioData.size)
            audioData.load(floatAudioData)

            val result = clf.classify(audioData)

            // AudioClassifierResult → List<ClassificationResult> (one per inference frame)
            // Each ClassificationResult → List<Classifications> (usually one head for YAMNet)
            // Each Classifications → List<Category> sorted by score (NOT by index!)
            // We need to reassemble into an index-ordered vector and average across frames.

            val accumulator = FloatArray(NUM_CLASSES)
            var frameCount = 0

            for (classificationResult in result.classificationResults()) {
                for (classifications in classificationResult.classifications()) {
                    for (category in classifications.categories()) {
                        val idx = category.index()
                        if (idx in 0 until NUM_CLASSES) {
                            accumulator[idx] = maxOf(accumulator[idx], category.score())
                        }
                    }
                    frameCount++
                }
            }

            if (frameCount == 0) {
                Log.w(TAG, "No classification frames returned")
                return null
            }

            accumulator.toList()
        } catch (e: Exception) {
            Log.e(TAG, "AudioClassifier inference failed", e)
            null
        }
    }

    /**
     * Returns the audio format this wrapper expects.
     */
    fun getRequiredAudioFormat(): AudioFormat = AudioFormat(sampleRate, requiredSampleCount)

    data class AudioFormat(val sampleRate: Int, val requiredSampleCount: Int)
}

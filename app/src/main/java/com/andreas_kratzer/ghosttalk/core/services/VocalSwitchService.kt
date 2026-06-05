package com.andreas_kratzer.ghosttalk.core.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.andreas_kratzer.ghosttalk.core.actions.ActionEvent
import com.andreas_kratzer.ghosttalk.core.actions.ActionEventEmitter
import com.andreas_kratzer.ghosttalk.core.ai.domain.AudioEmbedderWrapper
import com.andreas_kratzer.ghosttalk.core.ai.domain.VocalPatternMatcher
import com.andreas_kratzer.ghosttalk.core.data.VocalProfileRepository
import com.andreas_kratzer.ghosttalk.core.model.VocalProfile
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class VocalSwitchService : Service() {

    @Inject
    lateinit var vocalProfileRepository: VocalProfileRepository

    @Inject
    lateinit var audioEmbedderWrapper: AudioEmbedderWrapper

    @Inject
    lateinit var actionEventEmitter: ActionEventEmitter

    @Inject
    lateinit var vocalPatternMatcher: VocalPatternMatcher

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var isRunning = false
    private var activeProfiles = listOf<VocalProfile>()

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null

    companion object {
        private const val CHANNEL_ID = "vocal_switch_channel"
        private const val NOTIFICATION_ID = 888
        private const val TAG = "VocalSwitchService"
        
        // Cooldown between triggers in milliseconds
        private const val COOLDOWN_MS = 1500L
        
        // Match threshold
        private const val SIMILARITY_THRESHOLD = 0.85f
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            startForegroundServiceNotification()
            observeVocalProfiles()
            startListening()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        stopListening()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Vocal Switch Service"
            val descriptionText = "Listen to vocal commands to trigger switch scanning"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundServiceNotification() {
        val title = "Vocal Switch ist aktiv"
        val text = "Mikrofon wird auf trainierte Laute abgehört"
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun observeVocalProfiles() {
        serviceScope.launch {
            vocalProfileRepository.getActiveProfilesFlow().collectLatest { profiles ->
                activeProfiles = profiles
                Log.d(TAG, "Updated active profiles list: ${profiles.size} profiles loaded")
            }
        }
    }

    private fun startListening() {
        stopListening()
        
        val requiredFormat = audioEmbedderWrapper.getRequiredAudioFormat()
        val sampleRate = requiredFormat.sampleRate
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioEncoding = AudioFormat.ENCODING_PCM_16BIT
        
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioEncoding)
        val windowSamples = 15600
        val bufferSize = maxOf(minBufferSize, windowSamples * 2)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioEncoding,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                return
            }

            audioRecord?.startRecording()
            
            recordingThread = Thread {
                val shortBuffer = ShortArray(1024)
                val circularBuffer = FloatArray(windowSamples)
                var writeIndex = 0
                var accumulatedSamples = 0
                var lastTriggerTime = 0L

                while (isRunning) {
                    val record = audioRecord ?: break
                    val readCount = record.read(shortBuffer, 0, shortBuffer.size)
                    if (readCount <= 0) continue

                    for (i in 0 until readCount) {
                        circularBuffer[writeIndex] = shortBuffer[i] / 32768.0f
                        writeIndex = (writeIndex + 1) % windowSamples
                    }
                    accumulatedSamples += readCount

                    if (accumulatedSamples >= windowSamples) {
                        val snapshot = FloatArray(windowSamples)
                        for (i in 0 until windowSamples) {
                            snapshot[i] = circularBuffer[(writeIndex + i) % windowSamples]
                        }

                        val embedding = audioEmbedderWrapper.getEmbedding(snapshot)
                        if (embedding != null) {
                            val now = System.currentTimeMillis()
                            if (now - lastTriggerTime > COOLDOWN_MS) {
                                checkAndTriggerMatchingProfile(embedding, now) {
                                    lastTriggerTime = now
                                }
                            }
                        }
                    }
                }
            }.apply {
                priority = Thread.MAX_PRIORITY
                start()
            }
            
            Log.d(TAG, "VocalSwitch background recording thread started")
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for recording audio", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening thread", e)
        }
    }

    private fun checkAndTriggerMatchingProfile(
        liveEmbedding: List<Float>,
        now: Long,
        onTriggered: () -> Unit
    ) {
        var bestMatchResult: VocalPatternMatcher.MatchResult? = null
        var bestMatchProfile: VocalProfile? = null
        var bestAdaptiveThreshold = 0.82f

        for (profile in activeProfiles) {
            val adaptiveThreshold = vocalPatternMatcher.calculateAdaptiveThresholdFromList(profile.positiveTemplates)
            val evaluationThreshold = if (profile.buttonAction == null) 0.70f else adaptiveThreshold
            
            val result = vocalPatternMatcher.evaluate(
                inputVector = liveEmbedding,
                positives = profile.positiveTemplates,
                negatives = profile.negativeTemplates,
                threshold = evaluationThreshold
            )
            
            if (result.isMatch) {
                if (bestMatchResult == null || result.positiveConfidence > bestMatchResult.positiveConfidence) {
                    bestMatchResult = result
                    bestMatchProfile = profile
                    bestAdaptiveThreshold = adaptiveThreshold
                }
            }
        }

        if (bestMatchResult != null && bestMatchProfile != null) {
            Log.i(TAG, "Match found! Profile: ${bestMatchProfile.name}, Similarity: ${bestMatchResult.positiveConfidence}, Adaptive Threshold: $bestAdaptiveThreshold")
            onTriggered()
            
            val finalResult = bestMatchResult
            val finalProfile = bestMatchProfile
            val finalThreshold = bestAdaptiveThreshold
            serviceScope.launch {
                actionEventEmitter.emitEvent(
                    ActionEvent.VocalSwitchTriggered(
                        action = finalProfile.buttonAction,
                        label = finalProfile.spokenText ?: finalProfile.name,
                        positiveConfidence = finalResult.positiveConfidence,
                        negativeConfidence = finalResult.negativeConfidence,
                        threshold = finalThreshold
                    )
                )
            }
        }
    }

    private fun stopListening() {
        try {
            audioRecord?.apply {
                if (state == AudioRecord.STATE_INITIALIZED) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioRecord", e)
        } finally {
            audioRecord = null
        }
        
        recordingThread?.interrupt()
        recordingThread = null
        Log.d(TAG, "VocalSwitch background listening stopped")
    }
}

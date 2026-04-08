package com.andreas_kratzer.ghosttalk.core.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import com.andreas_kratzer.ghosttalk.core.audio.RoutedAudioPlayer
import com.andreas_kratzer.ghosttalk.core.settings.TtsSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import android.media.AudioManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class AndroidTtsProvider @Inject constructor(
    private val context: Context,
    private val settingsRepository: TtsSettings,
    private val routedAudioPlayer: RoutedAudioPlayer,
    private val voiceManager: TtsVoiceManager
) : TtsProvider, TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var initialized = false
    private val _availableVoicesFlow = MutableStateFlow<List<TtsVoice>>(emptyList())
    override val availableVoicesFlow: StateFlow<List<TtsVoice>> = _availableVoicesFlow.asStateFlow()

    override val isReady: Boolean get() = initialized
    private val handler = Handler(Looper.getMainLooper())
    private var pendingLanguageTag: String? = null
    private var pendingVoiceName: String? = null
    private var lastDeviceAddress: String? = "uninitialized"

    var fallbackListener: TextToSpeechHelper.OnVoiceFallbackListener? = null

    private data class PlaybackRequest(val file: File, val deviceAddress: String?, val onDoneCallback: (() -> Unit)?)
    private val playRequests = ConcurrentHashMap<String, PlaybackRequest>()
    private val directCallbacks = ConcurrentHashMap<String, () -> Unit>()

    init {
        initializeInternal()
    }

    private fun initializeInternal() {
        if (tts != null) return
        try {
            tts = TextToSpeech(context, this)
        } catch (e: Exception) {
            Log.e("AndroidTtsProvider", "Error initializing TTS: ${e.message}")
        }
    }

    private fun ensureReady() {
        if (tts == null) {
            initializeInternal()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            initialized = true
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    if (utteranceId == null) return
                    val request = playRequests.remove(utteranceId)
                    if (request != null) {
                        handler.postDelayed({
                            if (request.file.exists() && request.file.length() > 0) {
                                routedAudioPlayer.playAudioFile(request.file, request.deviceAddress) {
                                    request.file.delete()
                                    request.onDoneCallback?.let { callback ->
                                        handler.post { callback() }
                                    }
                                }
                            } else {
                                Log.e("AndroidTtsProvider", "Generated TTS file is empty or missing")
                                request.onDoneCallback?.let { callback ->
                                    handler.post { callback() }
                                }
                            }
                        }, 50)
                    } else if (utteranceId.startsWith("direct_")) {
                        val callback = directCallbacks.remove(utteranceId)
                        callback?.let { handler.post { it() } }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == null) return
                    val request = playRequests.remove(utteranceId)
                    request?.file?.delete()
                    request?.onDoneCallback?.let { handler.post { it() } }
                    
                    if (utteranceId.startsWith("direct_")) {
                        directCallbacks.remove(utteranceId)?.let { handler.post { it() } }
                    }
                }
            })
            handler.postDelayed({
                applyPendingLanguageAndVoice()
            }, 300)
        } else {
            Log.e("AndroidTtsProvider", "TTS init failed! Status code: $status")
            initialized = false
            tts = null
        }
    }

    private fun applyPendingLanguageAndVoice() {
        if (!initialized) return
        applySettings()
        // Update available voices flow after initialization
        _availableVoicesFlow.value = getAvailableVoices(pendingLanguageTag)
    }

    override fun speak(text: String, queueMode: Int, onDone: (() -> Unit)?, onError: ((String) -> Unit)?) {
        speakRouted(text, null, queueMode, false, onDone, onError)
    }

    override fun speakRouted(
        text: String, 
        deviceAddress: String?, 
        queueMode: Int,
        isForCues: Boolean,
        onDone: (() -> Unit)?,
        onError: ((String) -> Unit)?
    ) {
        ensureReady()
        if (!initialized || tts == null) {
            Log.e("AndroidTtsProvider", "TTS not initialized, cannot speak.")
            onDone?.invoke()
            return
        }

        val isRoutingSwitched = lastDeviceAddress != "uninitialized" && lastDeviceAddress != deviceAddress
        lastDeviceAddress = deviceAddress

        if (queueMode == TextToSpeech.QUEUE_FLUSH) {
            routedAudioPlayer.stopAll()
            playRequests.values.forEach { it.onDoneCallback?.let { cb -> handler.post { cb() } } }
            directCallbacks.values.forEach { handler.post { it() } }
            playRequests.clear()
            directCallbacks.clear()
            // Flush the native TTS as well
            tts?.speak("", TextToSpeech.QUEUE_FLUSH, null, "flush_${System.currentTimeMillis()}")
        }
        
        val delayedStart = isRoutingSwitched && queueMode == TextToSpeech.QUEUE_FLUSH

        val startAudio = {
            val actualQueueMode = if (delayedStart) TextToSpeech.QUEUE_ADD else queueMode
            
            if (deviceAddress == null) {
                val utteranceId = "direct_${System.currentTimeMillis()}_${text.hashCode()}"
                if (onDone != null) {
                    directCallbacks[utteranceId] = onDone
                }
                tts?.speak(text, actualQueueMode, null, utteranceId)
            } else {
                val utteranceId = "routed_${System.currentTimeMillis()}_${text.hashCode()}"
                val cacheFile = File(context.cacheDir, "$utteranceId.wav")
                playRequests[utteranceId] = PlaybackRequest(cacheFile, deviceAddress, onDone)

                val params = android.os.Bundle().apply {
                    putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                }
                tts?.synthesizeToFile(text, params, cacheFile, utteranceId)
            }
        }

        if (delayedStart) {
            Log.d("AndroidTtsProvider", "Routing switched, waiting for communication device to clear...")
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            
            if (audioManager.communicationDevice == null) {
                // Already clear, adding minimal hardware settle time Let the HAL process route drop
                handler.postDelayed({ startAudio() }, 100)
            } else {
                var isReadyFired = false
                val listener = object : AudioManager.OnCommunicationDeviceChangedListener {
                    override fun onCommunicationDeviceChanged(device: android.media.AudioDeviceInfo?) {
                        if (device == null && !isReadyFired) {
                            isReadyFired = true
                            audioManager.removeOnCommunicationDeviceChangedListener(this)
                            Log.d("AndroidTtsProvider", "Communication device cleared by OS, adding 50ms hardware settle time")
                            handler.postDelayed({ startAudio() }, 50)
                        }
                    }
                }
                audioManager.addOnCommunicationDeviceChangedListener(context.mainExecutor, listener)
                
                // Fallback timeout in case OS does not fire the event
                handler.postDelayed({
                    if (!isReadyFired) {
                        isReadyFired = true
                        audioManager.removeOnCommunicationDeviceChangedListener(listener)
                        Log.w("AndroidTtsProvider", "Timeout waiting for communication device to clear, proceeding")
                        startAudio()
                    }
                }, 400)
            }
        } else {
            startAudio()
        }
    }

    override fun setLanguageAndVoice(languageTag: String?, voiceName: String?) {
        this.pendingLanguageTag = languageTag
        this.pendingVoiceName = voiceName
        
        applySettings()
        
        // Update available voices flow for the new language
        _availableVoicesFlow.value = getAvailableVoices(languageTag)
    }

    override fun setVoice(voiceName: String?) {
        pendingVoiceName = voiceName
        if (!initialized || tts == null) return
        voiceManager.findVoice(tts, voiceName)?.let { tts?.voice = it }
    }

    private fun applySettings() {
        if (!initialized || tts == null) return

        val tag = pendingLanguageTag
        val locale = if (tag.isNullOrEmpty() || tag == "default") {
            settingsRepository.appLanguage?.let { Locale.forLanguageTag(it) } ?: Locale.getDefault()
        } else {
            Locale.forLanguageTag(tag)
        }

        tts?.setLanguage(locale)
        
        if (!pendingVoiceName.isNullOrEmpty()) {
            val targetVoice = voiceManager.findVoice(tts, pendingVoiceName)
            if (targetVoice != null) {
                if (targetVoice.isNetworkConnectionRequired && !isNetworkAvailable()) {
                    Log.w("AndroidTtsProvider", "Voice $pendingVoiceName requires network but system is offline. Finding local fallback...")
                    
                    val allVoices = tts?.voices ?: emptySet()
                    val localFallback = allVoices.filter { 
                        it.locale.language == targetVoice.locale.language && 
                        it.locale.country == targetVoice.locale.country &&
                        !it.isNetworkConnectionRequired
                    }.firstOrNull()
                    
                    if (localFallback != null) {
                        val fallbackName = localFallback.name ?: ""
                        tts?.voice = localFallback
                        fallbackListener?.onVoiceFallback(pendingVoiceName ?: "", fallbackName, "No Network")
                    } else {
                        tts?.language = locale
                        fallbackListener?.onVoiceFallback(pendingVoiceName ?: "", null, "No Network, No Local Voice")
                    }
                } else {
                    tts?.voice = targetVoice
                }
            } else {
                tts?.language = locale
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val activeNetwork = cm?.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun getAvailableLanguages(): List<Locale> {
        return voiceManager.getAvailableLanguages(tts)
    }

    override fun getAvailableVoices(languageTag: String?): List<TtsVoice> {
        val resolvedTag = if (languageTag.isNullOrEmpty() || languageTag == "default") {
            settingsRepository.appLanguage ?: "default"
        } else {
            languageTag
        }
        return voiceManager.getAvailableVoices(tts, resolvedTag).map { voice ->
            val vName = voice.name ?: ""
            val vLocale = voice.locale ?: Locale.getDefault()
            TtsVoice(
                id = vName,
                name = vName,
                locale = vLocale,
                isNetworkRequired = voice.isNetworkConnectionRequired,
                quality = when (voice.quality) {
                    Voice.QUALITY_VERY_HIGH -> TtsVoice.QUALITY_VERY_HIGH
                    Voice.QUALITY_HIGH -> TtsVoice.QUALITY_HIGH
                    Voice.QUALITY_LOW -> TtsVoice.QUALITY_LOW
                    else -> TtsVoice.QUALITY_NORMAL
                },
                gender = when {
                    vName.lowercase().contains("female") || vName.lowercase().contains("-f-") -> TtsVoice.Gender.FEMALE
                    vName.lowercase().contains("male") || vName.lowercase().contains("-m-") -> TtsVoice.Gender.MALE
                    else -> TtsVoice.Gender.UNKNOWN
                },
                provider = "android"
            )
        }
    }

    override suspend fun prefetch(text: String) {
        // Android TTS does not need prefetching
    }

    override fun stopAll() {
        tts?.stop()
        routedAudioPlayer.stopAll()
        val pendingRouted = playRequests.values.toList()
        playRequests.clear()
        pendingRouted.forEach { request ->
            request.onDoneCallback?.let { cb -> handler.post { cb() } }
            request.file.delete()
        }
        val pendingDirect = directCallbacks.values.toList()
        directCallbacks.clear()
        pendingDirect.forEach { cb -> handler.post { cb() } }
    }

    override fun shutdown() {
        tts?.shutdown()
        initialized = false
    }

    override fun isCached(text: String): Boolean = true
}

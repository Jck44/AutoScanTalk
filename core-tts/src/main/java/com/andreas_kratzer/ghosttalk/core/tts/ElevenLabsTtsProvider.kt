package com.andreas_kratzer.ghosttalk.core.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import com.andreas_kratzer.ghosttalk.core.audio.RoutedAudioPlayer
import com.andreas_kratzer.ghosttalk.core.di.ApplicationScope
import com.andreas_kratzer.ghosttalk.core.settings.CloudSettings
import com.andreas_kratzer.ghosttalk.core.settings.TtsSettings
import com.andreas_kratzer.ghosttalk.core.util.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class ElevenLabsTtsProvider @Inject constructor(
    private val context: Context,
    private val cloudSettings: CloudSettings,
    private val ttsSettings: TtsSettings,
    private val routedAudioPlayer: RoutedAudioPlayer,
    @param:ApplicationScope private val scope: CoroutineScope
) : CacheableTtsProvider {

    private val httpClient = OkHttpClient.Builder()
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val handler = Handler(Looper.getMainLooper())
    
    private var lastDeviceAddress: String? = "uninitialized"
    
    // Default voice ID if none is selected
    private var currentVoiceId: String = DEFAULT_VOICE_ID // Adam

    private val SPEECH_TAG = "elevenlabs_speech"
    private val VOICES_TAG = "elevenlabs_voices"

    private var isInitialized = false
    private val _isReadyFlow = MutableStateFlow(false)
    override val isReadyFlow: StateFlow<Boolean> = _isReadyFlow.asStateFlow()
    override val isReady: Boolean get() = _isReadyFlow.value

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
        Log.i("ElevenLabsTtsProvider", "speakRouted entered for: ${text.take(20)}... (isReady=$isReady, voice=$currentVoiceId)")
        val apiKey = cloudSettings.elevenLabsApiKey
        if (apiKey.isNullOrEmpty()) {
            Log.e("ElevenLabsTtsProvider", "API Key missing")
            onError?.invoke("API Key missing")
            return
        }

        val isRoutingSwitched = lastDeviceAddress != "uninitialized" && lastDeviceAddress != deviceAddress
        lastDeviceAddress = deviceAddress

        if (queueMode == TextToSpeech.QUEUE_FLUSH) {
            stopAll()
        }

        val delayedStart = isRoutingSwitched && queueMode == TextToSpeech.QUEUE_FLUSH

        fun startAudio() {
            val elevenLabsModel = cloudSettings.elevenLabsModel
            val languageCode = getIsoLanguageCode(cloudSettings.elevenLabsTtsLanguage)
            val cachedFile = getCacheFile(text, currentVoiceId, elevenLabsModel, languageCode)

            if (cachedFile.exists() && cachedFile.length() > 0) {
                Log.i("ElevenLabsTtsProvider", "Playing cached audio for ${cachedFile.name}")
                handler.post {
                    routedAudioPlayer.playAudioFile(cachedFile, deviceAddress, playbackSpeed = ttsSettings.ttsPlaybackSpeed) {
                        onDone?.invoke()
                    }
                }
                return
            }
            
            // If offline and not in cache, fail fast to trigger fallback
            if (!NetworkUtils.isNetworkAvailable(context)) {
                Log.w("ElevenLabsTtsProvider", "Offline and no cache found for: ${text.take(20)}... - Triggering fallback")
                handler.post {
                    onError?.invoke("Offline and not in cache")
                }
                return
            }
            
            val requestBody = JSONObject().apply {
                put("text", text)
                put("model_id", elevenLabsModel)
                put("voice_settings", JSONObject().apply {
                    put("stability", cloudSettings.elevenLabsStability)
                    put("similarity_boost", cloudSettings.elevenLabsSimilarityBoost)
                })
                languageCode?.let { put("language_code", it) }
            }.toString().toRequestBody("application/json".toMediaType())


            val request = Request.Builder()
                .url("https://api.elevenlabs.io/v1/text-to-speech/$currentVoiceId")
                .addHeader("xi-api-key", apiKey)
                .post(requestBody)
                .tag(SPEECH_TAG)
                .build()

            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    val isCanceled = e.message?.contains("Canceled") == true || e.message?.contains("Socket closed") == true
                    if (isCanceled) {
                        Log.d("ElevenLabsTtsProvider", "Speech call canceled")
                    } else {
                        Log.e("ElevenLabsTtsProvider", "API call failed: ${e.message}")
                    }
                    
                    handler.post { 
                        if (isCanceled) {
                            onDone?.invoke()
                        } else {
                            onError?.invoke(e.message ?: "Network error")
                        }
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        if (!resp.isSuccessful) {
                            val errorMsg = "API error: ${resp.code} ${resp.message}"
                            Log.e("ElevenLabsTtsProvider", errorMsg)
                            handler.post { 
                                onError?.invoke(errorMsg)
                            }
                            return
                        }

                        val body = resp.body

                        try {
                            val tempFile = File(cachedFile.absolutePath + ".tmp")
                            java.io.FileOutputStream(tempFile).use { output ->
                                body.byteStream().copyTo(output)
                            }
                            tempFile.renameTo(cachedFile)
                            Log.i("ElevenLabsTtsProvider", "Saved audio file size: ${cachedFile.length()} bytes")

                            handler.post {
                                routedAudioPlayer.playAudioFile(cachedFile, deviceAddress, playbackSpeed = ttsSettings.ttsPlaybackSpeed) {
                                    // Keep the file in cacheDir
                                    onDone?.invoke()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("ElevenLabsTtsProvider", "Error saving/playing audio: ${e.message}")
                            handler.post { 
                                onError?.invoke(e.message ?: "Playback error")
                            }
                        }
                    }
                }
            })
        }
        
        if (delayedStart) {
            Log.d("ElevenLabsTtsProvider", "Routing switched, waiting for communication device to clear...")
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            
            if (audioManager.communicationDevice == null) {
                // Already clear, adding minimal hardware settle time
                handler.postDelayed({ startAudio() }, 100)
            } else {
                var isReadyFired = false
                val listener = object : android.media.AudioManager.OnCommunicationDeviceChangedListener {
                    override fun onCommunicationDeviceChanged(device: android.media.AudioDeviceInfo?) {
                        if (device == null && !isReadyFired) {
                            isReadyFired = true
                            audioManager.removeOnCommunicationDeviceChangedListener(this)
                            Log.d("ElevenLabsTtsProvider", "Communication device cleared by OS, adding 50ms hardware settle time")
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
                        Log.w("ElevenLabsTtsProvider", "Timeout waiting for communication device to clear, proceeding")
                        startAudio()
                    }
                }, 400)
            }
        } else {
            startAudio()
        }
    }

    override suspend fun prefetch(text: String) {
        val apiKey = cloudSettings.elevenLabsApiKey
        if (apiKey.isNullOrEmpty()) return
        
        if (!NetworkUtils.isNetworkAvailable(context)) {
            Log.d("ElevenLabsTtsProvider", "Offline - skipping prefetch for: ${text.take(20)}...")
            return
        }

        val elevenLabsModel = cloudSettings.elevenLabsModel
        val languageCode = getIsoLanguageCode(cloudSettings.elevenLabsTtsLanguage)
        val cachedFile = getCacheFile(text, currentVoiceId, elevenLabsModel, languageCode)

        if (cachedFile.exists() && cachedFile.length() > 0) {
            return
        }

        val requestBody = JSONObject().apply {
            put("text", text)
            put("model_id", elevenLabsModel)
            put("voice_settings", JSONObject().apply {
                put("stability", cloudSettings.elevenLabsStability)
                put("similarity_boost", cloudSettings.elevenLabsSimilarityBoost)
            })
            languageCode?.let { put("language_code", it) }
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://api.elevenlabs.io/v1/text-to-speech/$currentVoiceId")
            .addHeader("xi-api-key", apiKey)
            .post(requestBody)
            .tag("PREFETCH")
            .build()

        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                httpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.e("ElevenLabsTtsProvider", "Prefetch failed: ${resp.code} ${resp.message} for text: ${text.take(20)}...")
                        return@withContext
                    }
                    val body = resp.body
                    val tempFile = File(cachedFile.absolutePath + ".tmp")
                    java.io.FileOutputStream(tempFile).use { output ->
                        body.byteStream().copyTo(output)
                    }
                    tempFile.renameTo(cachedFile)
                    Log.i("ElevenLabsTtsProvider", "Prefetched audio: ${cachedFile.name}")
                }
            } catch (e: Exception) {
                Log.e("ElevenLabsTtsProvider", "Error prefetching audio: ${e.message}")
            }
        }
    }
    
    override fun isCached(text: String): Boolean {
        val elevenLabsModel = cloudSettings.elevenLabsModel
        val languageCode = getIsoLanguageCode(cloudSettings.elevenLabsTtsLanguage)
        val cachedFile = getCacheFile(text, currentVoiceId, elevenLabsModel, languageCode)
        return cachedFile.exists() && cachedFile.length() > 0
    }
    
    private fun getCacheFile(text: String, voiceId: String, modelId: String, languageCode: String?): File {
        val tgtDir = File(context.filesDir, "elevenlabs")
        if (!tgtDir.exists()) tgtDir.mkdirs()
        
        val trimmedText = text.trim()
        val textBytes = trimmedText.toByteArray(Charsets.UTF_8)
        var base64Text = android.util.Base64.encodeToString(textBytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
        
        if (base64Text.length > 100) {
            val digest = java.security.MessageDigest.getInstance("MD5")
            val hash = digest.digest(textBytes).joinToString("") { "%02x".format(it) }.take(8)
            base64Text = "${base64Text.take(100)}~$hash"
        }
        
        val safeVoiceId = voiceId.replace(Regex("[^a-zA-Z0-9_-]"), "")
        val safeModelId = modelId.replace(Regex("[^a-zA-Z0-9_-]"), "")
        val safeLang = languageCode?.replace(Regex("[^a-z]"), "") ?: "auto"
        
        val fileName = "tts_eleven#${base64Text}#${safeVoiceId}#${safeModelId}#${safeLang}.mp3"
        val file = File(tgtDir, fileName)
        
        if (file.exists() && file.length() > 0) {
            Log.i("ElevenLabsTtsProvider", "Cache hit: ${file.name}")
            return file
        }

        // --- Robustness Fallbacks ---
        
        // 1. Try legacy model ID (e.g. "v3" instead of "eleven_v3")
        if (safeModelId.startsWith("eleven_")) {
            val legacyModelId = safeModelId.removePrefix("eleven_")
            val legacyFile = File(tgtDir, "tts_eleven#${base64Text}#${safeVoiceId}#${legacyModelId}#${safeLang}.mp3")
            if (legacyFile.exists() && legacyFile.length() > 0) {
                Log.i("ElevenLabsTtsProvider", "Cache hit (legacy model ID): ${legacyFile.name}")
                return legacyFile
            }
            
            // 2. Try legacy model ID + "auto" language fallback
            if (safeLang != "auto") {
                val legacyAutoFile = File(tgtDir, "tts_eleven#${base64Text}#${safeVoiceId}#${legacyModelId}#auto.mp3")
                if (legacyAutoFile.exists() && legacyAutoFile.length() > 0) {
                    Log.i("ElevenLabsTtsProvider", "Cache hit (legacy model + auto lang): ${legacyAutoFile.name}")
                    return legacyAutoFile
                }
            }
        }

        // 3. Try current model ID + "auto" language fallback
        if (safeLang != "auto") {
            val autoFile = File(tgtDir, "tts_eleven#${base64Text}#${safeVoiceId}#${safeModelId}#auto.mp3")
            if (autoFile.exists() && autoFile.length() > 0) {
                Log.i("ElevenLabsTtsProvider", "Cache hit (auto lang fallback): ${autoFile.name}")
                return autoFile
            }
        }

        // 4. DEEP SCAN: Look for ANY file with matching text hash and voice ID
        try {
            val prefix = "tts_eleven#${base64Text}#${safeVoiceId}#"
            val files = tgtDir.listFiles()
            val deepMatch = files?.find { it.name.startsWith(prefix) && it.length() > 0 }
            if (deepMatch != null) {
                Log.i("ElevenLabsTtsProvider", "Cache hit (DEEP SCAN match): ${deepMatch.name}")
                return deepMatch
            }
        } catch (e: Exception) {
            Log.e("ElevenLabsTtsProvider", "Deep scan failed: ${e.message}")
        }

        Log.i("ElevenLabsTtsProvider", "Cache miss: ${file.name} (even after deep scan)")
        return file
    }

    private fun getIsoLanguageCode(languageTag: String?): String? {
        if (languageTag.isNullOrEmpty() || languageTag.equals("default", ignoreCase = true) || languageTag == "Basis (System)") return null
        // Normalize underscores to hyphens for Locale parser, then extract native base language
        val normalizedTag = languageTag.replace("_", "-")
        val code = Locale.forLanguageTag(normalizedTag).language
        return code.takeIf { it.length == 2 }
    }

    init {
        migrateCacheToFilesDir()
        // Only fetch voices immediately if an API key is already configured.
        // Otherwise, the flow below will trigger fetchVoices() as soon as one is set.
        if (!cloudSettings.elevenLabsApiKey.isNullOrEmpty()) {
            fetchVoices()
        }
        
        scope.launch {
            cloudSettings.elevenLabsApiKeyFlow.collect { key ->
                if (!key.isNullOrEmpty()) {
                    fetchVoices()
                }
            }
        }
    }

    private fun migrateCacheToFilesDir() {
        val oldDir = File(context.cacheDir, "elevenlabs")
        val newDir = File(context.filesDir, "elevenlabs")

        if (oldDir.exists() && oldDir.isDirectory) {
            Log.i("ElevenLabsTtsProvider", "Starting migration of ElevenLabs cache to filesDir...")
            if (!newDir.exists()) newDir.mkdirs()

            val files = oldDir.listFiles()
            if (files != null) {
                var count = 0
                for (file in files) {
                    val destFile = File(newDir, file.name)
                    if (!destFile.exists()) {
                        if (file.renameTo(destFile)) {
                            count++
                        }
                    } else {
                        file.delete()
                    }
                }
                Log.i("ElevenLabsTtsProvider", "Migrated $count files to filesDir")
            }
            oldDir.delete()
        }
    }

    private val _availableVoicesFlow = MutableStateFlow<List<TtsVoice>>(emptyList())
    override val availableVoicesFlow: StateFlow<List<TtsVoice>> = _availableVoicesFlow.asStateFlow()

    private var availableVoices: List<TtsVoice> = emptyList()

    private fun fetchVoices() {
        val apiKey = cloudSettings.elevenLabsApiKey
        Log.i("ElevenLabsTtsProvider", "fetchVoices() called. Key present: ${!apiKey.isNullOrEmpty()}")
        if (apiKey.isNullOrEmpty()) {
            Log.w("ElevenLabsTtsProvider", "fetchVoices aborted: API Key is null or empty")
            return
        }

        val request = Request.Builder()
            .url("https://api.elevenlabs.io/v1/voices")
            .header("xi-api-key", apiKey)
            .tag(VOICES_TAG)
            .build()

        cancelCallsByTag(VOICES_TAG)

        Log.i("ElevenLabsTtsProvider", "Sending request to ElevenLabs voices API...")
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (e.message?.contains("Canceled") == true || e.message?.contains("Socket closed") == true) {
                    Log.d("ElevenLabsTtsProvider", "Voices fetch canceled - ignoring to allow newer request to proceed")
                    return
                } else {
                    Log.e("ElevenLabsTtsProvider", "Error fetching voices: ${e.message}", e)
                }
                applyFallbackVoices()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    val body = resp.body.string()
                    Log.i("ElevenLabsTtsProvider", "Received response: ${resp.code}. Body length: ${body.length}")
                    
                    if (!resp.isSuccessful) {
                        val errorMsg = "Unsuccessful response or empty body: ${resp.code}. Body: $body"
                        Log.e("ElevenLabsTtsProvider", errorMsg)
                        applyFallbackVoices()
                        return
                    }

                    try {
                        val voices = mutableListOf<TtsVoice>()
                        val json = JSONObject(body)
                        Log.i("ElevenLabsTtsProvider", "JSON Root Keys: ${json.keys().asSequence().toList()}")
                        val voicesArray = json.optJSONArray("voices")
                        
                        if (voicesArray != null) {
                            for (i in 0 until voicesArray.length()) {
                                val voiceObj = voicesArray.getJSONObject(i)
                                val id = voiceObj.optString("voice_id")
                                val name = voiceObj.optString("name")
                                
                                if (id.isNotEmpty() && name.isNotEmpty()) {
                                    voices.add(TtsVoice(
                                        id = id,
                                        name = name,
                                        locale = Locale.US, 
                                        isNetworkRequired = true,
                                        quality = TtsVoice.QUALITY_VERY_HIGH,
                                        provider = "elevenlabs"
                                    ))
                                }
                            }
                        } else {
                            Log.e("ElevenLabsTtsProvider", "No 'voices' array found in response")
                        }

                        if (voices.isEmpty()) {
                            Log.w("ElevenLabsTtsProvider", "Parsed 0 voices from ElevenLabs response.")
                            applyFallbackVoices()
                        } else {
                            availableVoices = voices
                            _availableVoicesFlow.value = voices
                            Log.i("ElevenLabsTtsProvider", "Fetched ${voices.size} voices from ElevenLabs")
                        }
                    } catch (e: Exception) {
                        Log.e("ElevenLabsTtsProvider", "Error parsing voices: ${e.message}", e)
                        applyFallbackVoices()
                    }
                }
            }
        })
    }
    
    private fun applyFallbackVoices() {
        val fallback = listOf(TtsVoice(
            id = DEFAULT_VOICE_ID,
            name = "Rachel (Standard)",
            locale = Locale.US,
            isNetworkRequired = true,
            quality = TtsVoice.QUALITY_VERY_HIGH,
            provider = "elevenlabs"
        ))
        availableVoices = fallback
        _availableVoicesFlow.value = fallback
    }

    override fun stopAll() {
        cancelCallsByTag(SPEECH_TAG)
        routedAudioPlayer.stopAll()
    }

    override fun isSpeaking(): Boolean {
        return routedAudioPlayer.isPlaying()
    }

    override fun shutdown() {

        stopAll()
    }

    private fun cancelCallsByTag(tag: String) {
        httpClient.dispatcher.queuedCalls().forEach { if (it.request().tag() == tag) it.cancel() }
        httpClient.dispatcher.runningCalls().forEach { if (it.request().tag() == tag) it.cancel() }
    }

    override fun setLanguageAndVoice(languageTag: String?, voiceName: String?) {
        setVoiceInternal(voiceName)
    }

    override fun setVoice(voiceName: String?) {
        setVoiceInternal(voiceName)
    }

    private fun setVoiceInternal(voiceName: String?) {
        if (!voiceName.isNullOrEmpty()) {
            // Validate ElevenLabs ID format (typically 20 chars alphanumeric)
            // If it's a legacy display name like "Rachel (Standard)", fallback to default ID
            if (voiceName.length == 20 && !voiceName.contains(" ")) {
                currentVoiceId = voiceName
            } else {
                currentVoiceId = DEFAULT_VOICE_ID
            }
            isInitialized = true
            _isReadyFlow.value = true
            Log.i("ElevenLabsTtsProvider", "Voice set to: $currentVoiceId (Initialized: $isInitialized)")
        }
    }

    override fun getAvailableLanguages(): List<Locale> {
        return listOf(Locale.GERMANY, Locale.US, Locale.UK)
    }

    override fun getAvailableVoices(languageTag: String?): List<TtsVoice> {
        return availableVoices
    }

    companion object {
        private const val DEFAULT_VOICE_ID = "pNInz6obpgDQGcFmaJgB"
    }
}

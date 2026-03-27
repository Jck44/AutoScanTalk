package com.andreas_kratzer.ghosttalk.core.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import com.andreas_kratzer.ghosttalk.core.audio.RoutedAudioPlayer
import com.andreas_kratzer.ghosttalk.core.settings.CloudSettings
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import com.andreas_kratzer.ghosttalk.core.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ElevenLabsTtsProvider @Inject constructor(
    private val context: Context,
    private val cloudSettings: CloudSettings,
    private val routedAudioPlayer: RoutedAudioPlayer,
    @param:ApplicationScope private val scope: CoroutineScope
) : TtsProvider {

    private val httpClient = OkHttpClient()
    private val handler = Handler(Looper.getMainLooper())
    private val playRequests = ConcurrentHashMap<String, File>()
    
    // Default voice ID if none is selected
    private var currentVoiceId: String = DEFAULT_VOICE_ID // Adam

    private val SPEECH_TAG = "elevenlabs_speech"
    private val VOICES_TAG = "elevenlabs_voices"

    override val isReady: Boolean get() = true // API-based, always "ready" if network is up

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
        val apiKey = cloudSettings.elevenLabsApiKey
        if (apiKey.isNullOrEmpty()) {
            Log.e("ElevenLabsTtsProvider", "API Key missing")
            onError?.invoke("API Key missing")
            onDone?.invoke()
            return
        }

        if (queueMode == TextToSpeech.QUEUE_FLUSH) {
            stopAll()
        }

        val requestBody = """
            {
                "text": "$text",
                "model_id": "eleven_multilingual_v2",
                "voice_settings": {
                    "stability": 0.5,
                    "similarity_boost": 0.75
                }
            }
        """.trimIndent().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://api.elevenlabs.io/v1/text-to-speech/$currentVoiceId")
            .addHeader("xi-api-key", apiKey)
            .post(requestBody)
            .tag(SPEECH_TAG)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                if (e.message?.contains("Canceled") == true || e.message?.contains("Socket closed") == true) {
                    Log.d("ElevenLabsTtsProvider", "Speech call canceled")
                } else {
                    Log.e("ElevenLabsTtsProvider", "API call failed: ${e.message}")
                    handler.post { 
                        onError?.invoke(e.message ?: "Network error")
                        onDone?.invoke() 
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
                            onDone?.invoke() 
                        }
                        return
                    }

                    val body = resp.body
                    if (body == null) {
                        handler.post { onDone?.invoke() }
                        return
                    }

                    try {
                        val tempFile = File(context.cacheDir, "elevenlabs_${System.currentTimeMillis()}.mp3")
                        FileOutputStream(tempFile).use { output ->
                            body.byteStream().copyTo(output)
                        }
                        Log.i("ElevenLabsTtsProvider", "Saved audio file size: ${tempFile.length()} bytes")

                        handler.post {
                            routedAudioPlayer.playAudioFile(tempFile, deviceAddress) {
                                tempFile.delete()
                                onDone?.invoke()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ElevenLabsTtsProvider", "Error saving/playing audio: ${e.message}")
                        handler.post { onDone?.invoke() }
                    }
                }
            }
        })
    }

    init {
        // Fetch voices immediately on initialization
        fetchVoices()
        
        scope.launch {
            cloudSettings.elevenLabsApiKeyFlow.collect { key ->
                if (!key.isNullOrEmpty()) {
                    fetchVoices()
                }
            }
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
                    val body = resp.body?.string()
                    Log.i("ElevenLabsTtsProvider", "Received response: ${resp.code}. Body length: ${body?.length ?: 0}")
                    
                    if (!resp.isSuccessful || body == null) {
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

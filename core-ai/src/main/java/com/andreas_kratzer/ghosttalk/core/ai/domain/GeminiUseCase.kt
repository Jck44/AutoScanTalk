package com.andreas_kratzer.ghosttalk.core.ai.domain

import android.graphics.Bitmap
import android.util.Base64
import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection

/**
 * UseCase for interacting with Gemini AI.
 * Reuses the app's OAuth token for authentication.
 */
@Singleton
open class GeminiUseCase @Inject constructor(
    private val googleAuthManager: GoogleAuthManager,
    private val logger: com.andreas_kratzer.ghosttalk.core.util.Logger,
    private val aiTools: Set<@JvmSuppressWildcards AiTool>,
    private val settingsRepository: SettingsRepository
) {
    private val requestMutex = Mutex()
    private var lastRequestEndTime = 0L

    private val oauthTokenProvider: suspend () -> String? = {
        googleAuthManager.getGoogleCredential()?.getToken()
    }

    private val driveProvider: suspend () -> Drive? = {
        val credential = googleAuthManager.getGoogleCredential()
        if (credential == null) {
            null
        } else {
            Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance()
            ) { request ->
                credential.initialize(request)
                request.connectTimeout = 3 * 60 * 1000 // 3 minutes
                request.readTimeout = 3 * 60 * 1000    // 3 minutes
            }.setApplicationName("GhosTTalk").build()
        }
    }

    enum class ToolStatus {
        AVAILABLE,
        REQUIRES_AUTH,
        FAILED,
        PENDING
    }
    
    companion object {
        private const val TAG = "GeminiUseCase"
        private var activeModelName = "gemini-flash-lite-latest" 
        private const val BASE_URL_TEMPLATE = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent"
        private const val LIST_MODELS_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val MIN_REQUEST_INTERVAL_MS = 1000L
        private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 Hours

        internal var lastSuccess: Boolean? = null // null: unknown, true: success, false: failed
        internal var lockoutUntilTime: Long = 0
        private var cachedModelsJson: String? = null
        private var lastModelsFetchTime: Long = 0L

        internal fun resetHealthStateForTesting() {
            lastSuccess = null
            lockoutUntilTime = 0
            activeModelName = "gemini-flash-lite-latest"
            cachedModelsJson = null
            lastModelsFetchTime = 0L
        }
    }
    private var appCommandHandler: ((String, Map<String, String>) -> Unit)? = null

    fun setAppCommandHandler(handler: (String, Map<String, String>) -> Unit) {
        this.appCommandHandler = handler
    }

    suspend fun generateResponse(
        prompt: String, 
        useGoogleSearch: Boolean = false,
        image: Bitmap? = null
    ): String = withContext(Dispatchers.IO) {
        requestMutex.withLock {
            val useApiKey = settingsRepository.useGeminiApiKey
            val apiKey = if (useApiKey) settingsRepository.geminiApiKey else null
            val token = if (useApiKey && !apiKey.isNullOrBlank()) {
                ""
            } else {
                oauthTokenProvider() ?: return@withLock "Fehler: Nicht angemeldet (OAuth Token fehlt)."
            }

            val now = System.currentTimeMillis()
            
            // 1. Check for 429 lockout
            if (now < lockoutUntilTime) {
                val remainingSeconds = ((lockoutUntilTime - now) / 1000).coerceAtLeast(1)
                throw Exception("HTTP 429: Lockout active. Please wait $remainingSeconds seconds.")
            }

            // 2. Enforce minimum inter-request interval to prevent burst limits
            val timeSinceLast = now - lastRequestEndTime
            if (timeSinceLast < MIN_REQUEST_INTERVAL_MS) {
                val waitTime = MIN_REQUEST_INTERVAL_MS - timeSinceLast
                logger.d(TAG, "Throttling active: Waiting ${waitTime}ms before next request.")
                delay(waitTime)
            }

            logger.d(TAG, "Generating response for prompt: $prompt, useGoogleSearch: $useGoogleSearch, image: ${image != null}")

            try {
                val result = performGeneration(token, prompt, useGoogleSearch, image)
                lastSuccess = true
                lastRequestEndTime = System.currentTimeMillis()
                return@withLock result
            } catch (e: Exception) {
                lastSuccess = false
                val errorMsg = e.message ?: ""
                if (errorMsg.contains("404")) {
                    logger.w(TAG, "Model $activeModelName failed (Error: $errorMsg), attempting to find alternative...")
                    
                    // Clear cache on 404 to ensure we have the latest list
                    cachedModelsJson = null

                    val failedModel = activeModelName
                    if (tryToSelectBestModel(excludeName = failedModel)) {
                        try {
                            val result = performGeneration(token, prompt, useGoogleSearch, image)
                            lastSuccess = true
                            lastRequestEndTime = System.currentTimeMillis()
                            return@withLock result
                        } catch (retryEx: Exception) {
                            lastSuccess = false
                            logger.e(TAG, "Retry with fallback model $activeModelName failed: ${retryEx.message}")
                        }
                    }
                }
                logger.e(TAG, "Gemini call failed: ${e.message}", e)
                lastRequestEndTime = System.currentTimeMillis()
                throw e
            }
        }
    }

    internal suspend fun tryToSelectBestModel(excludeName: String? = null): Boolean {
        try {
            val modelsJson = listModels()
            val modelsRoot = JSONObject(modelsJson)
            val modelsArray = modelsRoot.getJSONArray("models")
            
            val candidates = mutableListOf<String>()
            val debugInfo = StringBuilder("Available models metadata:\n")
            
            for (i in 0 until modelsArray.length()) {
                val model = modelsArray.getJSONObject(i)
                val name = model.getString("name").removePrefix("models/")
                
                // Collect metadata for logging
                val inputLimit = model.optInt("inputTokenLimit", -1)
                val outputLimit = model.optInt("outputTokenLimit", -1)
                val methods = model.optJSONArray("supportedGenerationMethods") ?: JSONArray()
                
                debugInfo.append("- $name: inputLimit=$inputLimit, outputLimit=$outputLimit, methods=$methods\n")

                if (name == excludeName) continue
                
                var canGenerate = false
                for (j in 0 until methods.length()) {
                    if (methods.getString(j) == "generateContent") canGenerate = true
                }
                
                if (canGenerate && !name.contains("vision") && !name.contains("embedding") && !name.contains("aqa")) {
                    candidates.add(name)
                }
            }
            logger.d(TAG, debugInfo.toString())

            val bestModel = candidates
                .filter { it.contains("flash") }
                .sortedWith(compareByDescending<String> { !it.contains("exp") }
                    .thenByDescending { it.contains("3.1") }
                    .thenByDescending { it.contains("lite") }
                    .thenByDescending { it.contains("1.5") }
                    .thenByDescending { it })
                .firstOrNull() 
                ?: candidates.sortedWith(compareByDescending<String> { !it.contains("exp") }
                    .thenByDescending { it })
                .firstOrNull()

            if (bestModel != null && bestModel != activeModelName) {
                activeModelName = bestModel
                return true
            }
        } catch (ex: Exception) {
            logger.e(TAG, "Failed to find best model", ex)
        }
        return false
    }

    private suspend fun performGeneration(token: String, prompt: String, useGoogleSearch: Boolean, image: Bitmap?): String {
        val initialContents = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", prompt)
                    })
                    image?.let {
                        val stream = ByteArrayOutputStream()
                        it.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                        val base64Image = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                        put(JSONObject().apply {
                            put("inline_data", JSONObject().apply {
                                put("mime_type", "image/jpeg")
                                put("data", base64Image)
                            })
                        })
                    }
                })
            })
        }
        val tools = createToolsArray(useGoogleSearch)
        
        var responseJson: String
        
        // Use a mutable JSONArray for contents to allow adding model responses and tool outputs
        val contentsHistory = initialContents
        
        for (turn in 1..8) { // Increased turns for recursive tool usage
            val requestJson = JSONObject().apply {
                put("contents", contentsHistory)
                if (tools.length() > 0) {
                    put("tools", tools)
                }
            }
            
            responseJson = callGeminiRest(token, requestJson)
            val root = JSONObject(responseJson)
            val candidates = root.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) return "Keine Antwort erhalten."
            
            val candidate = candidates.getJSONObject(0)
            val content = candidate.optJSONObject("content") ?: return "Keine Antwort erhalten."
            val parts = content.optJSONArray("parts") ?: return "Keine Antwort erhalten."
            
            // Add the model's response to history
            contentsHistory.put(content)
            
            var hasFunctionCall = false
            val functionResponseParts = JSONArray()
            
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                if (part.has("functionCall")) {
                    hasFunctionCall = true
                    val call = part.getJSONObject("functionCall")
                    val result = handleFunctionCall(token, call)
                    
                    functionResponseParts.put(JSONObject().apply {
                        put("functionResponse", JSONObject().apply {
                            put("name", call.getString("name"))
                            put("response", JSONObject().apply {
                                put("content", result)
                            })
                        })
                    })
                }
            }
            
            if (hasFunctionCall) {
                // Add all function responses as a single content turn from 'user' (per API specs for tool use)
                contentsHistory.put(JSONObject().apply {
                    put("role", "user")
                    put("parts", functionResponseParts)
                })
            } else {
                // Return text response if available
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    if (part.has("text")) {
                        return part.getString("text")
                    }
                }
                return "Keine Antwort erhalten."
            }
        }
        return "Fehler: Zu many interaction steps."
    }

    fun getAvailableTools(): List<AiTool> {
        return aiTools.toList()
    }

    fun getLocalCapabilities(): List<String> {
        // Nano currently has no tools, so we return an empty list or a description
        return emptyList()
    }

    private fun createToolsArray(useGoogleSearch: Boolean): JSONArray {
        val toolsArray = JSONArray()
        if (useGoogleSearch) {
            toolsArray.put(JSONObject().apply {
                put("googleSearch", JSONObject())
            })
        } else if (aiTools.isNotEmpty()) {
            toolsArray.put(JSONObject().apply {
                put("function_declarations", JSONArray().apply {
                    aiTools.forEach { tool ->
                        put(JSONObject().apply {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("parameters", tool.parameters)
                        })
                    }
                })
            })
        }
        return toolsArray
    }

    internal open suspend fun listModels(): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        cachedModelsJson?.let {
            if (now - lastModelsFetchTime < CACHE_TTL_MS) {
                logger.d(TAG, "Using cached models list.")
                return@withContext it
            }
        }

        val useApiKey = settingsRepository.useGeminiApiKey
        val apiKey = if (useApiKey) settingsRepository.geminiApiKey else null
        val url: URL
        val connection: HttpsURLConnection
        if (useApiKey && !apiKey.isNullOrBlank()) {
            url = URL("$LIST_MODELS_URL?key=$apiKey")
            connection = url.openConnection() as HttpsURLConnection
            connection.requestMethod = "GET"
        } else {
            val token = oauthTokenProvider() ?: return@withContext "Fehler: Kein Token."
            url = URL(LIST_MODELS_URL)
            connection = url.openConnection() as HttpsURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")
        }
        
        if (connection.responseCode == 200) {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            cachedModelsJson = response
            lastModelsFetchTime = System.currentTimeMillis()
            response
        } else {
            val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
            "Fehler beim Auflisten der Modelle (${connection.responseCode}): $error"
        }
    }

    private fun callGeminiRest(token: String, requestJson: JSONObject): String {
        val useApiKey = settingsRepository.useGeminiApiKey
        val apiKey = if (useApiKey) settingsRepository.geminiApiKey else null
        val url = if (useApiKey && !apiKey.isNullOrBlank()) {
            URL("${BASE_URL_TEMPLATE.format(activeModelName)}?key=$apiKey")
        } else {
            URL(BASE_URL_TEMPLATE.format(activeModelName))
        }
        val connection = url.openConnection() as HttpsURLConnection
        connection.requestMethod = "POST"
        if (!useApiKey || apiKey.isNullOrBlank()) {
            connection.setRequestProperty("Authorization", "Bearer $token")
        }
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true

        val requestString = requestJson.toString()
        logRequestPayload(requestString)

        connection.outputStream.use { it.write(requestString.toByteArray()) }

        val response = if (connection.responseCode == 200) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            val error = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            if (connection.responseCode == 429) {
                val waitSeconds = parseWaitTime(connection.getHeaderField("Retry-After"), error)
                val finalWait = waitSeconds.coerceIn(1, 3600)
                lockoutUntilTime = System.currentTimeMillis() + (finalWait * 1000)
                logger.w(TAG, "Gemini Quota Exceeded. Locking for ${finalWait}s. Error: $error")
            }
            throw Exception("HTTP ${connection.responseCode}: $error")
        }

        logger.d(TAG, "Incoming Gemini Response: $response")
        return response
    }

    private fun logRequestPayload(payload: String) {
        try {
            val json = JSONObject(payload)
            val contents = json.optJSONArray("contents")
            if (contents != null) {
                for (i in 0 until contents.length()) {
                    val content = contents.getJSONObject(i)
                    val parts = content.optJSONArray("parts")
                    if (parts != null) {
                        for (j in 0 until parts.length()) {
                            val part = parts.getJSONObject(j)
                            if (part.has("inline_data")) {
                                val inlineData = part.getJSONObject("inline_data")
                                inlineData.put("data", "[TRUNCATED IMAGE DATA]")
                            }
                        }
                    }
                }
            }
            logger.d(TAG, "Outgoing Gemini Request: $json")
        } catch (e: Exception) {
            logger.d(TAG, "Outgoing Gemini Request (raw): $payload")
        }
    }

    internal fun parseWaitTime(retryAfterHeader: String?, errorBody: String?): Long {
        // 1. Try Retry-After header
        retryAfterHeader?.toLongOrNull()?.let { return it }

        // 2. Try parsing from error message body: "Please retry in 30.34s"
        if (errorBody != null) {
            // Avoid misinterpreting "429" in the error body as seconds
            val regex = Regex("retry in (\\d+\\.?\\d*)s", RegexOption.IGNORE_CASE)
            val match = regex.find(errorBody)
            match?.groupValues?.get(1)?.toDoubleOrNull()?.let { return it.toLong() }
        }

        return 60 // Default fallback
    }

    private suspend fun handleFunctionCall(token: String, call: JSONObject): String {
        val name = call.getString("name")
        val argsObj = call.optJSONObject("args")
        val args = mutableMapOf<String, Any?>()
        argsObj?.keys()?.forEach { key ->
            args[key] = argsObj.get(key)
        }
        
        logger.d(TAG, "Executing tool: $name with args: $args")
        
        val tool = aiTools.find { it.name == name }
        if (tool == null) {
            return "Funktion nicht gefunden."
        }

        return try {
            tool.execute(args)
        } catch (e: Exception) {
            logger.e(TAG, "Tool $name execution failed", e)
            "[Fehler im Tool $name: ${e.message}. Fahre fort, falls möglich.]"
        }
    }

    fun getToolStatus(isUserSignedIn: Boolean): Map<String, ToolStatus> {
        val statusMap = mutableMapOf<String, ToolStatus>()
        
        val baseStatus = when (lastSuccess) {
            true -> ToolStatus.AVAILABLE
            false -> ToolStatus.FAILED
            null -> ToolStatus.PENDING
        }

        aiTools.forEach { tool ->
            if (tool.requiresAuth && !isUserSignedIn) {
                statusMap[tool.name] = ToolStatus.REQUIRES_AUTH
            } else {
                statusMap[tool.name] = baseStatus
            }
        }
        
        return statusMap
    }
}

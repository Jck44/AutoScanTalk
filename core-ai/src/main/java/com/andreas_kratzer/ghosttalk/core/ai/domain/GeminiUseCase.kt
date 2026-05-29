package com.andreas_kratzer.ghosttalk.core.ai.domain

import android.graphics.Bitmap
import android.util.Base64
import com.andreas_kratzer.ghosttalk.core.cloud.GoogleAuthManager
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URL
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection

/**
 * UseCase for interacting with Gemini AI.
 * Reuses the app's OAuth token for authentication.
 */
@Singleton
class GeminiUseCase @Inject constructor(
    private val googleAuthManager: GoogleAuthManager,
    private val logger: com.andreas_kratzer.ghosttalk.core.util.Logger,
    private val aiTools: Set<@JvmSuppressWildcards AiTool>,
    private val settingsRepository: SettingsRepository
) {
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
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("GhosTTalk").build()
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
        private var activeModelName = "gemini-2.0-flash" 
        private const val BASE_URL_TEMPLATE = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent"
        private const val LIST_MODELS_URL = "https://generativelanguage.googleapis.com/v1beta/models"

        internal var modelInitialized = false
        internal var lastSuccess: Boolean? = null // null: unknown, true: success, false: failed
        internal var lockoutUntilTime: Long = 0

        internal fun resetHealthStateForTesting() {
            modelInitialized = false
            lastSuccess = null
            lockoutUntilTime = 0
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
        val apiKey = settingsRepository.geminiApiKey
        val token = if (!apiKey.isNullOrBlank()) {
            ""
        } else {
            oauthTokenProvider() ?: return@withContext "Fehler: Nicht angemeldet (OAuth Token fehlt)."
        }
        
        val now = System.currentTimeMillis()
        if (now < lockoutUntilTime) {
            val remainingSeconds = ((lockoutUntilTime - now) / 1000).coerceAtLeast(1)
            throw Exception("HTTP 429: Lockout active. Please wait $remainingSeconds seconds.")
        }
        
        logger.d(TAG, "Generating response for prompt: $prompt, useGoogleSearch: $useGoogleSearch, image: ${image != null}")
        
        if (!modelInitialized) {
            tryToSelectBestModel()
            modelInitialized = true
        }
        
        try {
            val result = performGeneration(token, prompt, useGoogleSearch, image)
            lastSuccess = true
            return@withContext result
        } catch (e: Exception) {
            lastSuccess = false
            val errorMsg = e.message ?: ""
            if (errorMsg.contains("404") || errorMsg.contains("429")) {
                logger.w(TAG, "Model $activeModelName failed (Error: $errorMsg), attempting to find alternative...")
                val failedModel = activeModelName
                if (tryToSelectBestModel(excludeName = failedModel)) {
                    try {
                        val result = performGeneration(token, prompt, useGoogleSearch, image)
                        lastSuccess = true
                        return@withContext result
                    } catch (retryEx: Exception) {
                        lastSuccess = false
                        logger.e(TAG, "Retry with fallback model $activeModelName failed: ${retryEx.message}")
                    }
                }
            }
            logger.e(TAG, "Gemini call failed: ${e.message}", e)
            throw e
        }
    }

    private suspend fun tryToSelectBestModel(excludeName: String? = null): Boolean {
        try {
            val modelsJson = listModels()
            val modelsRoot = JSONObject(modelsJson)
            val modelsArray = modelsRoot.getJSONArray("models")
            val candidates = mutableListOf<String>()
            
            for (i in 0 until modelsArray.length()) {
                val model = modelsArray.getJSONObject(i)
                val name = model.getString("name").removePrefix("models/")
                if (name == excludeName) continue
                
                val methods = model.getJSONArray("supportedGenerationMethods")
                var canGenerate = false
                for (j in 0 until methods.length()) {
                    if (methods.getString(j) == "generateContent") canGenerate = true
                }
                
                if (canGenerate && !name.contains("vision") && !name.contains("embedding") && !name.contains("aqa")) {
                    candidates.add(name)
                }
            }
            
            val bestModel = candidates
                .filter { it.contains("flash") }
                .sortedDescending()
                .firstOrNull() 
                ?: candidates.sortedDescending().firstOrNull()

            if (bestModel != null && bestModel != activeModelName) {
                logger.d(TAG, "Selected model: $bestModel (failed/prev was $activeModelName)")
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
        return "Fehler: Zu viele Interaktionsschritte."
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

    suspend fun listModels(): String = withContext(Dispatchers.IO) {
        val apiKey = settingsRepository.geminiApiKey
        val url: URL
        val connection: HttpsURLConnection
        if (!apiKey.isNullOrBlank()) {
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
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
            "Fehler beim Auflisten der Modelle (${connection.responseCode}): $error"
        }
    }

    private fun callGeminiRest(token: String, requestJson: JSONObject): String {
        val apiKey = settingsRepository.geminiApiKey
        val url = if (!apiKey.isNullOrBlank()) {
            URL("${BASE_URL_TEMPLATE.format(activeModelName)}?key=$apiKey")
        } else {
            URL(BASE_URL_TEMPLATE.format(activeModelName))
        }
        val connection = url.openConnection() as HttpsURLConnection
        connection.requestMethod = "POST"
        if (apiKey.isNullOrBlank()) {
            connection.setRequestProperty("Authorization", "Bearer $token")
        }
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true

        connection.outputStream.use { it.write(requestJson.toString().toByteArray()) }

        return if (connection.responseCode == 200) {
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
    }

    internal fun parseWaitTime(retryAfterHeader: String?, errorBody: String?): Long {
        // 1. Try Retry-After header
        retryAfterHeader?.toLongOrNull()?.let { return it }

        // 2. Try parsing from error message body: "Please retry in 30.34s"
        if (errorBody != null) {
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

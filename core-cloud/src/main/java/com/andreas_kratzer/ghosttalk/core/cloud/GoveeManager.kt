package com.andreas_kratzer.ghosttalk.core.cloud

import com.andreas_kratzer.ghosttalk.core.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection

@Singleton
class GoveeManager @Inject constructor(
    private val logger: Logger
) {
    companion object {
        private const val TAG = "GoveeManager"
        private const val BASE_URL = "https://developer-api.govee.com/v1/devices/control"
    }

    suspend fun executeCommand(
        apiKey: String,
        deviceId: String,
        model: String,
        intent: String,
        value: String?
    ): Boolean = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || deviceId.isBlank()) {
            logger.w(TAG, "API Key or Device ID is blank.")
            return@withContext false
        }

        val url = URL(BASE_URL)
        try {
            val connection = url.openConnection() as HttpsURLConnection
            connection.requestMethod = "PUT"
            connection.setRequestProperty("Govee-API-Key", apiKey)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            // Govee API format: { "device": "...", "model": "...", "cmd": { "name": "turn", "value": "on" } }
            val payload = JSONObject().apply {
                put("device", deviceId)
                put("model", model)
                val cmd = JSONObject().apply {
                    when (intent) {
                        "action.on" -> {
                            put("name", "turn")
                            put("value", "on")
                        }
                        "action.off" -> {
                            put("name", "turn")
                            put("value", "off")
                        }
                        "action.brightness" -> {
                            put("name", "brightness")
                            put("value", value?.toIntOrNull() ?: 100)
                        }
                    }
                }
                put("cmd", cmd)
            }

            OutputStreamWriter(connection.outputStream).use { it.write(payload.toString()) }

            if (connection.responseCode == 200) {
                logger.d(TAG, "Govee command $intent successfully sent to $deviceId")
                return@withContext true
            } else {
                logger.e(TAG, "Govee command failed with code: ${connection.responseCode}")
            }
        } catch (e: Exception) {
            logger.e(TAG, "Error executing Govee command", e)
        }
        return@withContext false
    }
}

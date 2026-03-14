package com.andreas_kratzer.ghosttalk.core.cloud

import com.andreas_kratzer.ghosttalk.core.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection

@Singleton
class PhilipsHueManager @Inject constructor(
    private val logger: Logger
) {
    companion object {
        private const val TAG = "PhilipsHueManager"
        private const val REMOTE_API_URL = "https://api.meethue.com/bridge"
        private const val DISCOVERY_URL = "https://discovery.meethue.com"
    }

    /**
     * Discovers Philips Hue Bridges in the local network using N-UPnP.
     * Returns a list of IP addresses.
     */
    suspend fun discoverBridges(): List<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL(DISCOVERY_URL)
            val connection = url.openConnection() as HttpsURLConnection
            connection.requestMethod = "GET"
            
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(response)
            val ips = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                val bridge = jsonArray.getJSONObject(i)
                ips.add(bridge.getString("internalipaddress"))
            }
            return@withContext ips
        } catch (e: Exception) {
            logger.e(TAG, "Error discovering Hue bridges", e)
            return@withContext emptyList<String>()
        }
    }

    /**
     * Executes a command via Local Bridge API.
     */
    suspend fun executeLocalCommand(
        bridgeIp: String,
        username: String,
        lightId: String,
        intent: String,
        value: String?
    ): Boolean = withContext(Dispatchers.IO) {
        if (bridgeIp.isBlank() || username.isBlank() || lightId.isBlank()) {
            logger.w(TAG, "Bridge IP, Username, or Light ID is blank.")
            return@withContext false
        }

        val payload = createPayload(intent, value)
        val url = URL("http://$bridgeIp/api/$username/lights/$lightId/state")
        
        try {
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "PUT"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            OutputStreamWriter(connection.outputStream).use { it.write(payload.toString()) }

            if (connection.responseCode == 200) {
                logger.d(TAG, "Hue local command $intent successfully sent to $lightId")
                return@withContext true
            }
        } catch (e: Exception) {
            logger.e(TAG, "Error executing Hue local command", e)
        }
        return@withContext false
    }

    /**
     * Executes a command via Remote Hue API (Cloud).
     */
    suspend fun executeRemoteCommand(
        accessToken: String,
        bridgeId: String,
        lightId: String,
        intent: String,
        value: String?
    ): Boolean = withContext(Dispatchers.IO) {
        if (accessToken.isBlank() || bridgeId.isBlank()) {
            return@withContext false
        }

        val payload = createPayload(intent, value)
        val url = URL("$REMOTE_API_URL/$bridgeId/lights/$lightId/state")

        try {
            val connection = url.openConnection() as HttpsURLConnection
            connection.requestMethod = "PUT"
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            OutputStreamWriter(connection.outputStream).use { it.write(payload.toString()) }

            return@withContext connection.responseCode == 200
        } catch (e: Exception) {
            logger.e(TAG, "Error executing Hue remote command", e)
        }
        return@withContext false
    }

    private fun createPayload(intent: String, value: String?): JSONObject {
        return JSONObject().apply {
            when (intent) {
                "action.on" -> put("on", true)
                "action.off" -> put("on", false)
                "action.brightness" -> put("bri", value?.toIntOrNull() ?: 254)
            }
        }
    }
}

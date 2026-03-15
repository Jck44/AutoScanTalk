package com.andreas_kratzer.ghosttalk.core.cloud

import com.andreas_kratzer.ghosttalk.core.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection

/**
 * Data class representing a Google Home device.
 */
data class HomeDevice(
    val id: String,
    val name: String,
    val type: String,
    val traits: List<String>
)

/**
 * Manager for interacting with Google Home Device Access API.
 */
@Singleton
class GoogleHomeManager @Inject constructor(
    private val googleAuthManager: GoogleAuthManager,
    private val logger: Logger
) {
    private val oauthTokenProvider: suspend () -> String? = {
        // Note: getToken() should handle refresh if needed via GoogleAuthManager
        googleAuthManager.getGoogleCredential(listOf("https://www.googleapis.com/auth/sdm.service"))?.getToken()
    }

    companion object {
        private const val TAG = "GoogleHomeManager"
        private const val BASE_URL = "https://smartdevicemanagement.googleapis.com/v1"
        private const val LIST_DEVICES_URL_TEMPLATE = "$BASE_URL/enterprises/%s/devices"
        private const val EXECUTE_COMMAND_URL_TEMPLATE = "$BASE_URL/enterprises/%s/devices/%s:executeCommand"
    }

    /**
     * Lists all authorized devices from Google Home.
     */
    suspend fun listDevices(projectId: String): List<HomeDevice> = withContext(Dispatchers.IO) {
        if (projectId.isBlank()) {
            logger.w(TAG, "Project ID is blank. Cannot list devices.")
            return@withContext emptyList()
        }

        val token = oauthTokenProvider() ?: return@withContext emptyList()
        val url = URL(LIST_DEVICES_URL_TEMPLATE.format(projectId))
        
        try {
            val connection = url.openConnection() as HttpsURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            
            if (connection.responseCode == 200) {
                val json = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(json)
                val devicesArray = root.optJSONArray("devices") ?: JSONArray()
                val devices = mutableListOf<HomeDevice>()
                
                for (i in 0 until devicesArray.length()) {
                    val deviceJson = devicesArray.getJSONObject(i)
                    val namePath = deviceJson.getString("name") // enterprises/PROJECT_ID/devices/DEVICE_ID
                    val id = namePath.substringAfterLast("/")
                    val type = deviceJson.optString("type", "UNKNOWN")
                    val traitsJson = deviceJson.optJSONObject("traits") ?: JSONObject()
                    val traits = traitsJson.keys().asSequence().toList()
                    
                    val infoTrait = traitsJson.optJSONObject("sdm.devices.traits.Info")
                    val displayName = infoTrait?.optString("customName")
                        ?: infoTrait?.optString("name")
                        ?: "Device $id"
                    
                    devices.add(HomeDevice(id, displayName, type, traits))
                }
                return@withContext devices
            } else {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                logger.e(TAG, "Failed to list devices (${connection.responseCode}): $error")
            }
        } catch (e: Exception) {
            logger.e(TAG, "Error listing devices", e)
        }
        
        return@withContext emptyList()
    }

    /**
     * Executes a command on a specific device.
     */
    suspend fun executeCommand(
        projectId: String,
        deviceId: String,
        trait: String,
        command: String,
        params: Map<String, Any>
    ): Boolean = withContext(Dispatchers.IO) {
        if (projectId.isBlank() || deviceId.isBlank()) {
            logger.w(TAG, "Project ID or Device ID is blank. Cannot execute command.")
            return@withContext false
        }

        val token = oauthTokenProvider() ?: return@withContext false
        val url = URL(EXECUTE_COMMAND_URL_TEMPLATE.format(projectId, deviceId))
        
        try {
            val connection = url.openConnection() as HttpsURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            val payload = JSONObject().apply {
                put("command", command)
                put("params", JSONObject(params))
            }

            OutputStreamWriter(connection.outputStream).use { 
                it.write(payload.toString())
            }
            
            if (connection.responseCode == 200) {
                logger.d(TAG, "Command $command executed successfully on $deviceId") // Changed from logger.i to logger.d
                return@withContext true
            } else {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                logger.e(TAG, "Failed to execute command (${connection.responseCode}): $error")
            }
        } catch (e: Exception) {
            logger.e(TAG, "Error executing command", e)
        }
        
        return@withContext false
    }
}

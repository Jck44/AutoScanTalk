package com.andreas_kratzer.ghosttalk.core.cloud

import android.annotation.SuppressLint
import com.andreas_kratzer.ghosttalk.core.settings.SmartHomeSettings
import com.andreas_kratzer.ghosttalk.core.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.URL
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

@Singleton
class PhilipsHueManager @Inject constructor(
    private val logger: Logger,
    private val smartHomeSettings: SmartHomeSettings
) {
    companion object {
        private const val TAG = "PhilipsHueManager"
        private const val DISCOVERY_URL = "https://discovery.meethue.com"

        private fun getSha256Fingerprint(cert: X509Certificate): String {
            val md = MessageDigest.getInstance("SHA-256")
            val der = cert.encoded
            val digest = md.digest(der)
            return digest.joinToString(":") { String.format("%02X", it) }
        }
    }

    private val bypassHostnameVerifier = HostnameVerifier { _, _ -> true }

    // lgtm[java/insecure-trustmanager] - Intentional TOFU (Trust-On-First-Use) to fetch local bridge cert details
    @SuppressLint("TrustAllX509TrustManager", "CustomX509TrustManager")
    private class RecordingTrustManager : X509TrustManager {
        var acceptedCerts: Array<out X509Certificate>? = null
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            acceptedCerts = chain
        }
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    @SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
    private class FingerprintTrustManager(private val expectedFingerprint: String) : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            if (chain.isNullOrEmpty()) {
                throw CertificateException("Certificate chain is empty")
            }
            val serverCert = chain[0]
            val actualFingerprint = getSha256Fingerprint(serverCert)
            if (!actualFingerprint.equals(expectedFingerprint, ignoreCase = true)) {
                throw CertificateException(
                    "Certificate fingerprint mismatch! Expected: $expectedFingerprint, Got: $actualFingerprint"
                )
            }
        }
        
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    private fun createFingerprintSslSocketFactory(fingerprint: String): SSLSocketFactory {
        val trustManager = FingerprintTrustManager(fingerprint)
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), java.security.SecureRandom())
        return sslContext.socketFactory
    }

    private fun createHttpsConnection(urlStr: String, fingerprint: String): HttpsURLConnection {
        val url = URL(urlStr)
        val connection = url.openConnection() as HttpsURLConnection
        connection.sslSocketFactory = createFingerprintSslSocketFactory(fingerprint)
        connection.hostnameVerifier = bypassHostnameVerifier
        return connection
    }

    /**
     * Fetches the SSL certificate information of the local Hue Bridge at the given IP.
     * Uses a temporary trust-all manager to perform the handshake and extract details.
     */
    suspend fun fetchBridgeCertificateInfo(bridgeIp: String): BridgeCertificateInfo? = withContext(Dispatchers.IO) {
        if (bridgeIp.isBlank()) return@withContext null
        try {
            val trustManager = RecordingTrustManager()
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf(trustManager), java.security.SecureRandom())

            val url = URL("https://$bridgeIp/api/config")
            val connection = url.openConnection() as HttpsURLConnection
            connection.sslSocketFactory = sslContext.socketFactory
            connection.hostnameVerifier = bypassHostnameVerifier
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "GET"

            try {
                connection.connect()
                // Force connection to trigger handshake completely
                connection.responseCode
            } catch (e: Exception) {
                logger.d(TAG, "Exception during certificate fetch connection (expected if unauthorized): ${e.message}")
            } finally {
                connection.disconnect()
            }

            val certs = trustManager.acceptedCerts
            if (!certs.isNullOrEmpty()) {
                val cert = certs[0]
                return@withContext BridgeCertificateInfo(
                    subject = cert.subjectDN.name,
                    issuer = cert.issuerDN.name,
                    validFrom = cert.notBefore.toString(),
                    validTo = cert.notAfter.toString(),
                    fingerprint = getSha256Fingerprint(cert)
                )
            }
        } catch (e: Exception) {
            logger.e(TAG, "Error fetching bridge certificate info for $bridgeIp", e)
        }
        return@withContext null
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
     * Registers a new user/API key on the local bridge using HTTPS.
     * Must be called while the link button on the bridge is pressed.
     */
    suspend fun registerLocalUser(bridgeIp: String): String? = withContext(Dispatchers.IO) {
        if (bridgeIp.isBlank()) return@withContext null
        val fingerprint = smartHomeSettings.hueBridgeFingerprint
        if (fingerprint.isBlank()) {
            logger.w(TAG, "Cannot register user: Hue bridge fingerprint is empty.")
            return@withContext null
        }
        try {
            val connection = createHttpsConnection("https://$bridgeIp/api", fingerprint)
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            
            val payload = JSONObject().apply {
                put("devicetype", "ghosttalk#app")
            }
            
            OutputStreamWriter(connection.outputStream).use { it.write(payload.toString()) }
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(response)
                if (jsonArray.length() > 0) {
                    val firstObj = jsonArray.getJSONObject(0)
                    if (firstObj.has("success")) {
                        val successObj = firstObj.getJSONObject("success")
                        return@withContext successObj.getString("username")
                    } else if (firstObj.has("error")) {
                        val errorObj = firstObj.getJSONObject("error")
                        logger.d(TAG, "Registration status: ${errorObj.optString("description")}")
                    }
                }
            }
        } catch (e: Exception) {
            logger.e(TAG, "Error registering local user", e)
        }
        return@withContext null
    }

    /**
     * Fetches the list of lights connected to the local bridge using HTTPS.
     */
    suspend fun getLocalLights(bridgeIp: String, username: String): List<HomeDevice> = withContext(Dispatchers.IO) {
        if (bridgeIp.isBlank() || username.isBlank()) return@withContext emptyList()
        val fingerprint = smartHomeSettings.hueBridgeFingerprint
        if (fingerprint.isBlank()) {
            logger.w(TAG, "Cannot get local lights: Hue bridge fingerprint is empty.")
            return@withContext emptyList()
        }
        try {
            val connection = createHttpsConnection("https://$bridgeIp/api/$username/lights", fingerprint)
            connection.requestMethod = "GET"
            connection.setRequestProperty("Content-Type", "application/json")
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                if (response.trim().startsWith("[")) {
                    val array = JSONArray(response)
                    if (array.length() > 0 && array.getJSONObject(0).has("error")) {
                        logger.w(TAG, "Failed to get lights: ${array.getJSONObject(0).getJSONObject("error").optString("description")}")
                        return@withContext emptyList()
                    }
                }
                val jsonObject = JSONObject(response)
                val devices = mutableListOf<HomeDevice>()
                val keys = jsonObject.keys()
                while (keys.hasNext()) {
                    val id = keys.next()
                    val lightObj = jsonObject.getJSONObject(id)
                    val name = lightObj.getString("name")
                    devices.add(HomeDevice(id = id, name = name, type = "LIGHT"))
                }
                return@withContext devices
            }
        } catch (e: Exception) {
            logger.e(TAG, "Error getting local lights", e)
        }
        return@withContext emptyList()
    }

    /**
     * Executes a command via Local Bridge API using HTTPS.
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

        val fingerprint = smartHomeSettings.hueBridgeFingerprint
        if (fingerprint.isBlank()) {
            logger.w(TAG, "Cannot execute local command: Hue bridge fingerprint is empty.")
            return@withContext false
        }

        val payload = createPayload(intent, value)
        val urlStr = "https://$bridgeIp/api/$username/lights/$lightId/state"
        
        try {
            val connection = createHttpsConnection(urlStr, fingerprint)
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

    private fun createPayload(intent: String, value: String?): JSONObject {
        return JSONObject().apply {
            // Automatically turn on the light when adjusting brightness or color
            when (intent) {
                "action.on" -> put("on", true)
                "action.off" -> put("on", false)
                "action.brightness" -> {
                    put("on", true)
                    val pct = value?.toIntOrNull() ?: 100
                    val bri = ((pct.coerceIn(0, 100) / 100.0) * 254.0).toInt().coerceIn(1, 254)
                    put("bri", bri)
                }
                "action.color" -> {
                    put("on", true)
                    val (x, y) = when (value?.lowercase()) {
                        "rot", "red", "#ff0000" -> Pair(0.675, 0.322)
                        "grün", "green", "#00ff00" -> Pair(0.409, 0.518)
                        "blau", "blue", "#0000ff" -> Pair(0.167, 0.04)
                        "gelb", "yellow", "#ffff00" -> Pair(0.4317, 0.4996)
                        "orange", "#ffa500" -> Pair(0.5562, 0.4084)
                        "pink", "rosa", "#ffc0cb" -> Pair(0.3787, 0.1724)
                        "lila", "violett", "purple", "#800080" -> Pair(0.2727, 0.1133)
                        "warmweiß", "warm white" -> Pair(0.4573, 0.41)
                        "kaltweiß", "cold white" -> Pair(0.313, 0.329)
                        else -> Pair(0.4573, 0.41) // Default warm white
                    }
                    put("xy", JSONArray().apply {
                        put(x)
                        put(y)
                    })
                }
            }
        }
    }
}



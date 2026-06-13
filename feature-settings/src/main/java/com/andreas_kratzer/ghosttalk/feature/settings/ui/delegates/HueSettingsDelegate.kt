package com.andreas_kratzer.ghosttalk.feature.settings.ui.delegates

import android.content.Context
import android.widget.Toast
import com.andreas_kratzer.ghosttalk.core.cloud.BridgeCertificateInfo
import com.andreas_kratzer.ghosttalk.core.cloud.PhilipsHueManager
import com.andreas_kratzer.ghosttalk.core.data.SettingsRepository
import com.andreas_kratzer.ghosttalk.feature.settings.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class HueSettingsDelegate @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val hueManager: PhilipsHueManager
) {
    private val _huePairingStatus = MutableStateFlow<String?>(null)
    val huePairingStatus: StateFlow<String?> = _huePairingStatus.asStateFlow()
    
    private val _pendingCertificateInfo = MutableStateFlow<BridgeCertificateInfo?>(null)
    val pendingCertificateInfo: StateFlow<BridgeCertificateInfo?> = _pendingCertificateInfo.asStateFlow()
    
    private val _isUpdatingHueCache = MutableStateFlow(false)
    val isUpdatingHueCache: StateFlow<Boolean> = _isUpdatingHueCache.asStateFlow()

    private var scope: CoroutineScope? = null

    fun initialize(scope: CoroutineScope) {
        this.scope = scope
    }

    private fun viewModelScopeLaunch(block: suspend CoroutineScope.() -> Unit) {
        val activeScope = checkNotNull(scope) { "HueSettingsDelegate scope has not been initialized. Call initialize(scope) first." }
        activeScope.launch {
            block()
        }
    }

    fun discoverHueBridges() {
        viewModelScopeLaunch {
            Toast.makeText(context, context.getString(R.string.hue_toast_searching), Toast.LENGTH_SHORT).show()
            val bridges = hueManager.discoverBridges()
            if (bridges.isNotEmpty()) {
                settingsRepository.hueBridgeIp = bridges.first()
                Toast.makeText(context, context.getString(R.string.hue_toast_bridge_found, bridges.first()), Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, context.getString(R.string.hue_toast_no_bridge_found), Toast.LENGTH_LONG).show()
            }
        }
    }

    fun registerLocalHueBridge() {
        val ip = settingsRepository.hueBridgeIp
        if (ip.isBlank()) {
            Toast.makeText(context, context.getString(R.string.hue_toast_missing_ip), Toast.LENGTH_LONG).show()
            return
        }
        viewModelScopeLaunch {
            _huePairingStatus.value = "Zertifikat wird abgefragt (HTTPS)..."
            val certInfo = hueManager.fetchBridgeCertificateInfo(ip)
            if (certInfo == null) {
                _huePairingStatus.value = "Zertifikatsabfrage fehlgeschlagen."
                Toast.makeText(context, context.getString(R.string.hue_toast_certificate_error), Toast.LENGTH_LONG).show()
                return@viewModelScopeLaunch
            }

            val storedFingerprint = settingsRepository.hueBridgeFingerprint
            if (storedFingerprint.isBlank() || !storedFingerprint.equals(certInfo.fingerprint, ignoreCase = true)) {
                _pendingCertificateInfo.value = certInfo
                _huePairingStatus.value = "Zertifikatsfreigabe erforderlich."
            } else {
                proceedWithPairing(ip)
            }
        }
    }

    fun confirmHueBridgeCertificate() {
        val certInfo = _pendingCertificateInfo.value ?: return
        val ip = settingsRepository.hueBridgeIp
        settingsRepository.hueBridgeFingerprint = certInfo.fingerprint
        _pendingCertificateInfo.value = null
        viewModelScopeLaunch {
            proceedWithPairing(ip)
        }
    }

    fun cancelHueBridgeCertificate() {
        _pendingCertificateInfo.value = null
        _huePairingStatus.value = "Kopplung abgebrochen."
    }

    fun refreshHueDevicesCache(silentOnFailure: Boolean = false, onResult: ((Boolean) -> Unit)? = null) {
        val ip = settingsRepository.hueBridgeIp
        val username = settingsRepository.hueUsername
        if (ip.isBlank() || username.isBlank()) {
            if (!silentOnFailure) {
                Toast.makeText(context, context.getString(R.string.hue_toast_not_paired), Toast.LENGTH_LONG).show()
            }
            onResult?.invoke(false)
            return
        }

        viewModelScopeLaunch {
            _isUpdatingHueCache.value = true
            val fetchedDevices = hueManager.getLocalLights(ip, username)
            if (fetchedDevices.isNotEmpty()) {
                val array = org.json.JSONArray()
                fetchedDevices.forEach { device ->
                    val obj = org.json.JSONObject().apply {
                        put("id", device.id)
                        put("name", device.name)
                        put("type", device.type)
                    }
                    array.put(obj)
                }
                settingsRepository.hueCachedDevices = array.toString()
                Toast.makeText(context, context.getString(R.string.hue_toast_devices_cached, fetchedDevices.size), Toast.LENGTH_LONG).show()
                onResult?.invoke(true)
            } else {
                if (!silentOnFailure) {
                    Toast.makeText(context, context.getString(R.string.hue_toast_bridge_unreachable), Toast.LENGTH_LONG).show()
                }
                onResult?.invoke(false)
            }
            _isUpdatingHueCache.value = false
        }
    }

    private suspend fun proceedWithPairing(ip: String) {
        _huePairingStatus.value = context.getString(R.string.hue_pairing_press_link_button)
        Toast.makeText(context, context.getString(R.string.hue_toast_cert_accepted_press_button), Toast.LENGTH_LONG).show()
        var success = false
        val maxRetries = 15 // 30 seconds
        for (i in 1..maxRetries) {
            val username = hueManager.registerLocalUser(ip)
            if (username != null) {
                settingsRepository.hueUsername = username
                _huePairingStatus.value = "Erfolgreich gekoppelt!"
                Toast.makeText(context, context.getString(R.string.hue_toast_pairing_success), Toast.LENGTH_LONG).show()
                success = true
                break
            }
            _huePairingStatus.value = "Warte auf Knopfdruck... (Versuch $i von $maxRetries)"
            delay(2000)
        }
        if (!success) {
            _huePairingStatus.value = "Kopplung fehlgeschlagen. Zeitüberschreitung."
            Toast.makeText(context, context.getString(R.string.hue_toast_pairing_failed), Toast.LENGTH_LONG).show()
        } else {
            delay(3000)
            _huePairingStatus.value = null
        }
    }
}

package com.andreas_kratzer.ghosttalk.feature.settings.ui.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.core.model.SmartHomeProvider
import com.andreas_kratzer.ghosttalk.core.ui.components.PreferenceCategory
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsEditTextItem
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.R
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsViewModel

@Composable
fun SmartHomeSettingsSection(
    viewModel: SettingsViewModel
) {
    val dimensions = LocalDimensions.current
    
    val hueBridgeIp by viewModel.hueBridgeIp.collectAsState()
    val hueUsername by viewModel.hueUsername.collectAsState()
    val huePairingStatus by viewModel.huePairingStatus.collectAsState()
    val pendingCertInfo by viewModel.pendingCertificateInfo.collectAsState()
    val hueCachedDevices by viewModel.hueCachedDevices.collectAsState()
    val isUpdatingHueCache by viewModel.isUpdatingHueCache.collectAsState()

    val cachedCount = androidx.compose.runtime.remember(hueCachedDevices) {
        if (hueCachedDevices.isBlank()) 0 else {
            try {
                org.json.JSONArray(hueCachedDevices).length()
            } catch (_: Exception) {
                0
            }
        }
    }

    pendingCertInfo?.let { cert ->
        com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkDialog(
            title = "SSL-Zertifikat verifizieren",
            onDismiss = { viewModel.cancelHueBridgeCertificate() },
            confirmText = "Zertifikat vertrauen",
            onConfirm = { viewModel.confirmHueBridgeCertificate() },
            dismissText = "Abbrechen"
        ) {
            Column {
                Text(
                    text = "GhosTTalk stellt eine verschlüsselte Verbindung zur Bridge her. Bitte überprüfen Sie die folgenden Zertifikatsdetails:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Text(
                    text = "Name (Subject): ${cert.subject}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Aussteller (Issuer): ${cert.issuer}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Gültig ab: ${cert.validFrom}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Gültig bis: ${cert.validTo}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "SHA-256 Fingerprint:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = cert.fingerprint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(dimensions.paddingMedium)
    ) {
        // --- Philips Hue Section (Local Only) ---
        PreferenceCategory(stringResource(R.string.smart_home_provider_philips_hue)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                SettingsEditTextItem(
                    label = stringResource(R.string.settings_hue_bridge_ip),
                    value = hueBridgeIp,
                    onValueChange = { viewModel.setHueBridgeIp(it) }
                )
                
                SettingsEditTextItem(
                    label = stringResource(R.string.settings_hue_username),
                    value = hueUsername,
                    onValueChange = { viewModel.setHueUsername(it) }
                )
                
                huePairingStatus?.let { status ->
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                Text(
                    text = if (cachedCount > 0) "Geräteliste geladen: $cachedCount Lampen im Cache" else "Keine Lampen im Cache",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Row(modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = { viewModel.discoverHueBridges() }) {
                        Text(stringResource(R.string.settings_hue_discover_bridges))
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = { viewModel.registerLocalHueBridge() },
                        enabled = hueBridgeIp.isNotBlank()
                    ) {
                        Text("Verbindung herstellen")
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = { viewModel.refreshHueDevicesCache() },
                        enabled = hueBridgeIp.isNotBlank() && hueUsername.isNotBlank() && !isUpdatingHueCache
                    ) {
                        Text(if (isUpdatingHueCache) "Lade..." else "Geräteliste laden")
                    }
                }
            }
        }


    }
}

@Composable
fun SmartHomeProvider.getDisplayName(): String {
    return when (this) {
        SmartHomeProvider.GOOGLE_HOME -> stringResource(R.string.smart_home_provider_google_home)
        SmartHomeProvider.PHILIPS_HUE -> stringResource(R.string.smart_home_provider_philips_hue)
    }
}

package com.andreas_kratzer.ghosttalk.ui.setup.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsDropdownItem
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsViewModel

@Composable
fun DeviceSettingsStepContent(viewModel: SettingsViewModel) {
    val localDimensions = LocalDimensions.current
    val speakerVolume by viewModel.speakerVolume.collectAsState(100)
    val headphoneVolume by viewModel.headphoneVolume.collectAsState(100)
    val blockVolumeKeys by viewModel.blockVolumeKeys.collectAsState(false)
    val bluetoothDelay by viewModel.bluetoothDelay.collectAsState(1500L)

    val availableAudioDevices by viewModel.availableAudioDevices.collectAsState()
    val cachedAudioDevices by viewModel.cachedAudioDevices.collectAsState()
    val selectedTtsAddress by viewModel.selectedTtsAudioDeviceAddress.collectAsState(null)
    val selectedCuesAddress by viewModel.selectedCuesAudioDeviceAddress.collectAsState(null)

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Text(
        text = "Lokale Geräte-Einstellungen",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(localDimensions.paddingSmall))
    Text(
        text = "Diese Optionen gelten nur für dieses Gerät und werden nicht synchronisiert.",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(localDimensions.paddingLarge))

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // TTS Audio Device Select
        val ttsOptions = remember(availableAudioDevices, cachedAudioDevices) {
            val options = mutableListOf<Pair<String, () -> Unit>>()
            options.add("Standard-Gerät" to { viewModel.setTtsAudioDevice(null) })
            val activeDeviceIds = availableAudioDevices.mapNotNull { it.address.split("|").getOrNull(1) }.toSet()
            
            availableAudioDevices.forEach { device ->
                options.add(device.name to { viewModel.setTtsAudioDevice(device.address) })
            }
            
            cachedAudioDevices.forEach { (persistentId, name) ->
                if (!activeDeviceIds.contains(persistentId)) {
                    val displayName = "$name (Offline)"
                    val fallbackAddress = "0|$persistentId"
                    options.add(displayName to { viewModel.setTtsAudioDevice(fallbackAddress) })
                }
            }
            options
        }

        SettingsDropdownItem(
            label = "Audio-Ausgabe (Sprachausgabe)",
            selectedOption = viewModel.getResolvedDeviceName(selectedTtsAddress),
            options = ttsOptions
        )

        // Cues Audio Device Select
        val cuesOptions = remember(availableAudioDevices, cachedAudioDevices) {
            val options = mutableListOf<Pair<String, () -> Unit>>()
            options.add("Standard-Gerät" to { viewModel.setCuesAudioDevice(null) })
            val activeDeviceIds = availableAudioDevices.mapNotNull { it.address.split("|").getOrNull(1) }.toSet()
            
            availableAudioDevices.forEach { device ->
                options.add(device.name to { viewModel.setCuesAudioDevice(device.address) })
            }
            
            cachedAudioDevices.forEach { (persistentId, name) ->
                if (!activeDeviceIds.contains(persistentId)) {
                    val displayName = "$name (Offline)"
                    val fallbackAddress = "0|$persistentId"
                    options.add(displayName to { viewModel.setCuesAudioDevice(fallbackAddress) })
                }
            }
            options
        }

        SettingsDropdownItem(
            label = "Audio-Ausgabe (Hinweistöne / Cues)",
            selectedOption = viewModel.getResolvedDeviceName(selectedCuesAddress),
            options = cuesOptions
        )

        // Speaker Volume
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Lautstärke Lautsprecher",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "$speakerVolume%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = speakerVolume / 100f,
                onValueChange = { viewModel.setSpeakerVolume((it * 100).toInt()) }
            )
        }

        // Headphone Volume
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Lautstärke Kopfhörer",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "$headphoneVolume%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = headphoneVolume / 100f,
                onValueChange = { viewModel.setHeadphoneVolume((it * 100).toInt()) }
            )
        }

        // Block volume keys
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), MaterialTheme.shapes.medium)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Lautstärketasten sperren",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Verhindert versehentliche Lautstärkeänderungen durch physische Tasten.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = blockVolumeKeys,
                onCheckedChange = { viewModel.setBlockVolumeKeys(it) }
            )
        }

        // Bluetooth Delay
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bluetooth Audio Verzögerung",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${bluetoothDelay}ms",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = bluetoothDelay.toFloat(),
                onValueChange = { viewModel.setBluetoothDelay(it.toLong().toString()) },
                valueRange = 0f..3000f,
                steps = 59
            )
            Text(
                text = "Gibt Audiosignale leicht verzögert aus, um abgehackten Bluetooth-Ton zu korrigieren.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

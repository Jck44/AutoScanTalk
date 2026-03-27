package com.andreas_kratzer.ghosttalk.ui.pages.actions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.cloud.HomeDevice
import com.andreas_kratzer.ghosttalk.core.model.SmartHomeProvider
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.ui.sections.getDisplayName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartHomeActionFields(
    selectedProvider: SmartHomeProvider,
    onProviderSelected: (SmartHomeProvider) -> Unit,
    deviceId: String,
    onDeviceSelected: (HomeDevice) -> Unit,
    deviceName: String,
    selectedIntent: String,
    onIntentSelected: (String) -> Unit,
    value: String,
    onValueChange: (String) -> Unit,
    devices: List<HomeDevice>,
    isFetching: Boolean,
    onRefresh: () -> Unit
) {
    val dimensions = LocalDimensions.current
    var expandedProvider by remember { mutableStateOf(false) }
    var expandedDevice by remember { mutableStateOf(false) }
    var expandedIntent by remember { mutableStateOf(false) }

    val selectedDevice = devices.find { it.id == deviceId }
    
    val availableIntents = remember(selectedDevice, selectedProvider) {
        if (selectedProvider == SmartHomeProvider.GOOGLE_HOME) {
            selectedDevice?.traits?.flatMap { trait ->
                when (trait) {
                    "sdm.devices.traits.OnOff" -> listOf("sdm.devices.commands.OnOff.On", "sdm.devices.commands.OnOff.Off")
                    "sdm.devices.traits.Brightness" -> listOf("sdm.devices.commands.Brightness.Brightness")
                    "sdm.devices.traits.TemperatureSetting" -> listOf("sdm.devices.commands.TemperatureSetting.SetPoint")
                    else -> emptyList()
                }
            }?.map { it to it.substringAfterLast(".") } ?: emptyList()
        } else {
            // Skeleton for Hue
            listOf("action.on" to "An", "action.off" to "Aus")
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(dimensions.paddingSmall)) {
        // Step 1: Provider
        Text(stringResource(R.string.button_smart_home_provider_label), style = MaterialTheme.typography.labelMedium)
        ExposedDropdownMenuBox(
            expanded = expandedProvider,
            onExpandedChange = { expandedProvider = !expandedProvider }
        ) {
            OutlinedTextField(
                readOnly = true,
                value = selectedProvider.getDisplayName(),
                onValueChange = { },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProvider) },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expandedProvider,
                onDismissRequest = { expandedProvider = false }
            ) {
                SmartHomeProvider.entries.forEach { provider ->
                    DropdownMenuItem(
                        text = { Text(provider.getDisplayName()) },
                        onClick = {
                            onProviderSelected(provider)
                            expandedProvider = false
                        }
                    )
                }
            }
        }

        // Step 2: Device
        Text(stringResource(R.string.button_google_home_device_label), style = MaterialTheme.typography.labelMedium)
        ExposedDropdownMenuBox(
            expanded = expandedDevice,
            onExpandedChange = { expandedDevice = !expandedDevice }
        ) {
            OutlinedTextField(
                readOnly = true,
                value = if (isFetching) stringResource(R.string.button_google_home_loading_devices) else deviceName.ifEmpty { stringResource(R.string.button_google_home_no_devices) },
                onValueChange = { },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDevice) },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expandedDevice,
                onDismissRequest = { expandedDevice = false }
            ) {
                if (devices.isEmpty() && !isFetching) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.button_google_home_no_devices)) }, onClick = { onRefresh(); expandedDevice = false })
                }
                devices.forEach { device ->
                    DropdownMenuItem(
                        text = { Text(device.name) },
                        onClick = {
                            onDeviceSelected(device)
                            expandedDevice = false
                        }
                    )
                }
            }
        }

        // Step 3: Intent/Command
        if (deviceId.isNotEmpty()) {
            Text(stringResource(R.string.button_google_home_command_label), style = MaterialTheme.typography.labelMedium)
            ExposedDropdownMenuBox(
                expanded = expandedIntent,
                onExpandedChange = { expandedIntent = !expandedIntent }
            ) {
                val currentIntentLabel = availableIntents.find { it.first == selectedIntent }?.second ?: ""
                OutlinedTextField(
                    readOnly = true,
                    value = currentIntentLabel,
                    onValueChange = { },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedIntent) },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expandedIntent,
                    onDismissRequest = { expandedIntent = false }
                ) {
                    availableIntents.forEach { (intent, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onIntentSelected(intent)
                                expandedIntent = false
                            }
                        )
                    }
                }
            }

            // Step 4: Value (Optional)
            if (selectedIntent.contains("Brightness") || selectedIntent.contains("SetPoint")) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                            onValueChange(newValue)
                        }
                    },
                    label = { Text(stringResource(R.string.button_google_home_value_label)) },
                    shape = MaterialTheme.shapes.large,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    LaunchedEffect(selectedProvider) {
        if (selectedProvider == SmartHomeProvider.GOOGLE_HOME && devices.isEmpty()) onRefresh()
    }
}

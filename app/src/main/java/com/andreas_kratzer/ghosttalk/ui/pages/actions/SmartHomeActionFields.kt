package com.andreas_kratzer.ghosttalk.ui.pages.actions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
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
import androidx.compose.ui.focus.onFocusChanged
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
    deviceId: String = "",
    onDeviceSelected: (HomeDevice) -> Unit = {},
    deviceName: String = "",
    selectedIntent: String = "",
    onIntentSelected: (String) -> Unit = {},
    value: String = "",
    onValueChange: (String) -> Unit = {},
    devices: List<HomeDevice> = emptyList(),
    isFetching: Boolean = false,
    onRefresh: () -> Unit = {},
    onAutoSave: () -> Unit = {},
    onlyShowSelector: Boolean = false,
    onlyShowConfig: Boolean = false
) {
    val dimensions = LocalDimensions.current
    var expandedProvider by remember { mutableStateOf(false) }
    var expandedDevice by remember { mutableStateOf(false) }
    var expandedIntent by remember { mutableStateOf(false) }

    val selectedDevice = devices.find { it.id == deviceId }
    
    val availableIntents = remember(selectedDevice, selectedProvider) {
        listOf(
            "action.on" to "An",
            "action.off" to "Aus",
            "action.brightness" to "Helligkeit",
            "action.color" to "Farbe"
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(dimensions.paddingSmall)) {
        if (!onlyShowConfig) {
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
                    DropdownMenuItem(
                        text = { Text(SmartHomeProvider.PHILIPS_HUE.getDisplayName()) },
                        onClick = {
                            onProviderSelected(SmartHomeProvider.PHILIPS_HUE)
                            expandedProvider = false
                            onAutoSave()
                        }
                    )
                }
            }
        }

        if (!onlyShowSelector) {
            // Step 2: Device
            Text("Gerät (Lampe)", style = MaterialTheme.typography.labelMedium)
            ExposedDropdownMenuBox(
                expanded = expandedDevice,
                onExpandedChange = { expandedDevice = !expandedDevice }
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = if (isFetching) "Lade Lampen..." else deviceName.ifEmpty { "Keine Lampen gefunden" },
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
                        DropdownMenuItem(text = { Text("Suche Lampen...") }, onClick = { onRefresh(); expandedDevice = false })
                    }
                    devices.forEach { device ->
                        DropdownMenuItem(
                            text = { Text(device.name) },
                            onClick = {
                                onDeviceSelected(device)
                                expandedDevice = false
                                onAutoSave()
                            }
                        )
                    }
                }
            }

            if (selectedProvider == SmartHomeProvider.PHILIPS_HUE) {
                Button(
                    onClick = { onRefresh() },
                    enabled = !isFetching,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isFetching) "Lade..." else "Geräteliste laden")
                }
            }

            // Step 3: Intent/Command
            if (deviceId.isNotEmpty()) {
                Text("Befehl", style = MaterialTheme.typography.labelMedium)
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
                                    // Set a valid default when switching modes
                                    if (intent == "action.brightness") {
                                        onValueChange("100")
                                    } else if (intent == "action.color") {
                                        onValueChange("Warmweiß")
                                    } else {
                                        onValueChange("")
                                    }
                                    onAutoSave()
                                }
                            )
                        }
                    }
                }

                // Step 4: Value (Optional)
                if (selectedIntent == "action.brightness") {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { newValue ->
                            if (newValue.isEmpty() || (newValue.all { it.isDigit() } && newValue.toInt() <= 100)) {
                                onValueChange(newValue)
                            }
                        },
                        label = { Text("Helligkeit (0 - 100%)") },
                        shape = MaterialTheme.shapes.large,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().onFocusChanged { 
                            if (!it.isFocused) onAutoSave()
                        }
                    )
                } else if (selectedIntent == "action.color") {
                    var expandedColor by remember { mutableStateOf(false) }
                    val colors = listOf("Rot", "Grün", "Blau", "Gelb", "Orange", "Pink", "Lila", "Warmweiß", "Kaltweiß")
                    Text("Farbe", style = MaterialTheme.typography.labelMedium)
                    ExposedDropdownMenuBox(
                        expanded = expandedColor,
                        onExpandedChange = { expandedColor = !expandedColor }
                    ) {
                        OutlinedTextField(
                            readOnly = true,
                            value = value.ifEmpty { "Warmweiß" },
                            onValueChange = { },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedColor) },
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedColor,
                            onDismissRequest = { expandedColor = false }
                        ) {
                            colors.forEach { color ->
                                DropdownMenuItem(
                                    text = { Text(color) },
                                    onClick = {
                                        onValueChange(color)
                                        expandedColor = false
                                        onAutoSave()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(selectedProvider) {
        if (selectedProvider == SmartHomeProvider.PHILIPS_HUE && devices.isEmpty()) onRefresh()
    }
}

package com.andreas_kratzer.ghosttalk.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import kotlinx.coroutines.delay

@Composable
fun PreferenceCategory(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val dimensions = LocalDimensions.current
    Column(modifier = modifier.padding(vertical = dimensions.paddingMedium)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = dimensions.paddingSmall, bottom = dimensions.paddingMedium)
        )
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(dimensions.paddingMedium), content = content)
        }
    }
}

@Composable
fun SettingsToggleItem(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val dimensions = LocalDimensions.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = dimensions.paddingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
fun SettingsClickableItem(label: String, value: String, onClick: () -> Unit) {
    val dimensions = LocalDimensions.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = dimensions.paddingMedium)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
fun SettingsEditTextItem(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardOptions: KeyboardOptions? = null,
    forceKeyboard: Boolean = false,
    numericOnly: Boolean = false
) {
    val dimensions = LocalDimensions.current
    var localValue by remember(value) { mutableStateOf(value) }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    val defaultKeyboardOptions = if (numericOnly) {
        KeyboardOptions(keyboardType = KeyboardType.Number)
    } else {
        KeyboardOptions(
            keyboardType = KeyboardType.Text,
            autoCorrectEnabled = true,
            capitalization = KeyboardCapitalization.Sentences
        )
    }

    LaunchedEffect(localValue) {
        if (localValue != value) {
            delay(500) // Debounce period
            onValueChange(localValue)
        }
    }

    OutlinedTextField(
        value = localValue,
        onValueChange = { newValue -> 
            if (numericOnly) {
                if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                    localValue = newValue
                }
            } else {
                localValue = newValue
            }
        },
        label = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimensions.paddingSmall)
            .onFocusChanged { 
                if (it.isFocused && forceKeyboard) {
                    keyboardController?.show()
                }
            },
        keyboardOptions = keyboardOptions ?: defaultKeyboardOptions,
        singleLine = true
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDropdownItem(
    label: String,
    selectedOption: String,
    options: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier
) {
    val dimensions = LocalDimensions.current
    var expanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = dimensions.paddingSmall)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = dimensions.paddingSmall)
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedOption,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { (optionLabel, onClick) ->
                    DropdownMenuItem(
                        text = { Text(optionLabel, style = MaterialTheme.typography.bodyLarge) },
                        onClick = {
                            onClick()
                            focusManager.clearFocus()
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsSliderItem(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    description: String? = null
) {
    val dimensions = LocalDimensions.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = dimensions.paddingSmall)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = dimensions.paddingSmall)
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = dimensions.paddingSmall)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${(value * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = dimensions.paddingMedium)
            )
        }
    }
}

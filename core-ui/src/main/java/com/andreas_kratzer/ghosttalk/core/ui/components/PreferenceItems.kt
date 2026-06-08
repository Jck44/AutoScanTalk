package com.andreas_kratzer.ghosttalk.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import kotlinx.coroutines.delay

@Composable
fun PreferenceCategory(
    title: String,
    modifier: Modifier = Modifier,
    isCloudProfile: Boolean = false,
    isLocalDevice: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val dimensions = LocalDimensions.current
    Column(modifier = modifier.padding(vertical = dimensions.paddingMedium)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = dimensions.paddingSmall, bottom = dimensions.paddingMedium)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            if (isCloudProfile) {
                Icon(
                    imageVector = GhostTalkIcons.Cloud,
                    contentDescription = "Profil-Einstellung (wird synchronisiert)",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp).padding(horizontal = 2.dp)
                )
            }
            if (isLocalDevice) {
                Icon(
                    imageVector = GhostTalkIcons.Tablet,
                    contentDescription = "Lokale Einstellung (nicht synchronisiert)",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp).padding(horizontal = 2.dp)
                )
            }
        }
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
    description: String? = null,
    onValueChangeFinished: (() -> Unit)? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    val dimensions = LocalDimensions.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { 
                onCheckedChange(!checked)
                onValueChangeFinished?.invoke()
            }
            .padding(vertical = dimensions.paddingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Switch(
            checked = checked, 
            onCheckedChange = { 
                onCheckedChange(it)
                onValueChangeFinished?.invoke()
            }, 
            enabled = enabled
        )
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
    numericOnly: Boolean = false,
    onFocusLost: (() -> Unit)? = null,
    isPlaying: Boolean = false,
    onPlayPauseClick: (() -> Unit)? = null,
    isLoading: Boolean = false,
    playPauseIconTint: androidx.compose.ui.graphics.Color? = null,
    borderless: Boolean = false,
    placeholder: String = "",
    isPassword: Boolean = false
) {
    val dimensions = LocalDimensions.current
    var localValue by remember(value) { mutableStateOf(value) }
    var passwordVisible by remember { mutableStateOf(false) }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    val defaultKeyboardOptions = if (numericOnly) {
        KeyboardOptions(keyboardType = KeyboardType.Number)
    } else {
        KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
            autoCorrectEnabled = !isPassword,
            capitalization = if (isPassword) KeyboardCapitalization.None else KeyboardCapitalization.Sentences
        )
    }

    LaunchedEffect(localValue) {
        if (localValue != value) {
            delay(500) // Debounce period
            onValueChange(localValue)
        }
    }

    var hadFocus by remember { mutableStateOf(false) }

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
        label = if (label.isNotEmpty()) {
            { Text(label, style = MaterialTheme.typography.bodyMedium) }
        } else null,
        placeholder = if (placeholder.isNotEmpty()) {
            { Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) }
        } else null,
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimensions.paddingSmall)
            .onFocusChanged { 
                if (it.isFocused) {
                    hadFocus = true
                    if (forceKeyboard) keyboardController?.show()
                } else if (hadFocus) {
                    onFocusLost?.invoke()
                }
            },
        keyboardOptions = keyboardOptions ?: defaultKeyboardOptions,
        visualTransformation = if (isPassword && !passwordVisible) {
            androidx.compose.ui.text.input.PasswordVisualTransformation()
        } else {
            androidx.compose.ui.text.input.VisualTransformation.None
        },
        singleLine = true,
        colors = if (borderless) {
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                disabledBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                errorBorderColor = androidx.compose.ui.graphics.Color.Transparent
            )
        } else {
            OutlinedTextFieldDefaults.colors()
        },
        trailingIcon = if (onPlayPauseClick != null && localValue.isNotBlank()) {
            {
                if (isLoading) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = onPlayPauseClick) {
                        Icon(
                            imageVector = if (isPlaying) GhostTalkIcons.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Stop" else "Play",
                            tint = playPauseIconTint ?: androidx.compose.material3.LocalContentColor.current
                        )
                    }
                }
            }
        } else if (isPassword && localValue.isNotBlank()) {
            {
                val icon = if (passwordVisible) GhostTalkIcons.Visibility else GhostTalkIcons.VisibilityOff
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = icon,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    )
                }
            }
        } else null
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDropdownItem(
    label: String,
    selectedOption: String,
    options: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null
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
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
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
                            onValueChangeFinished?.invoke()
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
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
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

data class DropdownGroup(
    val name: String,
    val items: List<Pair<String, () -> Unit>>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsGroupedDropdownItem(
    label: String,
    selectedOption: String,
    groups: List<DropdownGroup>,
    modifier: Modifier = Modifier,
    iconProvider: (@Composable (String) -> Unit)? = null,
    onValueChangeFinished: (() -> Unit)? = null
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
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
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
                leadingIcon = iconProvider?.let { { it(selectedOption) } },
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
                groups.forEachIndexed { index, group ->
                    if (group.name.isNotBlank()) {
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    group.items.forEach { (optionLabel, onClick) ->
                        DropdownMenuItem(
                            text = { Text(optionLabel, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = if (group.name.isNotBlank() && iconProvider == null) 12.dp else 0.dp)) },
                            leadingIcon = iconProvider?.let { { it(optionLabel) } },
                            onClick = {
                                onClick()
                                focusManager.clearFocus()
                                expanded = false
                                onValueChangeFinished?.invoke()
                            }
                        )
                    }
                    if (index < groups.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
}


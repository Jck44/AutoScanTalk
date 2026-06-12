package com.andreas_kratzer.ghosttalk.ui.pages.actions

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.model.DeviceActionType
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions

@Composable
fun DateTimeReadFields(
    selectedType: DeviceActionType,
    includeWeekday: Boolean,
    onIncludeWeekdayChange: (Boolean) -> Unit,
    prefixText: String,
    onPrefixTextChange: (String) -> Unit,
    suffixText: String,
    onSuffixTextChange: (String) -> Unit,
    offsetValue: String,
    onOffsetValueChange: (String) -> Unit,
    onAutoSave: () -> Unit
) {
    val context = LocalContext.current
    val dimensions = LocalDimensions.current
    val scrollState = rememberScrollState()

    Column(verticalArrangement = Arrangement.spacedBy(dimensions.paddingSmall)) {
        // Preset Suggestion Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(dimensions.paddingSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectedType == DeviceActionType.READ_TIME) {
                SuggestionChip(
                    onClick = {
                        onPrefixTextChange(context.getString(R.string.device_control_time_prefix_std))
                        onSuffixTextChange(context.getString(R.string.device_control_time_suffix_std))
                        onOffsetValueChange("0")
                        onAutoSave()
                    },
                    label = { Text(stringResource(R.string.device_control_time_preset_standard)) }
                )
                SuggestionChip(
                    onClick = {
                        onPrefixTextChange("")
                        onSuffixTextChange("")
                        onOffsetValueChange("0")
                        onAutoSave()
                    },
                    label = { Text(stringResource(R.string.device_control_time_preset_short)) }
                )
                SuggestionChip(
                    onClick = {
                        onPrefixTextChange(context.getString(R.string.device_control_time_prefix_plus5))
                        onSuffixTextChange(context.getString(R.string.device_control_time_suffix_std))
                        onOffsetValueChange("5")
                        onAutoSave()
                    },
                    label = { Text(stringResource(R.string.device_control_time_preset_plus5)) }
                )
                SuggestionChip(
                    onClick = {
                        onPrefixTextChange(context.getString(R.string.device_control_time_prefix_minus5))
                        onSuffixTextChange(context.getString(R.string.device_control_time_suffix_std))
                        onOffsetValueChange("-5")
                        onAutoSave()
                    },
                    label = { Text(stringResource(R.string.device_control_time_preset_minus5)) }
                )
            } else if (selectedType == DeviceActionType.READ_DATE) {
                SuggestionChip(
                    onClick = {
                        onPrefixTextChange(context.getString(R.string.device_control_date_prefix_weekday_date))
                        onSuffixTextChange("")
                        onIncludeWeekdayChange(true)
                        onOffsetValueChange("0")
                        onAutoSave()
                    },
                    label = { Text(stringResource(R.string.device_control_date_preset_weekday_date)) }
                )
                SuggestionChip(
                    onClick = {
                        onPrefixTextChange(context.getString(R.string.device_control_date_prefix_only_date))
                        onSuffixTextChange("")
                        onIncludeWeekdayChange(false)
                        onOffsetValueChange("0")
                        onAutoSave()
                    },
                    label = { Text(stringResource(R.string.device_control_date_preset_only_date)) }
                )
                SuggestionChip(
                    onClick = {
                        onPrefixTextChange(context.getString(R.string.device_control_date_prefix_tomorrow))
                        onSuffixTextChange("")
                        onIncludeWeekdayChange(true)
                        onOffsetValueChange("1")
                        onAutoSave()
                    },
                    label = { Text(stringResource(R.string.device_control_date_preset_tomorrow)) }
                )
                SuggestionChip(
                    onClick = {
                        onPrefixTextChange(context.getString(R.string.device_control_date_prefix_yesterday))
                        onSuffixTextChange("")
                        onIncludeWeekdayChange(true)
                        onOffsetValueChange("-1")
                        onAutoSave()
                    },
                    label = { Text(stringResource(R.string.device_control_date_preset_yesterday)) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimensions.paddingSmall)
        ) {
            OutlinedTextField(
                value = prefixText,
                onValueChange = onPrefixTextChange,
                label = { Text(stringResource(R.string.button_device_control_prefix_label)) },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.weight(1f).onFocusChanged { 
                    if (!it.isFocused) onAutoSave()
                }
            )

            OutlinedTextField(
                value = suffixText,
                onValueChange = onSuffixTextChange,
                label = { Text(stringResource(R.string.button_device_control_suffix_label)) },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.weight(1f).onFocusChanged { 
                    if (!it.isFocused) onAutoSave()
                }
            )
        }

        if (selectedType == DeviceActionType.READ_DATE) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimensions.paddingSmall)
            ) {
                Box(modifier = Modifier.weight(1.2f)) {
                    com.andreas_kratzer.ghosttalk.core.ui.components.SettingsToggleItem(
                        label = stringResource(R.string.button_device_control_weekday_label),
                        checked = includeWeekday,
                        onCheckedChange = onIncludeWeekdayChange,
                        onValueChangeFinished = onAutoSave
                    )
                }
                
                OutlinedTextField(
                    value = offsetValue,
                    onValueChange = { if (it.isEmpty() || it == "-" || it.all { c -> c.isDigit() || c == '-' }) onOffsetValueChange(it) },
                    label = { Text(stringResource(R.string.button_device_control_offset_days_label)) },
                    shape = MaterialTheme.shapes.large,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(0.8f).onFocusChanged { 
                        if (!it.isFocused) onAutoSave()
                    }
                )
            }
        } else if (selectedType == DeviceActionType.READ_TIME) {
            OutlinedTextField(
                value = offsetValue,
                onValueChange = { if (it.isEmpty() || it == "-" || it.all { c -> c.isDigit() || c == '-' }) onOffsetValueChange(it) },
                label = { Text(stringResource(R.string.button_device_control_offset_minutes_label)) },
                shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().onFocusChanged { 
                    if (!it.isFocused) onAutoSave()
                }
            )
        } else {
            // Calendar Entries Count
            OutlinedTextField(
                value = offsetValue,
                onValueChange = { if (it.isEmpty() || it.all { c -> c.isDigit() }) onOffsetValueChange(it) },
                label = { Text(stringResource(R.string.button_device_control_calendar_count_label)) },
                placeholder = { Text("1") },
                shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().onFocusChanged { 
                    if (!it.isFocused) onAutoSave()
                }
            )
        }

        val appointmentsPlaceholder = stringResource(R.string.device_control_calendar_appointments_placeholder)
        val offsetInt = offsetValue.toIntOrNull() ?: 0
        val count = offsetInt.coerceAtLeast(1)
        val countSuffix = pluralStringResource(R.plurals.device_control_calendar_read_count_suffix, count, count)

        // Spoken text preview
        val previewText = remember(selectedType, prefixText, suffixText, includeWeekday, offsetValue, appointmentsPlaceholder, countSuffix) {
            try {
                val calendar = java.util.Calendar.getInstance()
                if (selectedType == DeviceActionType.READ_TIME) {
                    if (offsetInt != 0) {
                        calendar.add(java.util.Calendar.MINUTE, offsetInt)
                    }
                    val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    val timeString = sdf.format(calendar.time)
                    val prefix = prefixText.takeIf { it.isNotBlank() }?.let { if (it.endsWith(" ")) it else "$it " } ?: ""
                    val suffix = suffixText.takeIf { it.isNotBlank() }?.let { if (it.startsWith(" ")) it else " $it" } ?: ""
                    "$prefix$timeString$suffix"
                } else if (selectedType == DeviceActionType.READ_DATE) {
                    if (offsetInt != 0) {
                        calendar.add(java.util.Calendar.DAY_OF_YEAR, offsetInt)
                    }
                    val pattern = if (includeWeekday) "EEEE, dd. MMMM yyyy" else "dd. MMMM yyyy"
                    val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
                    val dateString = sdf.format(calendar.time)
                    val prefix = prefixText.takeIf { it.isNotBlank() }?.let { if (it.endsWith(" ")) it else "$it " } ?: ""
                    val suffix = suffixText.takeIf { it.isNotBlank() }?.let { if (it.startsWith(" ")) it else " $it" } ?: ""
                    "$prefix$dateString$suffix"
                } else {
                    val prefix = prefixText.takeIf { it.isNotBlank() }?.let { if (it.endsWith(" ")) it else "$it " } ?: ""
                    val suffix = suffixText.takeIf { it.isNotBlank() }?.let { if (it.startsWith(" ")) it else " $it" } ?: ""
                    "$prefix$appointmentsPlaceholder$suffix$countSuffix"
                }
            } catch (_: Exception) {
                ""
            }
        }

        if (previewText.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(dimensions.paddingMedium)) {
                    Text(
                        text = "Gesprochener Text (Vorschau):",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(dimensions.paddingSmall))
                    Text(
                        text = "\"$previewText\"",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                        )
                    )
                }
            }
        }
    }
}

package com.andreas_kratzer.ghosttalk.ui.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.ui.settings.PreferenceCategory
import com.andreas_kratzer.ghosttalk.ui.settings.SettingsToggleItem
import com.andreas_kratzer.ghosttalk.ui.settings.SettingsViewModel
import com.andreas_kratzer.ghosttalk.ui.theme.GhosTTalkIcons
import com.andreas_kratzer.ghosttalk.ui.theme.LocalDimensions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TestSettingsSection(viewModel: SettingsViewModel, isGlobal: Boolean) {
    val showTestButtons by viewModel.showTestButtons.collectAsState(false)
    val showPageId by viewModel.showPageIdInLog.collectAsState(true)
    val volumeKeysActivate by viewModel.volumeKeysActivate.collectAsState(false)
    val buttonHistory by viewModel.buttonHistory.collectAsState(emptyList())
    val dimensions = LocalDimensions.current
    
    var showHistoryDialog by remember { mutableStateOf(false) }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimensions.paddingMedium),
        verticalArrangement = Arrangement.spacedBy(dimensions.paddingMedium),
        maxItemsInEachRow = 2
    ) {
        if (isGlobal) {
            PreferenceCategory(stringResource(R.string.settings_category_test), modifier = Modifier.weight(1f)) {
                SettingsToggleItem(
                    label = stringResource(R.string.settings_show_test_buttons),
                    checked = showTestButtons,
                    onCheckedChange = { viewModel.setShowTestButtons(it) }
                )
                SettingsToggleItem(
                    label = stringResource(R.string.settings_show_page_id_in_log),
                    checked = showPageId,
                    onCheckedChange = { viewModel.setShowPageIdInLog(it) }
                )
                SettingsToggleItem(
                    label = stringResource(R.string.settings_volume_keys_trigger),
                    checked = volumeKeysActivate,
                    onCheckedChange = { viewModel.setVolumeKeysActivate(it) }
                )
            }
        }

        if (!isGlobal) {
            PreferenceCategory(stringResource(R.string.settings_category_button_history), modifier = Modifier.weight(1f)) {
                if (buttonHistory.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(dimensions.paddingMedium)
                    )
                } else {
                    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    Column {
                        buttonHistory.reversed().take(20).forEachIndexed { index, event ->
                            val timeStr = sdf.format(Date(event.timestamp))
                            ListItem(
                                headlineContent = { Text(event.label, style = MaterialTheme.typography.bodyLarge) },
                                overlineContent = { Text(timeStr, style = MaterialTheme.typography.labelSmall) },
                                supportingContent = { Text(event.actionType, style = MaterialTheme.typography.bodySmall) },
                                leadingContent = {
                                    Icon(
                                        imageVector = GhosTTalkIcons.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                            if (index < buttonHistory.size - 1 && index < 19) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = dimensions.paddingMedium),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.andreas_kratzer.ghosttalk.feature.settings.ui.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Slider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.core.ui.components.PreferenceCategory
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsToggleItem
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.R
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsViewModel
import com.andreas_kratzer.ghosttalk.feature.settings.ui.dialogs.AudioCacheDialog

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TestSettingsSection(viewModel: SettingsViewModel, isGlobal: Boolean) {
    val showTestButtons by viewModel.showTestButtons.collectAsState(false)
    val showPageId by viewModel.showPageIdInLog.collectAsState(true)
    val volumeKeysActivate by viewModel.volumeKeysActivate.collectAsState(false)
    val dimensions = LocalDimensions.current

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
            // Advanced Settings Category (Book-specific)
            val logLimit by viewModel.actionLogLimit.collectAsState(100)
            val activeBook by viewModel.activeBook.collectAsState()
            
            PreferenceCategory(stringResource(R.string.settings_category_advanced), modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.settings_action_log_limit),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.settings_action_log_limit_format, logLimit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val limitSteps = listOf(10, 20, 50, 100, 200, 500)
                    val currentIndex = remember(logLimit) {
                        limitSteps.mapIndexed { index, value -> index to kotlin.math.abs(value - logLimit) }
                            .minByOrNull { it.second }?.first ?: 3
                    }
                    Slider(
                        value = currentIndex.toFloat(),
                        onValueChange = { index -> 
                            viewModel.setActionLogLimitInput(limitSteps[index.toInt()].toString()) 
                        },
                        valueRange = 0f..(limitSteps.size - 1).toFloat(),
                        steps = limitSteps.size - 2
                    )
                }
                
                activeBook?.let { book ->
                    SettingsToggleItem(
                        label = stringResource(R.string.settings_log_ignored_actions),
                        checked = book.logIgnoredActions,
                        onCheckedChange = { isChecked: Boolean -> viewModel.setLogIgnoredActionsInput(isChecked) }
                    )
                    SettingsToggleItem(
                        label = stringResource(R.string.settings_log_stop_actions),
                        checked = book.logStopActions,
                        onCheckedChange = { isChecked: Boolean -> viewModel.setLogStopActionsInput(isChecked) }
                    )
                }
                
                var showAudioCacheDialog by remember { mutableStateOf(false) }
                Button(
                    onClick = { 
                        viewModel.loadAudioCache()
                        showAudioCacheDialog = true 
                    },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Gecachte Audios verwalten")
                }

                if (showAudioCacheDialog) {
                    val cacheItems by viewModel.audioCacheItems.collectAsState()
                    AudioCacheDialog(
                        cacheItems = cacheItems,
                        onDismiss = { showAudioCacheDialog = false },
                        onDelete = { viewModel.deleteAudioCacheItem(it) },
                        onClearAll = { viewModel.clearAudioCache() }
                    )
                }
            }
        }
    }
}


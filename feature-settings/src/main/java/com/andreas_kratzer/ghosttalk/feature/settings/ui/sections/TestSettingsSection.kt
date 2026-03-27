package com.andreas_kratzer.ghosttalk.feature.settings.ui.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.andreas_kratzer.ghosttalk.core.ui.components.PreferenceCategory
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsClickableItem
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsEditTextItem
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
    val buttonHistory by viewModel.buttonHistory.collectAsState(emptyList())
    val selectedHistoryItem by viewModel.selectedHistoryItem.collectAsState()
    val dimensions = LocalDimensions.current
    
    var previewImagePath by remember { mutableStateOf<String?>(null) }

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
                SettingsEditTextItem(
                    label = stringResource(R.string.settings_action_log_limit),
                    value = logLimit.toString(),
                    onValueChange = { newValue: String -> viewModel.setActionLogLimitInput(newValue) },
                    numericOnly = true
                )
                
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
                SettingsClickableItem(
                    label = "Audio-Cache",
                    value = "Gecachte Audios verwalten",
                    onClick = { 
                        viewModel.loadAudioCache()
                        showAudioCacheDialog = true 
                    }
                )

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


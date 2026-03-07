package com.andreas_kratzer.ghosttalk.ui.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.ui.settings.PreferenceCategory
import com.andreas_kratzer.ghosttalk.ui.settings.SettingsToggleItem
import com.andreas_kratzer.ghosttalk.ui.settings.SettingsClickableItem
import com.andreas_kratzer.ghosttalk.ui.settings.SettingsEditTextItem
import com.andreas_kratzer.ghosttalk.ui.settings.SettingsViewModel
import com.andreas_kratzer.ghosttalk.ui.theme.LocalDimensions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TestSettingsSection(viewModel: SettingsViewModel) {
    val showTestButtons by viewModel.showTestButtons.collectAsState(false)
    val showPageId by viewModel.showPageIdInLog.collectAsState(true)
    val volumeKeysActivate by viewModel.volumeKeysActivate.collectAsState(false)
    val geminiTimeout by viewModel.geminiTimeout.collectAsState(6000L)
    val buttonHistory by viewModel.buttonHistory.collectAsState(emptyList())
    val dimensions = LocalDimensions.current
    
    var showHistoryDialog by remember { mutableStateOf(false) }

    PreferenceCategory(stringResource(R.string.settings_category_test)) {
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
        Button(
            onClick = { showHistoryDialog = true },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.settings_show_button_history))
        }
        SettingsToggleItem(
            label = stringResource(R.string.settings_volume_keys_trigger),
            checked = volumeKeysActivate,
            onCheckedChange = { viewModel.setVolumeKeysActivate(it) }
        )
    }

    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            confirmButton = {
                TextButton(onClick = { showHistoryDialog = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            title = { Text(stringResource(R.string.settings_show_button_history)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                    ) {
                        buttonHistory.forEach { event ->
                            val timeStr = sdf.format(Date(event.timestamp))
                            Text(
                                text = "$timeStr | ${event.label} | ${event.actionType}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = dimensions.paddingSmall)
                            )
                        }
                    }
                }
            }
        )
    }
}

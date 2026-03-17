package com.andreas_kratzer.ghosttalk.feature.settings.ui.sections

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import android.graphics.BitmapFactory
import java.io.File
import com.andreas_kratzer.ghosttalk.core.ui.components.PreferenceCategory
import com.andreas_kratzer.ghosttalk.core.ui.components.SettingsToggleItem
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.R
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsViewModel
import com.andreas_kratzer.ghosttalk.core.data.ButtonUsageRepository
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
                        val historyToDisplay: List<ButtonUsageRepository.ButtonUsageEvent> = buttonHistory.reversed().take(15)
                        for (index in historyToDisplay.indices) {
                            val event: ButtonUsageRepository.ButtonUsageEvent = historyToDisplay[index]
                            HistoryItem(
                                event = event,
                                timeStr = sdf.format(Date(event.timestamp)),
                                onImageClick = { previewImagePath = it }
                            )
                            if (index < historyToDisplay.lastIndex) {
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

    if (previewImagePath != null) {
        @OptIn(ExperimentalMaterial3Api::class)
        BasicAlertDialog(
            onDismissRequest = { previewImagePath = null },
            modifier = Modifier.fillMaxSize().padding(16.dp)
        ) {
            val bitmap = remember(previewImagePath) {
                try {
                    BitmapFactory.decodeFile(previewImagePath)
                } catch (_: Exception) {
                    null
                }
            }
            Box(
                modifier = Modifier.fillMaxSize().clickable { previewImagePath = null },
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Full Image",
                        modifier = Modifier.clip(MaterialTheme.shapes.large),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(
    event: ButtonUsageRepository.ButtonUsageEvent,
    timeStr: String,
    onImageClick: (String) -> Unit
) {
    ListItem(
        headlineContent = { Text(event.label, style = MaterialTheme.typography.bodyLarge) },
        overlineContent = { Text(timeStr, style = MaterialTheme.typography.labelSmall) },
        supportingContent = { Text(event.actionType, style = MaterialTheme.typography.bodySmall) },
        leadingContent = {
            Icon(
                imageVector = GhostTalkIcons.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = {
            if (event.imagePath != null) {
                val path = event.imagePath
                val bitmap = remember(path) {
                    try {
                        BitmapFactory.decodeFile(path)
                    } catch (_: Exception) {
                        null
                    }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onImageClick(path!!) }
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
    )
}

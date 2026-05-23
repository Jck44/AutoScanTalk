package com.andreas_kratzer.ghosttalk.feature.settings.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.core.model.UserModeSession
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.R
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UserModeSessionsDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    val sessions by viewModel.userModeSessions.collectAsState(emptyList())
    val dimensions = LocalDimensions.current
    var showClearConfirm by remember { mutableStateOf(false) }

    val totalDuration = remember(sessions) { sessions.sumOf { it.endTime - it.startTime } }
    val sessionCount = sessions.size
    val avgDuration = remember(sessions) { if (sessionCount > 0) totalDuration / sessionCount else 0L }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(
                onClick = { showClearConfirm = true },
                enabled = sessions.isNotEmpty()
            ) {
                Text(
                    text = stringResource(R.string.settings_user_mode_sessions_clear),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        title = { Text(stringResource(R.string.settings_user_mode_sessions_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(450.dp)
            ) {
                // Statistics Summary Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(dimensions.paddingMedium),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SummaryStatItem(
                        label = stringResource(R.string.settings_user_mode_sessions_summary_total),
                        value = formatDuration(totalDuration),
                        modifier = Modifier.weight(1f)
                    )
                    SummaryStatItem(
                        label = stringResource(R.string.settings_user_mode_sessions_summary_count),
                        value = "$sessionCount",
                        modifier = Modifier.weight(0.7f)
                    )
                    SummaryStatItem(
                        label = stringResource(R.string.settings_user_mode_sessions_summary_avg),
                        value = formatDuration(avgDuration),
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = dimensions.paddingMedium),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // Sessions List
                Box(modifier = Modifier.weight(1f)) {
                    if (sessions.isEmpty()) {
                        Text(
                            text = stringResource(R.string.settings_user_mode_sessions_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(dimensions.paddingMedium)
                                .align(Alignment.Center),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        val sdfDate = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }
                        val sdfTime = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                        
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            itemsIndexed(sessions) { index, session ->
                                SessionItem(
                                    session = session,
                                    dateStr = sdfDate.format(Date(session.startTime)),
                                    timeStr = "${sdfTime.format(Date(session.startTime))} - ${sdfTime.format(Date(session.endTime))}"
                                )
                                if (index < sessions.size - 1) {
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
    )

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.settings_user_mode_sessions_clear)) },
            text = { Text(stringResource(R.string.settings_user_mode_sessions_clear_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearUserModeSessions()
                        showClearConfirm = false
                    }
                ) {
                    Text(stringResource(android.R.string.ok), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun SummaryStatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun SessionItem(
    session: UserModeSession,
    dateStr: String,
    timeStr: String
) {
    val duration = session.endTime - session.startTime
    ListItem(
        headlineContent = { Text(timeStr, style = MaterialTheme.typography.bodyLarge) },
        overlineContent = { Text(dateStr, style = MaterialTheme.typography.labelSmall) },
        leadingContent = {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = {
            Text(
                text = formatDuration(duration),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
    )
}

@Composable
private fun formatDuration(durationMillis: Long): String {
    val seconds = (durationMillis / 1000) % 60
    val minutes = (durationMillis / (1000 * 60)) % 60
    val hours = durationMillis / (1000 * 60 * 60)

    return when {
        hours > 0 -> "${hours} Std. ${minutes} Min."
        minutes > 0 -> "${minutes} Min. ${seconds} Sek."
        seconds > 0 -> "${seconds} Sek."
        else -> stringResource(R.string.settings_user_mode_sessions_duration_less_than_minute)
    }
}

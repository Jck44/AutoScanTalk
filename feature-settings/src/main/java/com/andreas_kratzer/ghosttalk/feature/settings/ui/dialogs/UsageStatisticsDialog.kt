package com.andreas_kratzer.ghosttalk.feature.settings.ui.dialogs

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.core.model.ButtonUsageStat
import com.andreas_kratzer.ghosttalk.core.model.GroupedButtonUsageStat
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.core.ui.theme.LocalDimensions
import com.andreas_kratzer.ghosttalk.feature.settings.R
import com.andreas_kratzer.ghosttalk.feature.settings.ui.SettingsViewModel
import kotlinx.serialization.json.*

@Composable
fun UsageStatisticsDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    val topButtons by viewModel.topButtonUsage.collectAsState()
    val dimensions = LocalDimensions.current
    var showClearConfirm by remember { mutableStateOf(false) }

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
                enabled = topButtons.isNotEmpty()
            ) {
                Text(
                    text = stringResource(R.string.settings_clear_usage_stats),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        title = { Text(stringResource(R.string.settings_usage_stats_title)) },
        text = {
            Box(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                if (topButtons.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_usage_stats_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(dimensions.paddingMedium)
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        itemsIndexed(topButtons) { index, stat ->
                            UsageStatItem(
                                stat = stat,
                                onEditButton = { pId, bId -> 
                                    viewModel.onEditButtonFromHistory(pId, bId)
                                    onDismiss()
                                },
                                onJumpToPage = { pId -> 
                                    viewModel.onJumpToPageFromHistory(pId)
                                    onDismiss()
                                }
                            )
                            if (index < topButtons.size - 1) {
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
    )

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.settings_clear_usage_stats)) },
            text = { Text(stringResource(R.string.settings_clear_usage_stats_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearButtonUsageStats(viewModel.activeBookId)
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
private fun UsageStatItem(
    stat: GroupedButtonUsageStat,
    onEditButton: (String, String) -> Unit,
    onJumpToPage: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val dimensions = LocalDimensions.current

    Column(modifier = Modifier.animateContentSize()) {
        ListItem(
            modifier = Modifier.clickable { expanded = !expanded },
            headlineContent = { Text(stat.label, style = MaterialTheme.typography.bodyLarge) },
            supportingContent = {
                Text(
                    text = stringResource(R.string.settings_usage_stats_label, stat.totalCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            leadingContent = {
                Icon(
                    imageVector = if (expanded) GhostTalkIcons.KeyboardArrowUp else GhostTalkIcons.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
            },
            trailingContent = {
                UsageStatMoreMenu(
                    stat = stat.children.first(), // Default to first child for group menu
                    onEditButton = onEditButton,
                    onJumpToPage = onJumpToPage
                )
            },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
        )

        if (expanded) {
            stat.children.forEach { child ->
                ChildUsageStatItem(
                    stat = child,
                    onEditButton = onEditButton,
                    onJumpToPage = onJumpToPage,
                    modifier = Modifier.padding(start = dimensions.paddingExtraLarge)
                )
            }
        }
    }
}

@Composable
private fun ChildUsageStatItem(
    stat: ButtonUsageStat,
    onEditButton: (String, String) -> Unit,
    onJumpToPage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        modifier = modifier,
        headlineContent = { 
            Text(
                text = "ID: ${stat.buttonConfigId.take(8)}...", 
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ) 
        },
        supportingContent = {
            Text(
                text = "${stat.usageCount} Klicks • Seite: ${stat.pageId.take(12)}...",
                style = MaterialTheme.typography.labelSmall
            )
        },
        trailingContent = {
            UsageStatMoreMenu(
                stat = stat,
                onEditButton = onEditButton,
                onJumpToPage = onJumpToPage
            )
        },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
    )
}

@Composable
private fun UsageStatMoreMenu(
    stat: ButtonUsageStat,
    onEditButton: (String, String) -> Unit,
    onJumpToPage: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val targetPageId = remember(stat.actionJson) {
        try {
            val jsonElement = Json.parseToJsonElement(stat.actionJson).jsonObject
            jsonElement["targetPageId"]?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }
    }

    Box {
        IconButton(onClick = { showMenu = true }) {
            Icon(
                imageVector = GhostTalkIcons.MoreVert,
                contentDescription = "Actions",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.history_edit_button)) },
                onClick = {
                    showMenu = false
                    onEditButton(stat.pageId, stat.buttonConfigId)
                },
                leadingIcon = {
                    Icon(GhostTalkIcons.Edit, contentDescription = null)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.history_jump_to_page)) },
                onClick = {
                    showMenu = false
                    if (targetPageId != null) {
                        onJumpToPage(targetPageId)
                    } else {
                        onJumpToPage(stat.pageId)
                    }
                },
                leadingIcon = {
                    Icon(GhostTalkIcons.ArrowForward, contentDescription = null)
                }
            )
        }
    }
}

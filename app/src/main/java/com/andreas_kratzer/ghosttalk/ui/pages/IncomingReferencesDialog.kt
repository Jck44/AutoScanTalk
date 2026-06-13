package com.andreas_kratzer.ghosttalk.ui.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.domain.pages.UsageLocation
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.andreas_kratzer.ghosttalk.ui.components.UsageLocationRow

@Composable
fun IncomingReferencesDialog(
    pageName: String,
    usages: List<UsageLocation>,
    onDismiss: () -> Unit,
    onNavigateToUsage: (UsageLocation) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.page_incoming_links_title)) },
        text = {
            Column {
                if (usages.isEmpty()) {
                    Text(
                        text = stringResource(R.string.page_incoming_links_empty),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = stringResource(R.string.page_incoming_links_message, pageName),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    val scrollState = rememberScrollState()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .verticalScroll(scrollState)
                    ) {
                        Column {
                            usages.forEach { usage ->
                                UsageLocationRow(
                                    usage = usage,
                                    trailingContent = {
                                        IconButton(
                                            onClick = {
                                                onNavigateToUsage(usage)
                                            }
                                        ) {
                                            Icon(
                                                imageVector = GhostTalkIcons.ArrowForward,
                                                contentDescription = stringResource(R.string.action_navigate)
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}

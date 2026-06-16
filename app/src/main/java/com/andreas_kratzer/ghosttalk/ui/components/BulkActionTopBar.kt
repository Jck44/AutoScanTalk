package com.andreas_kratzer.ghosttalk.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkActionTopBar(
    selectedCount: Int,
    onCancel: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.bulk_action_selected_count, selectedCount),
                style = MaterialTheme.typography.titleMedium
            )
        },
        navigationIcon = {
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.bulk_action_cancel)
                )
            }
        },
        actions = {
            if (selectedCount > 0) {
                IconButton(onClick = onMove) {
                    Icon(
                        imageVector = GhostTalkIcons.ArrowForward,
                        contentDescription = stringResource(R.string.bulk_action_move)
                    )
                }
                IconButton(onClick = onCopy) {
                    Icon(
                        imageVector = GhostTalkIcons.Copy,
                        contentDescription = stringResource(R.string.bulk_action_copy)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.bulk_action_delete)
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

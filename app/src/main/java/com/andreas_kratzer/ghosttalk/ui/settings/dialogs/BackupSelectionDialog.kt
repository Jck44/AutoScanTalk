package com.andreas_kratzer.ghosttalk.ui.settings.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.R
import com.andreas_kratzer.ghosttalk.core.cloud.domain.RemoteBackupInfo
import com.andreas_kratzer.ghosttalk.ui.theme.LocalDimensions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupSelectionDialog(
    backups: List<RemoteBackupInfo>,
    onBackupSelected: (RemoteBackupInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val dimensions = LocalDimensions.current
    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.settings_cloud_backup_selection_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.settings_cloud_backup_selection_description),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = dimensions.paddingMedium)
                )
                
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(dimensions.paddingSmall)
                    ) {
                        items(backups) { backup ->
                            BackupItem(
                                backup = backup,
                                dateFormat = dateFormat,
                                onClick = { onBackupSelected(backup) }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = dimensions.paddingSmall),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun BackupItem(
    backup: RemoteBackupInfo,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit
) {
    val dimensions = LocalDimensions.current
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(dimensions.paddingMedium)
    ) {
        Text(
            text = backup.bookName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(
                R.string.settings_cloud_backup_date_format,
                dateFormat.format(Date(backup.lastModified))
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

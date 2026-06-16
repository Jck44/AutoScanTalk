package com.andreas_kratzer.ghosttalk.feature.settings.ui.dialogs

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.andreas_kratzer.ghosttalk.core.ui.theme.GhostTalkIcons
import com.google.api.services.drive.model.File

@Composable
fun DriveFolderPickerDialog(
    folders: List<File>,
    isLoading: Boolean,
    onFetchFolders: (String) -> Unit,
    onFolderSelected: (String?, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var currentPathStack by remember { mutableStateOf(listOf("root" to "Google Drive")) }
    val currentFolder = currentPathStack.last()

    LaunchedEffect(currentFolder.first) {
        onFetchFolders(currentFolder.first)
    }

    com.andreas_kratzer.ghosttalk.core.ui.components.GhostTalkDialog(
        onDismiss = onDismiss,
        confirmText = "Diesen Ordner wählen",
        onConfirm = {
            if (currentFolder.first == "root") {
                onFolderSelected(null, null)
            } else {
                onFolderSelected(currentFolder.first, currentFolder.second)
            }
        },
        dismissText = "Abbrechen",
        confirmEnabled = !isLoading,
        titleContent = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (currentPathStack.size > 1) {
                        IconButton(onClick = {
                            currentPathStack = currentPathStack.dropLast(1)
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    } else {
                        Icon(GhostTalkIcons.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = currentFolder.second,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                Text(
                    text = "Wähle einen Zielordner für Backups",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    ) {
        Column(modifier = Modifier.height(300.dp)) {
            HorizontalDivider()
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (folders.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("Keine Unterordner gefunden.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(folders) { folder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentPathStack = currentPathStack + (folder.id to folder.name)
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isAppCreated = folder.name == "GhosTTalk_Sync" || folder.appProperties?.containsKey("ghosttalk_sync") == true
                            Icon(
                                imageVector = GhostTalkIcons.Folder,
                                contentDescription = null,
                                tint = if (isAppCreated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = folder.name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            CustomIcon(GhostTalkIcons.ArrowForward, contentDescription = null, size = 16.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomIcon(imageVector: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String?, size: androidx.compose.ui.unit.Dp, tint: androidx.compose.ui.graphics.Color) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = Modifier.size(size),
        tint = tint
    )
}
